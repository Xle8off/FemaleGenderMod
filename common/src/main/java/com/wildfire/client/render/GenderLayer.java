package com.wildfire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wildfire.api.uvs.UVLayout;
import com.wildfire.client.ClientHelper;
import com.wildfire.client.appearance.AppearanceState;
import com.wildfire.client.appearance.BreastShape;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.client.music.SpringPlusSession;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireHelper;
import com.wildfire.common.entitydata.BreastState;
import java.util.Objects;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.UnknownNullability;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public class GenderLayer<STATE extends HumanoidRenderState, MODEL extends HumanoidModel<STATE>> extends RenderLayer<STATE, MODEL> {
   private static final BreastSide[] BREAST_SIDES = BreastSide.values();
   private WildfireModelRenderer.@UnknownNullability("null until #resizeBox() is first called") ModelBox lBreast;
   private WildfireModelRenderer.@UnknownNullability("null until #resizeBox() is first called") ModelBox rBreast;
   private WildfireModelRenderer.@UnknownNullability("null until #resizeBox() is first called") ModelBox lBreastWear;
   private WildfireModelRenderer.@UnknownNullability("null until #resizeBox() is first called") ModelBox rBreastWear;
   private WildfireModelRenderer.ModelBox lBreastShirt;
   private WildfireModelRenderer.ModelBox rBreastShirt;
   private @Nullable UVLayout prevLeftBreastUVLayout;
   private @Nullable UVLayout prevRightBreastUVLayout;
   private @Nullable UVLayout prevLeftBreastOverlayUVLayout;
   private @Nullable UVLayout prevRightBreastOverlayUVLayout;
   private int previousShapeBucket = -1;
   private BreastShape previousBreastShape;
   private final RenderLayerParent<STATE, MODEL> context;
   private boolean isUniboob;
   protected boolean isChestplateOccupied;
   protected boolean bounceEnabled;
   protected boolean breathingAnimation;
   protected float breastOffsetX;
   protected float breastOffsetY;
   protected float breastOffsetZ;
   protected float lPhysPositionY;
   protected float lPhysPositionX;
   protected float rPhysPositionY;
   protected float rPhysPositionX;
   protected float lPhysBounceRotation;
   protected float rPhysBounceRotation;
   protected float breastSize;
   protected float zOffset;
   protected float outwardAngle;
   private float deformationX;
   private float deformationY;
   private float softPhysicsScale;

   public GenderLayer(RenderLayerParent<STATE, MODEL> render) {
      super(render);
      this.context = render;
   }

   public void submit(PoseStack matrixStack, SubmitNodeCollector nodeCollector, int lightCoords, STATE state, float limbAngle, float limbDistance) {
      GenderRenderState genderRenderState = ClientHelper.INSTANCE.getRenderState(state);
      if (genderRenderState != null) {
         try {
            if (!this.setupRender(state, genderRenderState)) {
               return;
            }

            int overlayCoords = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
            this.renderSides(state, (MODEL)this.getParentModel(), genderRenderState, matrixStack, nodeCollector, lightCoords, overlayCoords, this::renderBreast);
         } catch (Exception e) {
            WildfireGender.LOGGER.error("Failed to render breast layer", e);
         }
      }
   }

   protected boolean setupRender(STATE entityState, GenderRenderState genderState) {
      if (!SpringPlusSession.enabled()) {
         return false;
      }

      if (ClientConfig.config().overrides().disableRendering().get()) {
         return false;
      }

      boolean armorPhysicsOverride = ClientConfig.config().overrides().armorPhysics().get();
      this.isChestplateOccupied = genderState.armor.coversBreasts() && !armorPhysicsOverride;
      if (!genderState.armor.alwaysHidesBreasts() && (genderState.showBreastsInArmor || !this.isChestplateOccupied)) {
         if (!this.isLayerVisible(entityState, genderState)) {
            return false;
         }

         BreastState breasts = genderState.breasts;
         this.breastOffsetX = WildfireHelper.round(breasts.offsets().x(), 1.0F);
         this.breastOffsetY = -WildfireHelper.round(breasts.offsets().y(), 1.0F);
         this.breastOffsetZ = -WildfireHelper.round(breasts.offsets().z(), 1.0F);
         this.isUniboob = genderState.uniboob;
         GenderRenderState.BreastPhysicsState leftPhysicsState = genderState.leftBreastPhysics;
         float bSize = leftPhysicsState.getBreastSize();
         this.outwardAngle = Math.round(breasts.cleavage() * 100.0F);
         this.outwardAngle = Math.min(this.outwardAngle, 10.0F);
         this.resizeBox(genderState, bSize);
         this.lPhysPositionY = leftPhysicsState.getPositionY();
         this.lPhysPositionX = leftPhysicsState.getPositionX();
         this.lPhysBounceRotation = leftPhysicsState.getBounceRotation();
         if (this.isUniboob) {
            this.rPhysPositionY = this.lPhysPositionY;
            this.rPhysPositionX = this.lPhysPositionX;
            this.rPhysBounceRotation = this.lPhysBounceRotation;
         } else {
            GenderRenderState.BreastPhysicsState rightPhysicsState = genderState.rightBreastPhysics;
            this.rPhysPositionY = rightPhysicsState.getPositionY();
            this.rPhysPositionX = rightPhysicsState.getPositionX();
            this.rPhysBounceRotation = rightPhysicsState.getBounceRotation();
         }

         this.breastSize = Math.min(bSize * 1.5F, 0.7F);
         if (bSize > 0.7F) {
            this.breastSize = bSize;
         }

         if (this.breastSize < 0.02F) {
            return false;
         }

         this.zOffset = 0.0625F - bSize * 0.0625F;
         this.breastSize = this.breastSize + 0.5F * Math.abs(bSize - 0.7F) * 2.0F;
         float resistance = Math.clamp(genderState.armor.physicsResistance(), 0.0F, 1.0F);
         this.softPhysicsScale = armorPhysicsOverride ? 1.0F : 1.0F - resistance;
         this.breathingAnimation = (armorPhysicsOverride || resistance <= 0.5F) && genderState.isBreathing;
         this.bounceEnabled = genderState.hasBreastPhysics && (!this.isChestplateOccupied || resistance < 1.0F);
         return true;
      } else {
         return false;
      }
   }

   protected boolean isLayerVisible(STATE state, GenderRenderState genderState) {
      return !state.isInvisibleToPlayer || state.appearsGlowing();
   }

   protected void resizeBox(GenderRenderState state, float breastSize) {
      int shapeBucket = Math.clamp(Math.round(breastSize * 40.0F), 0, 32);
      BreastShape shape = AppearanceState.shape(state.entityUUID);
      if (!Objects.equals(this.prevLeftBreastUVLayout, state.leftBreastUVLayout)
         || !Objects.equals(this.prevRightBreastUVLayout, state.rightBreastUVLayout)
         || !Objects.equals(this.prevLeftBreastOverlayUVLayout, state.leftBreastOverlayUVLayout)
         || !Objects.equals(this.prevRightBreastOverlayUVLayout, state.rightBreastOverlayUVLayout)
         || this.previousShapeBucket != shapeBucket
         || this.previousBreastShape != shape) {
         this.prevLeftBreastUVLayout = state.leftBreastUVLayout;
         this.prevRightBreastUVLayout = state.rightBreastUVLayout;
         this.prevLeftBreastOverlayUVLayout = state.leftBreastOverlayUVLayout;
         this.prevRightBreastOverlayUVLayout = state.rightBreastOverlayUVLayout;
         this.previousShapeBucket = shapeBucket;
         this.previousBreastShape = shape;
         if (shape == BreastShape.LEGACY) {
            this.lBreast = new WildfireModelRenderer.BreastModelBox(64, 64, -4.0F, 0.0F, 0.0F, 4, 5, 3, 0.0F, state.leftBreastUVLayout);
            this.rBreast = new WildfireModelRenderer.BreastModelBox(64, 64, 0.0F, 0.0F, 0.0F, 4, 5, 3, 0.0F, state.rightBreastUVLayout);
            this.lBreastWear = new WildfireModelRenderer.OverlayModelBox(64, 64, -4.0F, 0.0F, 0.0F, 4, 5, 3, 0.0F, state.leftBreastOverlayUVLayout);
            this.rBreastWear = new WildfireModelRenderer.OverlayModelBox(64, 64, 0.0F, 0.0F, 0.0F, 4, 5, 3, 0.0F, state.rightBreastOverlayUVLayout);
            this.lBreastShirt = new WildfireModelRenderer.OverlayModelBox(64, 64, -4.0F, 0.0F, 0.0F, 4, 5, 3, 0.38F, state.leftBreastOverlayUVLayout);
            this.rBreastShirt = new WildfireModelRenderer.OverlayModelBox(64, 64, 0.0F, 0.0F, 0.0F, 4, 5, 3, 0.38F, state.rightBreastOverlayUVLayout);
         } else {
            float size = shapeBucket / 40.0F;
            this.lBreast = new WildfireModelRenderer.RoundedBreastModelBox(64, 64, -2.0F, size, 0.0F, shape, state.leftBreastUVLayout);
            this.rBreast = new WildfireModelRenderer.RoundedBreastModelBox(64, 64, 2.0F, size, 0.0F, shape, state.rightBreastUVLayout);
            this.lBreastWear = new WildfireModelRenderer.RoundedBreastModelBox(64, 64, -2.0F, size, 0.035F, shape, state.leftBreastOverlayUVLayout);
            this.rBreastWear = new WildfireModelRenderer.RoundedBreastModelBox(64, 64, 2.0F, size, 0.035F, shape, state.rightBreastOverlayUVLayout);
            this.lBreastShirt = new WildfireModelRenderer.RoundedBreastModelBox(64, 64, -2.0F, size, 0.12F, 0.0F, shape, state.leftBreastOverlayUVLayout, true);
            this.rBreastShirt = new WildfireModelRenderer.RoundedBreastModelBox(64, 64, 2.0F, size, 0.12F, 0.0F, shape, state.rightBreastOverlayUVLayout, true);
         }
      }
   }

   protected void setupTransformations(STATE state, MODEL model, GenderRenderState genderState, PoseStack matrixStack, BreastSide side) {
      BreastShape attachmentShape = AppearanceState.shape(genderState.entityUUID);
      boolean softMesh = attachmentShape != BreastShape.LEGACY && attachmentShape != BreastShape.BLOCKS && attachmentShape != BreastShape.CONNECTED_BLOCKS;
      this.deformationX = 0.0F;
      this.deformationY = 0.0F;
      if (state.isBaby) {
         matrixStack.scale(state.ageScale, state.ageScale, state.ageScale);
         matrixStack.translate(0.0F, 0.75F, 0.0F);
      }

      TorsoAttachment.apply(matrixStack, model.root(), model.body);
      if (this.bounceEnabled && !softMesh) {
         matrixStack.translate(
            side.forSide(this.lPhysPositionX, this.rPhysPositionX) / 32.0F, side.forSide(this.lPhysPositionY, this.rPhysPositionY) / 32.0F, 0.0F
         );
      }

      matrixStack.translate(
         side.leftOrNegate(this.breastOffsetX) * 0.0625F, 0.05625F + this.breastOffsetY * 0.0625F, this.zOffset - 0.125F + this.breastOffsetZ * 0.0425F
      );
      if (!this.isUniboob) {
         matrixStack.translate(side.leftOrNegate(-0.125F), 0.0F, 0.0F);
      }

      if (this.bounceEnabled && !softMesh) {
         matrixStack.rotate(Axis.YP.rotationDegrees(side.forSide(this.lPhysBounceRotation, this.rPhysBounceRotation)));
      }

      if (!this.isUniboob) {
         matrixStack.translate(side.leftOrNegate(0.125F), 0.0F, 0.0F);
      }

      float rotation = this.breastSize;
      if (this.bounceEnabled && !softMesh) {
         matrixStack.translate(0.0F, -0.035F * this.breastSize, 0.0F);
         rotation -= side.forSide(this.lPhysPositionY, this.rPhysPositionY) / 12.0F;
      }

      rotation = Math.min(rotation, this.breastSize + 0.2F);
      rotation = Math.min(rotation, 1.0F);
      if (this.isChestplateOccupied) {
         matrixStack.translate(0.0F, 0.0F, 0.01F);
      }

      BreastShape selectedShape = AppearanceState.shape(genderState.entityUUID);
      float profileAngle = selectedShape == BreastShape.LEGACY
         ? 35.0F
         : (selectedShape != BreastShape.BLOCKS && selectedShape != BreastShape.CONNECTED_BLOCKS ? 9.0F : 0.0F);
      float appliedOutwardAngle = selectedShape == BreastShape.CONNECTED_BLOCKS ? 0.0F : this.outwardAngle;
      Quaternionf rotationTransform = side.forSide(Axis.YP, Axis.YN)
         .rotationDegrees(appliedOutwardAngle)
         .rotateX(-profileAngle * rotation * (float) (Math.PI / 180.0));
      if (this.breathingAnimation) {
         float f5 = -Mth.cos(state.ageInTicks * 0.09F) * 0.45F + 0.45F;
         rotationTransform.rotateX(f5 * (float) (Math.PI / 180.0));
      }

      matrixStack.rotate(rotationTransform);
      if (this.bounceEnabled) {
         TorsoMotionPhysics.Motion motion = TorsoMotionPhysics.sample(genderState.entityUUID, model.root(), model.body, state.ageInTicks);
         if (softMesh) {
            EmoteMotionBridge.Pose emote = EmoteMotionBridge.read(state, genderState.partialTicks);
            IndependentBreastMotion.Pair independent = IndependentBreastMotion.sample(
               genderState.entityUUID,
               state.ageInTicks,
               state.bodyRot * (float) (Math.PI / 180.0) + (emote == null ? model.root().yRot + model.body.yRot : emote.yaw()),
               emote == null ? model.root().xRot + model.body.xRot : emote.pitch(),
               emote == null ? model.root().zRot + model.body.zRot : emote.roll(),
               this.lPhysPositionX,
               this.lPhysPositionY,
               this.rPhysPositionX,
               this.rPhysPositionY,
               emote == null ? model.root().x + model.body.x : emote.x(),
               emote == null ? model.root().y + model.body.y : emote.y()
            );
            IndependentBreastMotion.Motion movement = this.isUniboob ? independent.left() : side.forSide(independent.left(), independent.right());
            this.deformationX = movement.x() * this.softPhysicsScale;
            this.deformationY = movement.y() * this.softPhysicsScale;
         } else {
            matrixStack.rotate(Axis.XP.rotationDegrees(motion.pitch()));
            matrixStack.rotate(Axis.ZP.rotationDegrees(side.leftOrNegate(motion.roll())));
         }
      }

      matrixStack.scale(0.9995F, 1.0F, 1.0F);
   }

   private void renderBreast(
      STATE state, GenderRenderState genderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, int lightCoords, int overlayCoords, BreastSide side
   ) {
      LivingEntityRenderer<?, STATE, ? extends EntityModel<? super STATE>> renderer = (LivingEntityRenderer<?, STATE, ? extends EntityModel<? super STATE>>)this.context;
      boolean bodyVisible = renderer.isBodyVisible(state);
      boolean forceTransparent = !bodyVisible && !state.isInvisibleToPlayer;
      boolean glowing = state.appearsGlowing();
      RenderType type = renderer.getRenderType(state, bodyVisible, forceTransparent, glowing);
      if (type != null) {
         int baseColor = forceTransparent ? 654311423 : -1;
         WildfireModelRenderer.ModelBox model = side.forSide(this.lBreast, this.rBreast);
         nodeCollector.submitModel(
            new BreastModel(model, this.deformationX, this.deformationY),
            state,
            poseStack,
            type,
            lightCoords,
            overlayCoords,
            baseColor,
            null,
            state.outlineColor);
         if (genderState.hasJacketLayer) {
            poseStack.translate(0.0F, 0.0F, -0.015F);
            poseStack.scale(1.05F, 1.05F, 1.05F);
            WildfireModelRenderer.ModelBox jacketModel = side.forSide(this.lBreastWear, this.rBreastWear);
            nodeCollector.order(1)
               .submitModel(
                  new BreastModel(jacketModel, this.deformationX, this.deformationY),
                  state,
                  poseStack,
                  type,
                  lightCoords,
                  overlayCoords,
                  baseColor,
                  null,
                  state.outlineColor);
         }

         int shirt = AppearanceState.cosmetics(genderState.entityUUID).shirt();
         Identifier custom = AppearanceState.garmentTexture(genderState.entityUUID, 0);
         if ((shirt != 0 || custom != null) && !HumanoidArmorLayer.shouldRender(state.chestEquipment, EquipmentSlot.CHEST)) {
            int[] colors = new int[]{0, -1447451, -14078668, -7581072};
            int color = custom == null && shirt > 0 && shirt < colors.length ? colors[shirt] : -1;
            if (forceTransparent) {
               color = color & 16777215 | 637534208;
            }

            nodeCollector.order(2)
               .submitModel(
                  new BreastModel(side.forSide(this.lBreastShirt, this.rBreastShirt), this.deformationX, this.deformationY),
                  state,
                  poseStack,
                  RenderTypes.entityCutout(custom == null ? Identifier.withDefaultNamespace("textures/block/white_concrete.png") : custom),
                  lightCoords,
                  overlayCoords,
                  color,
                  null,
                  state.outlineColor);
         }
      }
   }

   protected void renderSides(
      STATE state,
      MODEL model,
      GenderRenderState genderState,
      PoseStack matrixStack,
      SubmitNodeCollector nodeCollector,
      int lightCoords,
      int overlayCoords,
      GenderLayer.BreastSideRenderer<STATE> renderer
   ) {
      for (BreastSide breastSide : BREAST_SIDES) {
         matrixStack.pushPose();

         try {
            this.setupTransformations(state, model, genderState, matrixStack, breastSide);
            renderer.render(state, genderState, matrixStack, nodeCollector, lightCoords, overlayCoords, breastSide);
         } finally {
            matrixStack.popPose();
         }
      }
   }

   @FunctionalInterface
   protected interface BreastSideRenderer<STATE extends HumanoidRenderState> {
      void render(STATE var1, GenderRenderState var2, PoseStack var3, SubmitNodeCollector var4, int var5, int var6, BreastSide var7);
   }
}
