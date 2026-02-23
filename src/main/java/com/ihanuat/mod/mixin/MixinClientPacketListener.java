package com.ihanuat.mod.mixin;

import com.ihanuat.mod.IhanuatClient;
import com.ihanuat.mod.ReconnectScheduler;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class MixinClientPacketListener {

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void onDisconnect(DisconnectionDetails details, CallbackInfo ci) {
        // Only act if the macro considers it a non-intentional disconnect
        if (IhanuatClient.isMacroRunning() && !IhanuatClient.isIntentionalDisconnect()) {
            // Unexpected kick — reconnect after short delay (30-60s)
            long delay = 30 + (long) (Math.random() * 30);
            ReconnectScheduler.scheduleReconnect(delay, true);
        }
    }
}
