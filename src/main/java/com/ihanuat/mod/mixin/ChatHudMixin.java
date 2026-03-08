package com.ihanuat.mod.mixin;

import com.ihanuat.mod.MacroConfig;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatHudMixin {

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, CallbackInfo ci) {
        if (!MacroConfig.hideFilteredChat) {
            return;
        }

        String text = message.getString();

        // ── Filter patterns: search-based (contains) ────────────────────────────────
        if (text.contains("for killing a") ||
                text.contains("Starting script via command")) {

            ci.cancel();
        }
    }
}
