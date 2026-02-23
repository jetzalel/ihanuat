package com.ihanuat.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ReconnectScheduler {
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> pendingReconnect;

    public static void scheduleReconnect(long delaySeconds, boolean shouldResume) {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ihanuat-reconnect");
                t.setDaemon(true);
                return t;
            });
        }

        if (pendingReconnect != null) {
            pendingReconnect.cancel(false);
        }

        long reconnectAt = Instant.now().getEpochSecond() + delaySeconds;
        RestStateManager.saveReconnectTime(reconnectAt, shouldResume);

        pendingReconnect = scheduler.schedule(
                ReconnectScheduler::doReconnect,
                delaySeconds,
                TimeUnit.SECONDS);
    }

    private static void doReconnect() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ServerData serverData = new ServerData(
                    "Hypixel", "mc.hypixel.net", ServerData.Type.OTHER);

            ConnectScreen.startConnecting(
                    new TitleScreen(),
                    mc,
                    ServerAddress.parseString("mc.hypixel.net"),
                    serverData,
                    false,
                    null);

            // Note: We no longer clear state here. IhanuatClient will clear it after
            // re-joining.
        });
    }

    public static void cancel() {
        if (pendingReconnect != null) {
            pendingReconnect.cancel(false);
        }
        RestStateManager.clearState();
    }

    public static boolean isPending() {
        return pendingReconnect != null && !pendingReconnect.isDone();
    }
}
