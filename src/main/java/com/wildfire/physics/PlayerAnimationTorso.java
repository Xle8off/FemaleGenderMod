package com.wildfire.physics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.util.RenderUtil;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.world.entity.Avatar;

/** Native Player Animation Library bridge used by Emotecraft 26.2. */
public final class PlayerAnimationTorso {
    private PlayerAnimationTorso() {}

    public static AnimatedTorsoPhysics.Motion apply(Avatar avatar, PoseStack poseStack, float frameTime) {
        try {
            var manager = PlayerAnimationAccess.getPlayerAnimManager(avatar);
            // Render layers may be submitted before PAL's renderer hook has refreshed
            // the animation stack for this frame. PAL internally deduplicates this call.
            manager.handleAnimations(manager.getTickDelta(), false, false);
            if (!manager.isActive()) return null;

            PlayerAnimBone torso = manager.get3DTransform("torso");
            PlayerAnimBone body = manager.get3DTransform("body");
            RenderUtil.translateMatrixToBone(poseStack, torso);
            // Emotes commonly animate the parent `body` bone while leaving `torso`
            // locally unchanged. Physics must observe the complete parent chain.
            float activeX = body.position.x + torso.position.x;
            float activeY = -(body.position.y + torso.position.y);
            float activePitch = body.rotation.x + torso.rotation.x;
            float activeYaw = body.rotation.y + torso.rotation.y;
            float activeRoll = body.rotation.z + torso.rotation.z;
            return AnimatedTorsoPhysics.sample(
                avatar.getUUID(), frameTime,
                activeX, activeY, activePitch, activeYaw, activeRoll
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
