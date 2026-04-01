package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;

public class PestBonusManager {
    public static volatile boolean isBonusInactive = false;
    public static volatile boolean isReactivatingBonus = false;
    public static volatile long interactionTime = 0;
    public static volatile long lastPhillipCallTime = 0;

    public static volatile boolean clickConfirmed = false;
    private static volatile int clickAttempts = 0;

    private static final int MAX_CLICK_ATTEMPTS = 3;
    private static final long GUI_OPEN_TIMEOUT_MS = 5000;
    private static final long CLICK_CONFIRM_TIMEOUT_MS = 3000;

    public static void resetState() {
        isBonusInactive = false;
        isReactivatingBonus = false;
        interactionTime = 0;
        clickConfirmed = false;
        clickAttempts = 0;
    }

    private static void clickSlot(Minecraft client, AbstractContainerScreen<?> screen) {
        if (client.player == null) return;

        // Guard: GUI must have enough slots
        if (screen.getMenu().slots.size() < 12) {
            ClientUtils.sendDebugMessage(client,
                    "Phillip: GUI has only " + screen.getMenu().slots.size()
                            + " slots — not fully loaded yet.");
            return;
        }

        // Guard: slot 11 must not be empty
        net.minecraft.world.item.ItemStack slotItem = screen.getMenu().slots.get(11).getItem();
        if (slotItem.isEmpty()) {
            ClientUtils.sendDebugMessage(client, "Phillip: Slot 11 is empty — GUI may not be ready.");
            return;
        }

        if (clickAttempts >= MAX_CLICK_ATTEMPTS) {
            ClientUtils.sendDebugMessage(client,
                    "Phillip: Max click attempts (" + MAX_CLICK_ATTEMPTS + ") reached. Giving up.");
            isReactivatingBonus = false;
            return;
        }

        clickAttempts++;
        ClientUtils.sendDebugMessage(client,
                "Phillip: Clicking slot 11 (attempt " + clickAttempts + "/" + MAX_CLICK_ATTEMPTS + ").");

        // Must run on the game thread
        final AbstractContainerScreen<?> screenRef = screen;
        client.execute(() -> client.gameMode.handleInventoryMouseClick(
                screenRef.getMenu().containerId,
                11,
                0,
                ClickType.PICKUP,
                client.player
        ));
    }

    public static boolean waitForGuiOpen(Minecraft client) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (true) {
            if (MacroWorkerThread.shouldAbortTask(client)) return false;

            if (client.screen instanceof AbstractContainerScreen<?> screen) {
                String title = screen.getTitle().getString().toLowerCase();
                if (title.contains("pesthunter")) {
                    ClientUtils.sendDebugMessage(client, "Phillip: GUI opened successfully.");
                    return true;
                }
            }

            if (System.currentTimeMillis() - start > GUI_OPEN_TIMEOUT_MS) {
                ClientUtils.sendDebugMessage(client,
                        "Phillip: Timed out waiting for GUI to open after "
                                + GUI_OPEN_TIMEOUT_MS + "ms.");
                return false;
            }

            MacroWorkerThread.sleep(100);
        }
    }

    public static void waitForClickConfirmation(Minecraft client) throws InterruptedException {
        long start = System.currentTimeMillis();

        while (!clickConfirmed) {
            if (MacroWorkerThread.shouldAbortTask(client)) return;

            if (System.currentTimeMillis() - start > CLICK_CONFIRM_TIMEOUT_MS) {
                // Retry if we still have attempts left and the GUI is still open
                if (clickAttempts < MAX_CLICK_ATTEMPTS
                        && client.screen instanceof AbstractContainerScreen<?> screen) {
                    ClientUtils.sendDebugMessage(client,
                            "Phillip: No confirmation received — retrying click.");
                    clickSlot(client, screen);
                    start = System.currentTimeMillis();
                } else {
                    ClientUtils.sendDebugMessage(client,
                            "Phillip: Click confirmation timed out. Continuing anyway.");
                    isReactivatingBonus = false;
                    return;
                }
            }

            MacroWorkerThread.sleep(100);
        }
        clickConfirmed = true;
        isReactivatingBonus = false;
        ClientUtils.sendDebugMessage(client, "Phillip: Bonus successfully reactivated.");
    }

    public static void waitForReactivation(Minecraft client) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (isReactivatingBonus) {
            if (MacroWorkerThread.shouldAbortTask(client)) return;
            if (System.currentTimeMillis() - start > 10000) {
                ClientUtils.sendDebugMessage(client, "Phillip reactivation timed out — continuing anyway.");
                isReactivatingBonus = false;
                return;
            }
            MacroWorkerThread.sleep(100);
        }
    }

    public static void runBonusReactivationSequence(Minecraft client) throws InterruptedException {
        if (client.player == null) return;

        ClientUtils.sendDebugMessage(client, "Bonus INACTIVE detected — calling Phillip.");
        client.player.displayClientMessage(
                Component.literal("§dBonus is INACTIVE! Calling Phillip..."), true);

        isReactivatingBonus = true;
        clickConfirmed = false;
        clickAttempts = 0;
        interactionTime = System.currentTimeMillis();
        lastPhillipCallTime = interactionTime;

        boolean guiOpened = false;

        for (int attempt = 1; attempt <= 3; attempt++) {
            int attemptFinal = attempt;

            client.execute(() -> {
                ClientUtils.sendCommand(client, "/call phillip");
                ClientUtils.sendDebugMessage(client, "Calling Phillip (attempt " + attemptFinal + "/3)");
            });

            guiOpened = waitForGuiOpen(client);

            if (guiOpened) {
                break;
            }

            if (attempt < 3) {
                ClientUtils.sendDebugMessage(client, "Phillip: GUI not opened, retrying in 5s...");
                MacroWorkerThread.sleep(5000);
            }
        }
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            ClientUtils.sendDebugMessage(client, "Phillip: Screen closed before click. Aborting.");
            isReactivatingBonus = false;
            return;
        }

        clickSlot(client, screen);

        waitForClickConfirmation(client);
    }
}