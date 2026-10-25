package com.wildfire.common.mixins;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.wildfire.client.render.CosmeticCubeRenderer;
import net.minecraft.client.model.geom.ModelPart.Cube;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Cube.class)
public abstract class CosmeticCubeMixin implements CosmeticCubeRenderer.CustomAtlas {
   @Unique
   private boolean springplus$customAtlas;

   @Override
   public void springplus$markCustomAtlas() {
      this.springplus$customAtlas = true;
   }

   @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
   private void springplus$compileAtlas(Pose pose, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
      if (this.springplus$customAtlas) {
         CosmeticCubeRenderer.compile((Cube)(Object)this, pose, vertices, light, overlay, color);
         ci.cancel();
      }
   }
}
