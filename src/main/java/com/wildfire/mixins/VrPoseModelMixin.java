package com.wildfire.mixins;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PlayerModel.class,priority=100)
abstract class VrPoseModelMixin {
 @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("TAIL"))
 private void springplus$vr(AvatarRenderState state,CallbackInfo ci){com.wildfire.client.vr.VrPoseClient.apply(state,(PlayerModel)(Object)this);}
}
