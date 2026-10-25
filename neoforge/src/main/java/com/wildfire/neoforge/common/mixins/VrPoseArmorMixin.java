package com.wildfire.neoforge.common.mixins;

import com.wildfire.client.vr.VrPoseClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Armor is animated independently by the deferred model renderer in 26.2. */
@Mixin(value=HumanoidModel.class,priority=100)
abstract class VrPoseArmorMixin {
 @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",at=@At("TAIL"))
 private void springplus$vrArmor(HumanoidRenderState state,CallbackInfo ci){
  if(state instanceof AvatarRenderState avatar)
   VrPoseClient.apply(avatar,(HumanoidModel<?>)(Object)this);
 }
}
