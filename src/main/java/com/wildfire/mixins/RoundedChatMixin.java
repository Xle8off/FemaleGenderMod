package com.wildfire.mixins;

import com.wildfire.gui.RoundedChatBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
abstract class RoundedChatMixin {
    @Redirect(method = "fill",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"),
        require = 0)
    private void springplus$chatLine(GuiGraphicsExtractor graphics, int left, int top,
                                      int right, int bottom, int color) {
        RoundedChatBackground.fill(graphics, left, top, right, bottom, color);
    }
}
