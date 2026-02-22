package com.ihanuat.mod.mixin;

import com.ihanuat.mod.IhanuatClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(CallbackInfo ci) {
        // Suppress mouse rotation during return sequence tracking
        if (IhanuatClient.shouldSuppressMouseRotation()) {
            ci.cancel();
        }
    }
}
