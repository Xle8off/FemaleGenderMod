package com.wildfire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wildfire.api.IBreastArmorTexture;
import com.wildfire.api.uvs.UVMap;
import com.wildfire.client.ClientHelper;
import com.wildfire.common.WildfireGender;
import java.util.Objects;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo.LayerType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.UnknownNullability;
import org.joml.Vector2ic;

public class GenderArmorLayer<STATE extends HumanoidRenderState, MODEL extends HumanoidModel<STATE>> extends GenderLayer<STATE, MODEL> {
   private final EquipmentLayerRenderer equipmentRenderer;
   private WildfireModelRenderer.@UnknownNullability("null until #resizeBox() is first called") BreastModelBox lBoobArmor;
   private WildfireModelRenderer.@UnknownNullability("null until #resizeBox() is first called") BreastModelBox rBoobArmor;
   private IBreastArmorTexture textureData = IBreastArmorTexture.DEFAULT;

   public GenderArmorLayer(RenderLayerParent<STATE, MODEL> render, EquipmentLayerRenderer equipmentRenderer) {
      super(render);
      this.equipmentRenderer = equipmentRenderer;
   }

   @Override
   public void submit(PoseStack matrixStack, SubmitNodeCollector nodeCollector, int light, STATE state, float limbAngle, float limbDistance) {
      GenderRenderState genderRenderState = ClientHelper.INSTANCE.getRenderState(state);
      if (genderRenderState != null && HumanoidArmorLayer.shouldRender(state.chestEquipment, EquipmentSlot.CHEST)) {
         try {
            if (!this.setupRender(state, genderRenderState)) {
               return;
            }

            if (this.isArmorStand(state) && !genderRenderState.armor.armorStandsCopySettings()) {
               return;
            }

            this.renderSides(
               state, (MODEL)this.getParentModel(), genderRenderState, matrixStack, nodeCollector, light, OverlayTexture.NO_OVERLAY, this::renderArmor
            );
         } catch (Exception e) {
            WildfireGender.LOGGER.error("Failed to render breast armor", e);
         }
      }
   }

   private boolean isArmorStand(STATE state) {
      return state.entityType == EntityTypes.ARMOR_STAND;
   }

   private void renderArmor(
      STATE state, GenderRenderState genderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, int lightCoords, int overlayCoords, BreastSide side
   ) {
      Equippable equippable = Objects.requireNonNull(
         (Equippable)state.chestEquipment.get(DataComponents.EQUIPPABLE), "Chest slot is null after having not been null at the start of render call"
      );
      LayerType layerType = state.isBaby && !this.isArmorStand(state) ? LayerType.HUMANOID_BABY : LayerType.HUMANOID;
      BreastModel model = new BreastModel(side.forSide(this.lBoobArmor, this.rBoobArmor));
      this.equipmentRenderer
         .renderLayers(
            layerType,
            (ResourceKey)equippable.assetId().orElseThrow(),
            model,
            state,
            state.chestEquipment,
            poseStack,
            nodeCollector,
            lightCoords,
            null,
            state.outlineColor,
            genderState.hasJacketLayer ? 2 : 1
         );
   }

   @Override
   protected boolean isLayerVisible(STATE state, GenderRenderState genderState) {
      return genderState.armor.coversBreasts();
   }

   @Override
   protected void resizeBox(GenderRenderState state, float breastSize) {
      if (this.lBoobArmor == null || this.rBoobArmor == null || !Objects.equals(this.textureData, state.armor.texture())) {
         this.textureData = state.armor.texture();
         Vector2ic texSize = this.textureData.textureSize();
         UVMap uvs = this.textureData.uvs();
         this.lBoobArmor = new WildfireModelRenderer.BreastModelBox(texSize.x(), texSize.y(), -4.0F, 0.0F, 0.0F, 4, 5, 3, 0.0F, uvs.left());
         this.rBoobArmor = new WildfireModelRenderer.BreastModelBox(texSize.x(), texSize.y(), 0.0F, 0.0F, 0.0F, 4, 5, 3, 0.0F, uvs.right());
      }
   }

   @Override
   protected void setupTransformations(STATE state, MODEL model, GenderRenderState genderState, PoseStack matrixStack, BreastSide side) {
      super.setupTransformations(state, model, genderState, matrixStack, side);
      if (genderState.hasJacketLayer) {
         matrixStack.translate(0.0F, 0.0F, -0.015F);
         matrixStack.scale(1.05F, 1.05F, 1.05F);
      }

      matrixStack.translate(side.leftOrNegate(0.001F), 0.015F, -0.015F);
      matrixStack.scale(1.05F, 1.0F, 1.0F);
   }
}
