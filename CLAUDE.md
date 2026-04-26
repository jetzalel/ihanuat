# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Ihanuat is a Fabric mod for Minecraft 1.21 (Java 21) that automates the Hypixel Skyblock Garden farming loop. It cooperates with an external farming script ("Taunahi") — Ihanuat does not move the player around plots itself; instead it orchestrates the higher-level loop (pest cleaning, visitors, wardrobe/equipment swaps, rests, recovery, profit tracking) and starts/stops the underlying script via chat commands.

## Build

```bash
./gradlew build              # produces build/libs/*.jar (consumed by CI for releases)
./gradlew runClient          # launches a Fabric dev client with the mod loaded
./gradlew genSources          # generate Minecraft sources for IDE navigation
```

Releases are produced by `.github/workflows/build.yml` on every push to `main`/`master`. CI reads `mod_version` from `gradle.properties` and appends `-r<N>` (next free revision) to derive a release tag, so bumping `mod_version` is the only thing needed to start a new release line. There are no tests in this project.

## Big-picture architecture

### Entrypoints
- `Ihanuat` (`fabric.mod.json` `main`) — server-side init, just logs.
- `IhanuatClient` (`client`) — does almost everything. Registers keybinds (`O` opens config GUI, `K` toggles macro), client tick handlers, chat receive/send listeners, screen events, and HUD drag/resize. Most cross-module wiring lives here.

### State machine
`MacroState.State` ∈ `{OFF, FARMING, CLEANING, RECOVERING, VISITING, AUTOSELLING, SPRAYING}` is the single source of truth for what the macro is doing. `MacroStateManager` owns transitions and is the only thing that should call `setCurrentState`. It also:
- Tracks session/lifetime/today running time, persisting `lifetimeAccumulated` to `MacroConfig` every minute and on transitions to OFF.
- Distinguishes intentional vs. unexpected disconnects (`intentionalDisconnect` flag) — unexpected disconnects while not OFF/RECOVERING flip the state to `RECOVERING`, which `RecoveryManager` then drives.
- `stopMacro(client)` is the canonical "kill switch": it cancels worker tasks, releases held keys, sends `/stop` to the underlying script, and resets per-module state.

State checks are pervasive. New code that runs during a specific phase should gate on `MacroStateManager.getCurrentState()` (or use `MacroWorkerThread.shouldAbortTask(client, requiredState)` inside worker tasks).

### Threading: `MacroWorkerThread`
There is a single shared worker thread that serialises blocking macro tasks. **Do not spawn `new Thread(...)` from modules** — submit via `MacroWorkerThread.getInstance().submit(name, runnable)`. Inside a task:
- It is OK to `Thread.sleep` (use `MacroWorkerThread.sleep(ms)`).
- Anything touching Minecraft client state (slots, screens, player, key state) must be dispatched via `client.execute(() -> ...)`.
- Insert `if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.X)) return;` checkpoints around any sleep or client round-trip — `cancelCurrent()` is called on stop and on state changes, and tasks that don't poll will keep running into a stale state.

### Modules (`com.ihanuat.mod.modules`)
Each module owns one concern and exposes static methods because there is exactly one client. Common shape: a `reset()` (called on macro start/stop), an `update(Minecraft)` ticked from `IhanuatClient.END_CLIENT_TICK`, plus chat/GUI hooks invoked from `IhanuatClient`. Highlights:
- `PestManager` + `PestPrepSwapManager` / `PestReturnManager` / `PestAotvManager` / `PestBonusManager` / `PestCleaningSequencer` / `PestTabListParser` — pest detection (tab list and chat) and the cleaning sequence. `PestManager.isCleaningInProgress` is widely checked as a "do not interrupt" guard.
- `VisitorManager` — drives the visitor wardrobe + accept-offer flow, triggered by chat ("offer accepted") and GUI scans done from `ScreenEvents.AFTER_INIT`.
- `GearManager` / `WardrobeManager` / `RodManager` / `EquipmentManager` — inventory/wardrobe/equipment swaps; `GearManager.swapToFarmingToolSync` blocks on the worker thread and is the canonical "make sure we're holding the farm tool" call.
- `DynamicRestManager` + `RestStateManager` + `ReconnectScheduler` — schedules randomised farm/rest cycles. The reconnect target is persisted to `RestStateManager` so it survives Minecraft restarts; on rejoin `IhanuatClient` reads it and either resumes or clears it.
- `RecoveryManager` — only runs while state is `RECOVERING`. Polls `ClientUtils.getCurrentLocation` and walks the player back to Garden via `/warp garden` etc.
- `RestartManager` / `QuitThresholdManager` — react to Hypixel server-restart messages and to user-configured session caps.
- `ChatRuleManager`, `DiscordStatusManager`, `BookCombineManager`, `BoosterCookieManager`, `JunkManager`, `GeorgeManager`, `SprayonatorManager`, `CropFeverManager`, `SuperCrafter`, `RotationManager`, `TodayTimeTracker` — self-contained features wired from `IhanuatClient`.
- `modules/profitTracker/ProfitManager` — coordinates session/daily/lifetime profit counts; helpers `BazaarService`, `ChatParser`, `InventoryTracker`, `PetXpTracker`, `SackTracker`, `ItemConstants`. Persists to `ihanuat_profit_lifetime.json` and `ihanuat_profit_daily.json` (separate from the main config file).

### Config (`MacroConfig`)
A static-fields-as-config holder serialised with GSON to `<fabric-config>/ihanuat_config.json`. On first launch, current defaults are also written to `ihanuat_defaults.json` and never overwritten — `getDefaultString/Int/Double/List` reads from that file so the UI can offer "reset to default" against the build-time defaults the user first saw. `save()` is guarded by a `ReentrantLock` because both the game thread and the worker thread can save. When adding a new persisted setting, add the field, the default constant, the `DEFAULT_…` sibling on the `ConfigData` snapshot inside `save()`, and the read in `load()`.

### GUI / HUD
- `gui/ClickGui` — the in-game config screen (opened with `O`). Large file, panel-based with theme variables that read from `MacroConfig.theme*`.
- `gui/MacroHudRenderer` + `gui/ProfitHudRenderer` — the always-on HUDs. They support edit mode (drag/resize while a container screen is open), wired in `IhanuatClient` via `ScreenMouseEvents`.
- `gui/DynamicRestScreen` — the screen shown while a Dynamic Rest break is active.

### Mixins (`src/main/resources/ihanuat.mixins.json`)
- `MixinGameRenderer` — pumps `RotationManager.update` from `GameRenderer.render` for smooth aim interpolation.
- `MixinMouseHandler` — suppresses real mouse rotation while `RotationManager.isRotating()`.
- `MixinClientPacketListener`, `ChatHudMixin` — chat capture / filtering hooks.
- `AccessorInventory`, `PlayerTabOverlayAccessor` — accessor mixins for inventory and tab list internals consumed by `PestTabListParser` and inventory scans.

### Chat-driven control flow
A large fraction of behaviour is keyed off chat messages from Hypixel and Taunahi. The handlers in `IhanuatClient.onInitializeClient` recognise things like `"Evacuating to Hub..."`, `"autosell ... script activated"`, `"Let's use sprayonator."`, `"Yuck! ... Plot N"`, etc. Two listeners are registered (`ClientReceiveMessageEvents.GAME` and `.CHAT`) for chat rules; `ChatRuleManager` dedupes via a `ConcurrentHashMap` so webhooks fire once even if a message hits both.

### i18n
`I18n.tr(english, simplifiedChinese)` is a tiny two-locale switch driven by `MacroConfig.language`. New user-facing strings should go through it.
