package com.ihanuat.mod.util;

import net.minecraft.client.Minecraft;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class CommandUtils {
    private static final Queue<String> chatMessageQueue = new LinkedList<>();
    private static final long MESSAGE_TIMEOUT_MS = 10000; // 10 second timeout
    private static final Set<String> awaitingMessages = new HashSet<>();

    /**
     * Register a chat message to the queue.
     * This is called from IhanuatClient when a chat message is received.
     */
    public static void onChatMessage(String message) {
        synchronized (chatMessageQueue) {
            chatMessageQueue.add(message);
            chatMessageQueue.notifyAll();
        }
    }

    /**
     * Wait for a chat message containing the specified substring.
     * Blocks until the message is found or timeout is reached.
     *
     * @param client           The Minecraft instance
     * @param messageSubstring The substring to search for in chat messages
     * @return true if the message was found, false if timeout occurred
     */
    public static boolean waitForChatMessage(Minecraft client, String messageSubstring) {
        long startTime = System.currentTimeMillis();

        synchronized (chatMessageQueue) {
            while (System.currentTimeMillis() - startTime < MESSAGE_TIMEOUT_MS) {
                // Check existing messages
                java.util.Iterator<String> it = chatMessageQueue.iterator();
                while (it.hasNext()) {
                    String msg = it.next();
                    if (msg.contains(messageSubstring)) {
                        it.remove(); // Remove the matched message
                        return true;
                    }
                }

                try {
                    // Wait for new messages
                    long remainingTime = MESSAGE_TIMEOUT_MS - (System.currentTimeMillis() - startTime);
                    if (remainingTime > 0) {
                        chatMessageQueue.wait(Math.min(remainingTime, 100));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false;
    }

    /**
     * Non-blocking method to check if a message has been received.
     * Returns immediately with true if the message was found, false otherwise.
     *
     * @param messageSubstring The substring to search for
     * @return true if the message has been received, false otherwise
     */
    public static boolean hasReceivedMessage(String messageSubstring) {
        synchronized (chatMessageQueue) {
            java.util.Iterator<String> it = chatMessageQueue.iterator();
            while (it.hasNext()) {
                String msg = it.next();
                if (msg.contains(messageSubstring)) {
                    it.remove(); // Remove the matched message
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if a message receipt is being awaited (non-blocking check for async
     * operations).
     *
     * @param messageSubstring The substring to check
     * @return true if the message has been received, false otherwise
     */
    public static boolean isMessageReceived(String messageSubstring) {
        return hasReceivedMessage(messageSubstring);
    }

    /**
     * Execute /setspawn and wait for the confirmation message.
     * Blocks until the spawn location is confirmed or timeout occurs.
     *
     * @param client The Minecraft instance
     * @return true if spawn was set successfully, false if timeout occurred
     */
    public static boolean setSpawn(Minecraft client) {
        ClientUtils.sendCommand(client, "/setspawn");

        if (com.ihanuat.mod.MacroConfig.delayMode == com.ihanuat.mod.MacroConfig.DelayMode.LEGACY) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }

        boolean success = waitForChatMessage(client, "Your spawn location has been set!");

        if (success) {
            ClientUtils.sendDebugMessage(client, "Spawn set has been detected");
        }

        return success;
    }

    /**
     * Initiate /setspawn command (non-blocking).
     * Check result with hasSpawnBeenSet().
     *
     * @param client The Minecraft instance
     */
    public static void initiateSetSpawn(Minecraft client) {
        ClientUtils.sendCommand(client, "/setspawn");
    }

    /**
     * Check if /setspawn has been confirmed (non-blocking).
     *
     * @return true if the spawn confirmation message was received
     */
    public static boolean hasSpawnBeenSet() {
        if (com.ihanuat.mod.MacroConfig.delayMode == com.ihanuat.mod.MacroConfig.DelayMode.LEGACY) {
            return false; // Return false to force the caller to wait for their own fallback timeout
        }
        return hasReceivedMessage("Your spawn location has been set!");
    }

    /**
     * Execute /warp garden and wait for the confirmation message or position
     * change.
     * Blocks until the warp is confirmed, a significant position change is
     * detected, or timeout occurs.
     *
     * @param client The Minecraft instance
     * @return true if warp was successful, false if timeout occurred
     */
    public static boolean warpGarden(Minecraft client) {
        if (client.player == null)
            return false;

        net.minecraft.world.phys.Vec3 startPos = client.player.position();
        ClientUtils.sendCommand(client, "/warp garden");

        if (com.ihanuat.mod.MacroConfig.delayMode == com.ihanuat.mod.MacroConfig.DelayMode.LEGACY) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < MESSAGE_TIMEOUT_MS) {
            // Priority 1: Chat confirmation
            if (hasReceivedMessage("Warping...")) {
                ClientUtils.sendDebugMessage(client, "/warp garden success (chat)");
                return true;
            }

            // Priority 2: Position fallback (moved > 10 blocks and in Garden)
            if (client.player != null) {
                double dist = client.player.position().distanceTo(startPos);
                if (dist > 10) {
                    com.ihanuat.mod.MacroState.Location loc = ClientUtils.getCurrentLocation(client);
                    if (loc == com.ihanuat.mod.MacroState.Location.GARDEN) {
                        ClientUtils.sendDebugMessage(client,
                                "/warp garden success (pos fallback, dist: " + String.format("%.1f", dist) + ")");
                        return true;
                    }
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    /**
     * Initiate /warp garden command (non-blocking).
     * Check result with hasWarpedGarden().
     *
     * @param client The Minecraft instance
     */
    public static void initiateWarpGarden(Minecraft client) {
        ClientUtils.sendCommand(client, "/warp garden");
    }

    /**
     * Check if /warp garden has been confirmed (non-blocking).
     *
     * @return true if the warp confirmation message was received
     */
    public static boolean hasWarpedGarden() {
        if (com.ihanuat.mod.MacroConfig.delayMode == com.ihanuat.mod.MacroConfig.DelayMode.LEGACY) {
            return false;
        }
        return hasReceivedMessage("Warping...");
    }

    /**
     * Execute /plottp and wait for the confirmation message or position change.
     * Blocks until the warp is confirmed, a significant position change is
     * detected, or timeout occurs.
     *
     * @param client     The Minecraft instance
     * @param plotNumber The plot number to warp to
     * @return true if warp was successful, false if timeout occurred
     */
    public static boolean plotTp(Minecraft client, String plotNumber) {
        if (client.player == null)
            return false;

        net.minecraft.world.phys.Vec3 startPos = client.player.position();
        ClientUtils.sendCommand(client, "/plottp " + plotNumber);

        if (com.ihanuat.mod.MacroConfig.delayMode == com.ihanuat.mod.MacroConfig.DelayMode.LEGACY) {
            try {
                Thread.sleep(1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < MESSAGE_TIMEOUT_MS) {
            // Priority 1: Chat confirmation
            if (hasReceivedMessage("Teleported you to Plot")) {
                ClientUtils.sendDebugMessage(client, "plottp success (chat)");
                return true;
            }

            // Priority 2: Position fallback (moved > 10 blocks)
            if (client.player != null) {
                double dist = client.player.position().distanceTo(startPos);
                if (dist > 10) {
                    ClientUtils.sendDebugMessage(client,
                            "plottp success (pos fallback, dist: " + String.format("%.1f", dist) + ")");
                    return true;
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    /**
     * Initiate /plottp command (non-blocking).
     * Check result with hasPlotTp().
     *
     * @param client     The Minecraft instance
     * @param plotNumber The plot number to warp to
     */
    public static void initiatePlotTp(Minecraft client, String plotNumber) {
        ClientUtils.sendCommand(client, "/plottp " + plotNumber);
    }

    /**
     * Check if /plottp has been confirmed (non-blocking).
     *
     * @return true if the warp confirmation message was received
     */
    public static boolean hasPlotTp() {
        if (com.ihanuat.mod.MacroConfig.delayMode == com.ihanuat.mod.MacroConfig.DelayMode.LEGACY) {
            return false;
        }
        return hasReceivedMessage("Teleported you to Plot");
    }

    /**
     * Stop the current ez script and wait for a specified delay.
     * 
     * @param client  The Minecraft instance
     * @param delayMs Delay in milliseconds
     */
    public static void stopScript(Minecraft client, long delayMs) {
        ClientUtils.sendCommand(client, ".ez-stopscript");
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Stop the current ez script and wait for a default 250ms delay.
     * 
     * @param client The Minecraft instance
     */
    public static void stopScript(Minecraft client) {
        stopScript(client, 250);
    }

    /**
     * Start an ez script and wait for a specified delay.
     * 
     * @param client        The Minecraft instance
     * @param scriptCommand The full command or script string
     * @param delayMs       Delay in milliseconds
     */
    public static void startScript(Minecraft client, String scriptCommand, long delayMs) {
        ClientUtils.sendCommand(client, scriptCommand);
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Start an ez script and wait for a default 250ms delay.
     * 
     * @param client        The Minecraft instance
     * @param scriptCommand The full command or script string
     */
    public static void startScript(Minecraft client, String scriptCommand) {
        startScript(client, scriptCommand, 250);
    }

    /**
     * Clear any pending chat messages from the queue.
     */
    public static void clearMessageQueue() {
        synchronized (chatMessageQueue) {
            chatMessageQueue.clear();
        }
    }
}
