package com.wildfire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Util;

public class BreastModel extends Model<HumanoidRenderState> {
   private static final ModelPart DUMMY_PART = (ModelPart)Util.make(() -> {
      MeshDefinition part = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
      part.getRoot().clearRecursively();
      return part.getRoot().bake(64, 64);
   });
   private final WildfireModelRenderer.ModelBox model;
   private final float displacementX;
   private final float displacementY;

   public BreastModel(WildfireModelRenderer.ModelBox model) {
      this(model, 0.0F, 0.0F);
   }

   public BreastModel(WildfireModelRenderer.ModelBox model, float displacementX, float displacementY) {
      super(DUMMY_PART, RenderTypes::entityCutout);
      this.model = model;
      this.displacementX = displacementX;
      this.displacementY = displacementY;
   }

   public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int light, int overlay, int color) {
      WildfireModelRenderer.renderBox(this.model, poseStack.last(), vertexConsumer, light, overlay, color, this.displacementX, this.displacementY);
   }
}
