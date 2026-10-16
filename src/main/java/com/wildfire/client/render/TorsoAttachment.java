package com.wildfire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;

/** A torso child inherits the FINAL vanilla/PAL pose, not raw animation-bone coordinates. */
public final class TorsoAttachment {
    private TorsoAttachment() {}
    public static void apply(PoseStack pose, ModelPart root, ModelPart torso) {
        root.translateAndRotate(pose);
        torso.translateAndRotate(pose);
    }
}
