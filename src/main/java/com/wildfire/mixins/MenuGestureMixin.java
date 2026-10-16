package com.wildfire.mixins;
import com.wildfire.client.music.MenuWorldRenderer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PlayerModel.class,priority=900)
abstract class MenuGestureMixin {
    @Inject(method="translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",at=@At("TAIL"))
    private void springplus$menuHand(AvatarRenderState state,net.minecraft.world.entity.HumanoidArm side,com.mojang.blaze3d.vertex.PoseStack pose,CallbackInfo ci) {
        if(side==net.minecraft.world.entity.HumanoidArm.RIGHT)
            com.wildfire.client.music.MenuArmBend.hand(((PlayerModel)(Object)this).rightArm,pose);
    }
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("HEAD"))
    private void springplus$resetMenuGesture(AvatarRenderState state,CallbackInfo ci) {
        var model=(PlayerModel)(Object)this;
        com.wildfire.client.music.MenuArmBend.set(model.rightArm,0f);
        com.wildfire.client.music.MenuArmBend.sleeve(model.rightSleeve,0f);
    }
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("TAIL"))
    private void springplus$menuGesture(AvatarRenderState state,CallbackInfo ci) {
        var snapshot=com.wildfire.render.GenderRenderState.get(state);
        if(snapshot==null)return;
        var model=(PlayerModel)(Object)this;
        if(MenuWorldRenderer.gesture(snapshot.entityUUID,state,model)) {
            // 26.2 sleeves are children of the arm: copying its pose applies the rotation twice.
            com.wildfire.client.music.MenuArmBend.sleeve(model.rightSleeve,model.rightArm,com.wildfire.client.music.MenuArmBend.angle(model.rightArm));
        }
    }
}
