package com.wildfire.neoforge.common.mixins;

import com.wildfire.neoforge.appearance.HdSkinClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class HdAvatarMixin extends LivingEntityRenderer<Avatar, AvatarRenderState, HumanoidModel<AvatarRenderState>> {
    private HdAvatarMixin(EntityRendererProvider.Context context, HumanoidModel<AvatarRenderState> model, float shadow) {
        super(context, model, shadow);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void springplus$hdSkin(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        state.skin = HdSkinClient.skin(avatar.getUUID(), state.skin);
    }
}
