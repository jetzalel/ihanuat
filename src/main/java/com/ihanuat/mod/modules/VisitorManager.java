package com.ihanuat.mod.modules;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class VisitorManager {
    private static final Pattern VISITORS_PATTERN = Pattern.compile("Visitors:\\s*\\(?(\\d+)\\)?");
    private static final long VISITOR_REENTRY_COOLDOWN_MS = 60_000L;

    private static VisitorOffer pendingOffer = null;
    private static volatile long visitorReentryCooldownUntilMs = 0L;

    // ── Inner Data Classes ──

    public static class VisitorOffer {
        public String visitorName;
        public long totalCost = 0;
    }

    // ── Existing Methods (unchanged) ──

    public static int getVisitorCount(Minecraft client) {
        if (!MacroConfig.autoVisitor || client.level == null)
            return 0;

        if (!client.isSameThread()) {
            java.util.concurrent.CompletableFuture<Integer> future = new java.util.concurrent.CompletableFuture<>();
            client.execute(() -> {
                future.complete(getVisitorCount(client));
            });
            try {
                return future.get(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                return 0;
            }
        }

        try {
            if (client.getConnection() != null) {
                java.util.Collection<net.minecraft.client.multiplayer.PlayerInfo> players = client.getConnection()
                        .getListedOnlinePlayers();
                for (net.minecraft.client.multiplayer.PlayerInfo info : players) {
                    String name = "";
                    if (info.getTabListDisplayName() != null) {
                        name = info.getTabListDisplayName().getString();
                    } else if (info.getProfile() != null) {
                        name = String.valueOf(info.getProfile());
                    }
                    String clean = name.replaceAll("(?i)\u00A7[0-9a-fk-or]", "").trim();
                    Matcher m = VISITORS_PATTERN.matcher(clean);
                    if (m.find()) {
                        return Integer.parseInt(m.group(1));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static void handleVisitorScriptFinished(Minecraft client) {
        startVisitorReentryCooldown(client);
        client.player.displayClientMessage(Component.literal("\u00A7aVisitor sequence complete. Returning to farm..."),
                true);
        MacroWorkerThread.getInstance().submit("VisitorFinished-ReturnToFarm", () -> {
            try {
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                ClientUtils.sendDebugMessage(client, "Warping to garden...");
                com.ihanuat.mod.util.CommandUtils.warpGarden(client);
                PestReturnManager.isReturningFromPestVisitor = true;
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                ClientUtils.sendDebugMessage(client, "Finalizing return to farm...");
                finalizeReturnToFarm(client);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void finalizeReturnToFarm(Minecraft client) {
        if (MacroWorkerThread.shouldAbortTask(client))
            return;
        ClientUtils.sendDebugMessage(client,
                "finalizeReturnToFarm triggered. State: " + MacroStateManager.getCurrentState());
        if (MacroStateManager.getCurrentState() == MacroState.State.OFF)
            return;

        int visitors = getVisitorCount(client);
        if (visitors >= MacroConfig.visitorThreshold && !isVisitorReentryCooldownActive(client, true)) {
            client.player.displayClientMessage(
                    Component.literal("\u00A7dVisitor Threshold Met (" + visitors + "). Redirecting to Visitors..."),
                    true);
            client.execute(() -> {
                GearManager.swapToFarmingTool(client);
            });
            MacroWorkerThread.sleep(250);

            if (MacroConfig.autoWardrobeVisitor && MacroConfig.wardrobeSlotVisitor > 0
                    && WardrobeManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
                client.player.displayClientMessage(Component.literal(
                        "\u00A7eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor + ")..."), true);
                GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor);
                if (WardrobeManager.isSwappingWardrobe) {
                    ClientUtils.waitForWardrobeGui(client);
                    while (WardrobeManager.isSwappingWardrobe)
                        MacroWorkerThread.sleep(50);
                    while (WardrobeManager.wardrobeCleanupTicks > 0)
                        MacroWorkerThread.sleep(50);
                    MacroWorkerThread.sleep(250);
                }
            }
            ClientUtils.waitForGearAndGui(client);
            ClientUtils.sendDebugMessage(client, "Wardrobe swap done, now triggering visitor macro");
            com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.VISITING);
            ClientUtils.sendDebugMessage(client, "Stopping script: Returning to visitor macro (threshold met)");
            com.ihanuat.mod.util.CommandUtils.stopScript(client, 250);
            ClientUtils.sendDebugMessage(client, "Starting visitor macro script");
            com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:visitor", 0);
            PestManager.isCleaningInProgress = false;
            return;
        }

        if (visitors >= MacroConfig.visitorThreshold) {
            ClientUtils.sendDebugMessage(client,
                    "Visitor threshold met, but re-entry cooldown is active. Continuing farming.");
        }

        client.execute(() -> {
            GearManager.swapToFarmingTool(client);
        });
        MacroWorkerThread.sleep(250);

        if (MacroConfig.autoWardrobeVisitor && MacroConfig.wardrobeSlotFarming > 0
                && WardrobeManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotFarming) {
            client.player.displayClientMessage(Component.literal(
                    "\u00A7eRestoring Farming Wardrobe (Slot " + MacroConfig.wardrobeSlotFarming + ")..."), true);
            GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotFarming);
            if (WardrobeManager.isSwappingWardrobe) {
                ClientUtils.sendDebugMessage(client, "finalizeReturnToFarm: Waiting for wardrobe GUI...");
                ClientUtils.waitForWardrobeGui(client);
                ClientUtils.sendDebugMessage(client,
                        "finalizeReturnToFarm: Wardrobe GUI detected, waiting for swap to complete...");
                while (WardrobeManager.isSwappingWardrobe)
                    MacroWorkerThread.sleep(50);
                while (WardrobeManager.wardrobeCleanupTicks > 0)
                    MacroWorkerThread.sleep(50);
                MacroWorkerThread.sleep(350);
                ClientUtils.sendDebugMessage(client, "finalizeReturnToFarm: Wardrobe swap fully complete.");
            }
        }

        ClientUtils.waitForGearAndGui(client);

        if (MacroConfig.autoRodReturnToFarm) {
            ClientUtils.sendDebugMessage(client, "Auto Rod: Triggering second rod cast (VisitorManager)...");
            RodManager.executeRodSequence(client);
        }

        client.execute(() -> {
            GearManager.swapToFarmingTool(client);
            ClientUtils.sendDebugMessage(client, "Setting state to FARMING and starting script.");
            com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
            ClientUtils.sendDebugMessage(client, "Stopping script: Visitor sequence finished, returning to farming");
            com.ihanuat.mod.util.CommandUtils.stopScript(client, 250);
            ClientUtils.sendDebugMessage(client, "Starting farming script: " + MacroConfig.getFullRestartCommand());
            com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
        });
        PestPrepSwapManager.prepSwappedForCurrentPestCycle = false;
        PestManager.isCleaningInProgress = false;
    }

    // ── Visitor ROI: GUI Scanning ──

    @SuppressWarnings("rawtypes")
    public static void scanVisitorGui(Minecraft client,
            net.minecraft.client.gui.screens.inventory.AbstractContainerScreen screen) {
        if (!MacroStateManager.isMacroRunning() || client.player == null)
            return;

        Component titleComp = screen.getTitle();
        String title = titleComp.getString().trim();

        // The accept button is usually in slot 29 of a 54-slot chest
        int slotIndex = 29;
        if (screen.getMenu().slots.size() <= slotIndex)
            return;

        net.minecraft.world.inventory.Slot slot = screen.getMenu().getSlot(slotIndex);
        if (slot == null || !slot.hasItem())
            return;

        ItemStack stack = slot.getItem();
        String name = stack.getHoverName().getString();

        if (!name.contains("Accept Offer"))
            return;

        VisitorOffer offer = new VisitorOffer();
        offer.visitorName = title;
        StringBuilder costBreakdown = new StringBuilder("\u00A7d[Ihanuat] \u00A77Costs: ");

        net.minecraft.world.item.component.ItemLore loreCmp = stack.get(DataComponents.LORE);
        if (loreCmp == null) {
            client.player.displayClientMessage(
                    Component.literal("\u00A7d[Ihanuat] \u00A7cNo lore found on Accept Offer button!"), false);
            return;
        }
        List<Component> lore = loreCmp.lines();
        boolean parsingRequirements = false;

        for (Component line : lore) {
            String text = line.getString().replaceAll("\u00A7[0-9a-fk-or]", "").trim();
            if (text.isEmpty())
                continue;

            if (text.contains("Items Required:")) {
                parsingRequirements = true;
                continue;
            }
            if (text.contains("Rewards:")) {
                parsingRequirements = false;
                continue;
            }

            if (parsingRequirements && !text.contains("Farming XP") && !text.contains("Garden Experience")) {
                parseRequirement(client, text, offer, stack, costBreakdown);
            }
        }

        if (offer.totalCost > 0) {
            pendingOffer = offer;
        }
    }

    // ── Helpers ──

    private static String formatPrice(long price) {
        if (price >= 1_000_000)
            return String.format("%.1fM", price / 1_000_000.0);
        if (price >= 1_000)
            return String.format("%.1fk", price / 1_000.0);
        return String.valueOf(price);
    }

    private static void parseRequirement(Minecraft client, String text, VisitorOffer offer, ItemStack stack,
            StringBuilder breakdown) {
        // Handle "Enchanted Hay Bale x256" format
        Matcher m = Pattern.compile("(.+?)\\s+x(\\d+)$").matcher(text);
        String itemName;
        long count = 1;

        if (m.find()) {
            itemName = m.group(1).trim();
            count = Long.parseLong(m.group(2));
        } else {
            itemName = text.trim();
        }

        String id = resolveId(itemName, stack);
        double price = ProfitManager.getItemPrice(id != null ? id : itemName);

        if (price > 0) {
            long total = (long) (price * count);
            offer.totalCost += total;
            breakdown.append("\u00A7e").append(count).append("x ").append(itemName)
                    .append(" \u00A77(\u00A7c").append(formatPrice(total)).append("\u00A77), ");
        } else {
            breakdown.append("\u00A7c?x ").append(itemName).append(" (price unknown), ");
            System.out.println("[Ihanuat] Unknown Visitor Cost Item: " + itemName + " (ID: " + id + ")");
        }
    }

    /**
     * Resolves a Skyblock Item ID from NBT custom data, or falls back to the
     * Cofl API search cache in ProfitManager.fetchIdByName.
     */
    @SuppressWarnings("unchecked")
    private static String resolveId(String name, ItemStack scannerStack) {
        // 1. Try NBT lookup from the "Accept Offer" stack's custom data
        try {
            CustomData customData = scannerStack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("ExtraAttributes")) {
                    java.util.Optional<CompoundTag> eaOpt = tag.getCompound("ExtraAttributes");
                    if (eaOpt.isPresent()) {
                        CompoundTag ea = eaOpt.get();
                        if (ea.contains("id")) {
                            String nbtId = ea.getString("id").orElse("");
                            if (!nbtId.isEmpty()) {
                                return nbtId;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Ihanuat] NBT lookup failed for '" + name + "': " + e.getMessage());
        }

        // 2. Fallback: Cofl API name-based lookup with cache
        return ProfitManager.fetchIdByName(name);
    }

    public static void onOfferAccepted(String visitorName) {
        if (pendingOffer != null) {
            String cleanPending = pendingOffer.visitorName.replaceAll("(?i)\u00A7[0-9a-fk-or]", "").trim();
            String cleanAccepted = visitorName.replaceAll("(?i)\u00A7[0-9a-fk-or]", "").trim();

            // Lenient matching: "Moby" should match "Moby (RARE)"
            if (cleanAccepted.startsWith(cleanPending) || cleanPending.startsWith(cleanAccepted)) {
                ProfitManager.addVisitorCost(pendingOffer.totalCost);
                pendingOffer = null;
            }
        }
    }

    public static void clearPendingOffer() {
        pendingOffer = null;
    }

    public static void startVisitorReentryCooldown(Minecraft client) {
        visitorReentryCooldownUntilMs = System.currentTimeMillis() + VISITOR_REENTRY_COOLDOWN_MS;
        ClientUtils.sendDebugMessage(client, "Visitor re-entry cooldown started (1 minute).");
    }

    public static boolean isVisitorReentryCooldownActive(Minecraft client, boolean showMessage) {
        long now = System.currentTimeMillis();
        long remainingMs = visitorReentryCooldownUntilMs - now;
        if (remainingMs <= 0) {
            return false;
        }

        long remainingSeconds = (remainingMs + 999L) / 1000L;
        ClientUtils.sendDebugMessage(client,
                "Visitor re-entry cooldown active (" + remainingSeconds + "s remaining). Skipping visitor macro.");
        if (showMessage && client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("\u00A7eVisitor cooldown active (" + remainingSeconds + "s). Staying on farm."),
                    true);
        }
        return true;
    }
}
