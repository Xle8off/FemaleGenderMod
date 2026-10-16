package com.wildfire.mixins;

import com.wildfire.gui.RoundedPanels;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
abstract class RoundedHudMixin {
    @Redirect(method = "displayScoreboardSidebar",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"),
        require = 0)
    private void springplus$scoreboardPanel(GuiGraphicsExtractor graphics, int left, int top,
                                             int right, int bottom, int color) {
        RoundedPanels.fill(graphics, left, top, right, bottom, color, 3);
    }
}
