package com.wildfire.neoforge.common.mixins;

import com.wildfire.neoforge.client.RoundedPanels;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerTabOverlay.class)
abstract class RoundedTabMixin {
    @Shadow public Component header;
    @Shadow public Component footer;

    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0),
        require = 0)
    private void springplus$tabHeader(GuiGraphicsExtractor graphics, int left, int top,
                                       int right, int bottom, int color) {
        RoundedPanels.fillEdges(graphics, left, top, right, bottom, color, true, false, 4);
    }

    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1),
        require = 0)
    private void springplus$tabBody(GuiGraphicsExtractor graphics, int left, int top,
                                     int right, int bottom, int color) {
        RoundedPanels.fillEdges(graphics, left, top, right, bottom, color,
            header == null, footer == null, 4);
    }

    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 3),
        require = 0)
    private void springplus$tabFooter(GuiGraphicsExtractor graphics, int left, int top,
                                       int right, int bottom, int color) {
        RoundedPanels.fillEdges(graphics, left, top, right, bottom, color, false, true, 4);
    }
}
