package com.wildfire.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.client.render.CosmeticCubeRenderer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only imported atlas cubes bypass constructor-time renderer caches. */
@Mixin(ModelPart.Cube.class)
public abstract class CosmeticCubeMixin implements CosmeticCubeRenderer.CustomAtlas {
    @Unique private boolean springplus$customAtlas;
    @Override public void springplus$markCustomAtlas() { springplus$customAtlas = true; }

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void springplus$compileAtlas(PoseStack.Pose pose, VertexConsumer vertices,
                                        int light, int overlay, int color, CallbackInfo ci) {
        if (!springplus$customAtlas) return;
        CosmeticCubeRenderer.compile((ModelPart.Cube)(Object)this, pose, vertices, light, overlay, color);
        ci.cancel();
    }
}
