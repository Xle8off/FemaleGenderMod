package dev.aeltorx.inflow.client.mixin;

import dev.aeltorx.inflow.client.ModerationPanelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
abstract class ModerationHudMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void inflow$minimized(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() == null) ModerationPanelScreen.drawMinimized(graphics);
    }
}
