package com.wildfire.mixins;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Entity submission runs even when the vanilla nickname is hidden. */
@Mixin(EntityRenderer.class)
abstract class ListeningCardMixin {
    @Inject(method="submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at=@At("TAIL"))
    private void springplus$musicCard(EntityRenderState entityState, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (!(entityState instanceof AvatarRenderState state)) return;
        var snapshot = com.wildfire.render.GenderRenderState.get(state);
        if(snapshot != null) com.wildfire.client.music.ListeningLabelRenderer.submit(snapshot.entityUUID, state, pose, collector, camera);
        if(snapshot != null) com.wildfire.client.music.MenuWorldRenderer.submit(snapshot.entityUUID, state, pose, collector, camera);
        if(snapshot != null) com.wildfire.client.overhead.OverheadMediaRenderer.submit(snapshot.entityUUID,state,pose,collector,camera);
    }
}
