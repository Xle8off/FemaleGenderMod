/*
 * Wildfire's Female Gender Mod is a female gender mod created for Minecraft.
 * Copyright (C) 2023-present WildfireRomeo
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wildfire.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.WildfireHelper;
import com.wildfire.main.config.ClientConfig;
import com.wildfire.main.entitydata.BreastState;
import com.wildfire.main.networking.appearance.ServerAppearanceSync;
import com.wildfire.main.networking.appearance.BreastShape;
import com.wildfire.main.networking.appearance.GarmentClient;
import com.wildfire.main.uvs.UVLayout;
import com.wildfire.mixins.accessors.LivingEntityRendererAccessor;
import com.wildfire.render.WildfireModelRenderer.BreastModelBox;
import com.wildfire.render.WildfireModelRenderer.ModelBox;
import com.wildfire.render.WildfireModelRenderer.OverlayModelBox;
import com.wildfire.render.WildfireModelRenderer.RoundedBreastModelBox;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.UnknownNullability;
import org.joml.Quaternionf;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

// TODO split this into an AbstractGenderLayer?
/// @apiNote Only use this on the client side
public class GenderLayer<STATE extends HumanoidRenderState, MODEL extends HumanoidModel<STATE>> extends RenderLayer<STATE, MODEL> {

    /// @apiNote  **Do not modify this array**
    private static final BreastSide[] BREAST_SIDES = BreastSide.values();

    @UnknownNullability("null until #resizeBox() is first called")
    private ModelBox lBreast, rBreast;
    @UnknownNullability("null until #resizeBox() is first called")
    private ModelBox lBreastWear, rBreastWear;
    @UnknownNullability("null until #resizeBox() is first called")
    private ModelBox lBreastShirt, rBreastShirt;

    private @Nullable UVLayout prevLeftBreastUVLayout, prevRightBreastUVLayout,
        prevLeftBreastOverlayUVLayout, prevRightBreastOverlayUVLayout;
    private int previousShapeBucket = -1;
    private BreastShape previousBreastShape;

    private final RenderLayerParent<STATE, MODEL> context;

    private boolean isUniboob;
    protected boolean isChestplateOccupied, bounceEnabled, breathingAnimation;
    protected float breastOffsetX, breastOffsetY, breastOffsetZ, lPhysPositionY, lPhysPositionX, rPhysPositionY, rPhysPositionX,
            lPhysBounceRotation, rPhysBounceRotation, breastSize, zOffset, outwardAngle;

    public GenderLayer(RenderLayerParent<STATE, MODEL> render) {
        super(render);
        this.context = render;
    }

    @Override
    public void submit(PoseStack matrixStack, SubmitNodeCollector nodeCollector, int lightCoords, STATE state, float limbAngle, float limbDistance) {
        var genderRenderState = GenderRenderState.get(state);
        if (genderRenderState == null) return;

        try {
            if(!setupRender(state, genderRenderState)) return;
            //Note: We ignore LivingEntityRenderer#getWhiteOverlayProgress as it is always 0 for entities we attach the layer to
            int overlayCoords = LivingEntityRenderer.getOverlayCoords(state, 0);
            renderSides(state, getParentModel(), genderRenderState, matrixStack, nodeCollector, lightCoords, overlayCoords, this::renderBreast);
        } catch(Exception e) {
            WildfireGender.LOGGER.error("Failed to render breast layer", e);
        }
    }

    /// Common logic for setting up breast rendering
    ///
    /// @return `true` if rendering should continue
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean setupRender(STATE entityState, GenderRenderState genderState) {
        if (!com.wildfire.client.music.SpringPlusSession.enabled()) return false;
        if(!ClientConfig.RENDER_BREASTS) return false;

        boolean armorPhysicsOverride = ClientConfig.config().armorPhysicsOverride.get();
        isChestplateOccupied = genderState.armor.coversBreasts() && !armorPhysicsOverride;
        if (genderState.armor.alwaysHidesBreasts() || !genderState.showBreastsInArmor && isChestplateOccupied) {
            //If the armor always hides breasts or there is armor and the player configured breasts
            // to be hidden when wearing armor, we can just exit early rather than doing any calculations
            return false;
        }

        if(!isLayerVisible(entityState, genderState)) {
            return false;
        }

        BreastState breasts = genderState.breasts;
        breastOffsetX = WildfireHelper.round(breasts.offsets().x(), 1);
        breastOffsetY = -WildfireHelper.round(breasts.offsets().y(), 1);
        breastOffsetZ = -WildfireHelper.round(breasts.offsets().z(), 1);

        isUniboob = genderState.uniboob;

        GenderRenderState.BreastPhysicsState leftPhysicsState = genderState.leftBreastPhysics;
        final float bSize = leftPhysicsState.getBreastSize();
        outwardAngle = Math.round(breasts.cleavage() * 100f);
        outwardAngle = Math.min(outwardAngle, 10);

        resizeBox(genderState, bSize);

        lPhysPositionY = leftPhysicsState.getPositionY();
        lPhysPositionX = leftPhysicsState.getPositionX();
        lPhysBounceRotation = leftPhysicsState.getBounceRotation();
        if(isUniboob) {
            rPhysPositionY = lPhysPositionY;
            rPhysPositionX = lPhysPositionX;
            rPhysBounceRotation = lPhysBounceRotation;
        } else {
            GenderRenderState.BreastPhysicsState rightPhysicsState = genderState.rightBreastPhysics;
            rPhysPositionY = rightPhysicsState.getPositionY();
            rPhysPositionX = rightPhysicsState.getPositionX();
            rPhysBounceRotation = rightPhysicsState.getBounceRotation();
        }

        breastSize = Math.min(bSize * 1.5f, 0.7f); // Limit the max size to 0.7f

        if (bSize > 0.7f) {
            breastSize = bSize; // If bSize exceeds 0.7f, use bSize
        }

        if (breastSize < 0.02f) {
            return false; // Return false if breastSize is too small
        }

        zOffset = 0.0625f - (bSize * 0.0625f); // Calculate zOffset
        breastSize += 0.5f * Math.abs(bSize - 0.7f) * 2f; // Adjust breastSize based on bSize

        float resistance = Math.clamp(genderState.armor.physicsResistance(), 0, 1);
        softPhysicsScale = armorPhysicsOverride ? 1f : 1f - resistance;
        breathingAnimation = (armorPhysicsOverride || resistance <= 0.5F) && genderState.isBreathing;
        bounceEnabled = genderState.hasBreastPhysics && (!isChestplateOccupied || resistance < 1); //oh, you found this?
        return true;
    }

    protected boolean isLayerVisible(STATE state, GenderRenderState genderState) {
        return !state.isInvisibleToPlayer || state.appearsGlowing();
    }

    protected void resizeBox(GenderRenderState state, float breastSize) {
        int shapeBucket = Math.clamp(Math.round(breastSize * 40f), 0, 32);
        BreastShape shape = ServerAppearanceSync.shape(state.entityUUID);
        //TODO: Better way for this?
        if(!Objects.equals(this.prevLeftBreastUVLayout, state.leftBreastUVLayout)
                || !Objects.equals(this.prevRightBreastUVLayout, state.rightBreastUVLayout)
                || !Objects.equals(this.prevLeftBreastOverlayUVLayout, state.leftBreastOverlayUVLayout)
                || !Objects.equals(this.prevRightBreastOverlayUVLayout, state.rightBreastOverlayUVLayout)
                || previousShapeBucket != shapeBucket || previousBreastShape != shape) {

            this.prevLeftBreastUVLayout = state.leftBreastUVLayout;
            this.prevRightBreastUVLayout = state.rightBreastUVLayout;
            this.prevLeftBreastOverlayUVLayout = state.leftBreastOverlayUVLayout;
            this.prevRightBreastOverlayUVLayout = state.rightBreastOverlayUVLayout;
            this.previousShapeBucket = shapeBucket;
            this.previousBreastShape = shape;

            if (shape == BreastShape.LEGACY) {
                // Preserve the original FemaleGenderMod geometry exactly. The familiar
                // wedge profile comes from rotating these two 4x5x3 cuboids in setupTransformations.
                this.lBreast = new BreastModelBox(64, 64, -4F, 0F, 0F, 4, 5, 3, 0F, state.leftBreastUVLayout);
                this.rBreast = new BreastModelBox(64, 64, 0F, 0F, 0F, 4, 5, 3, 0F, state.rightBreastUVLayout);
                this.lBreastWear = new OverlayModelBox(64, 64, -4F, 0F, 0F, 4, 5, 3, 0F, state.leftBreastOverlayUVLayout);
                this.rBreastWear = new OverlayModelBox(64, 64, 0F, 0F, 0F, 4, 5, 3, 0F, state.rightBreastOverlayUVLayout);
                this.lBreastShirt = new OverlayModelBox(64, 64, -4F, 0F, 0F, 4, 5, 3, 0.38F, state.leftBreastOverlayUVLayout);
                this.rBreastShirt = new OverlayModelBox(64, 64, 0F, 0F, 0F, 4, 5, 3, 0.38F, state.rightBreastOverlayUVLayout);
            } else {
                float quantizedSize = shapeBucket / 40f;
                this.lBreast = new RoundedBreastModelBox(64, 64, -2F, quantizedSize, 0F, shape, state.leftBreastUVLayout);
                this.rBreast = new RoundedBreastModelBox(64, 64, 2F, quantizedSize, 0F, shape, state.rightBreastUVLayout);
                this.lBreastWear = new RoundedBreastModelBox(64, 64, -2F, quantizedSize, 0.035F, shape, state.leftBreastOverlayUVLayout);
                this.rBreastWear = new RoundedBreastModelBox(64, 64, 2F, quantizedSize, 0.035F, shape, state.rightBreastOverlayUVLayout);
                // The shirt torso leaves the bust area open, so this cloth can
                // follow the actual breast mesh instead of floating in front of it.
                this.lBreastShirt = new RoundedBreastModelBox(64, 64, -2F, quantizedSize, 0.12F, 0F, shape, state.leftBreastOverlayUVLayout, true);
                this.rBreastShirt = new RoundedBreastModelBox(64, 64, 2F, quantizedSize, 0.12F, 0F, shape, state.rightBreastOverlayUVLayout, true);
            }
        }
    }

    private float deformationX, deformationY;
    private float softPhysicsScale;

    protected void setupTransformations(STATE state, MODEL model, GenderRenderState genderState, PoseStack matrixStack, BreastSide side) {
        BreastShape attachmentShape = ServerAppearanceSync.shape(genderState.entityUUID);
        boolean softMesh = attachmentShape != BreastShape.LEGACY && attachmentShape != BreastShape.BLOCKS
            && attachmentShape != BreastShape.CONNECTED_BLOCKS;
        deformationX = 0f;
        deformationY = 0f;
        if(state.isBaby) {
            matrixStack.scale(state.ageScale, state.ageScale, state.ageScale);
            matrixStack.translate(0f, 0.75f, 0f);
        }

        com.wildfire.client.render.TorsoAttachment.apply(matrixStack, model.root(), model.body);

        if(bounceEnabled && !softMesh) {
            matrixStack.translate(side.forSide(lPhysPositionX, rPhysPositionX) / 32f, side.forSide(lPhysPositionY, rPhysPositionY) / 32f, 0);
        }

        matrixStack.translate(side.leftOrNegate(breastOffsetX) * 0.0625f, 0.05625f + (breastOffsetY * 0.0625f), zOffset - 0.0625f * 2f + (breastOffsetZ * 0.0425f)); //shift down to correct position

        if(!isUniboob) {
            matrixStack.translate(side.leftOrNegate(-0.0625f * 2), 0, 0);
        }
        if(bounceEnabled && !softMesh) {
            matrixStack.mulPose(Axis.YP.rotationDegrees(side.forSide(lPhysBounceRotation, rPhysBounceRotation)));
        }
        if(!isUniboob) {
            matrixStack.translate(side.leftOrNegate(0.0625f * 2), 0, 0);
        }

        float rotation = breastSize;
        if(bounceEnabled && !softMesh) {
            matrixStack.translate(0, -0.035f * breastSize, 0); //shift down to correct position
            rotation -= side.forSide(lPhysPositionY, rPhysPositionY) / 12f;
        }

        rotation = Math.min(rotation, breastSize + 0.2f);
        rotation = Math.min(rotation, 1); //hard limit for MAX

        if(isChestplateOccupied) {
            matrixStack.translate(0, 0, 0.01f);
        }

        BreastShape selectedShape = ServerAppearanceSync.shape(genderState.entityUUID);
        float profileAngle = selectedShape == BreastShape.LEGACY ? 35f
            : selectedShape == BreastShape.BLOCKS || selectedShape == BreastShape.CONNECTED_BLOCKS ? 0f : 9f;
        float appliedOutwardAngle = selectedShape == BreastShape.CONNECTED_BLOCKS ? 0f : outwardAngle;
        Quaternionf rotationTransform = side.forSide(Axis.YP, Axis.YN)
            .rotationDegrees(appliedOutwardAngle)
            .rotateX(-profileAngle * rotation * Mth.DEG_TO_RAD);

        if(breathingAnimation) {
            float f5 = -Mth.cos(state.ageInTicks * 0.09F) * 0.45F + 0.45F;
            rotationTransform.rotateX(f5 * Mth.DEG_TO_RAD);
        }

        matrixStack.mulPose(rotationTransform);
        if (bounceEnabled) {
            var motion = com.wildfire.client.render.TorsoMotionPhysics.sample(
                genderState.entityUUID, model.root(), model.body, state.ageInTicks);
            if (softMesh) {
                var emote = com.wildfire.client.render.EmoteMotionBridge.read(state, genderState.partialTicks);
                var independent = com.wildfire.client.render.IndependentBreastMotion.sample(
                    genderState.entityUUID, state.ageInTicks,
                    state.bodyRot * Mth.DEG_TO_RAD + (emote == null
                        ? model.root().yRot + model.body.yRot : emote.yaw()),
                    emote == null ? model.root().xRot + model.body.xRot : emote.pitch(),
                    emote == null ? model.root().zRot + model.body.zRot : emote.roll(),
                    lPhysPositionX, lPhysPositionY, rPhysPositionX, rPhysPositionY,
                    emote == null ? model.root().x + model.body.x : emote.x(),
                    emote == null ? model.root().y + model.body.y : emote.y());
                var movement = isUniboob ? independent.left() : side.forSide(independent.left(), independent.right());
                deformationX = movement.x() * softPhysicsScale;
                deformationY = movement.y() * softPhysicsScale;
            } else {
                matrixStack.mulPose(Axis.XP.rotationDegrees(motion.pitch()));
                matrixStack.mulPose(Axis.ZP.rotationDegrees(side.leftOrNegate(motion.roll())));
            }
        }
        matrixStack.scale(0.9995f, 1f, 1f); //z-fighting FIXXX
    }

    private void renderBreast(STATE state, GenderRenderState genderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, int lightCoords, int overlayCoords, BreastSide side) {
        // Convenience method around [LivingEntityRendererAccessor#invokeGetRenderType]
        var renderer = (LivingEntityRenderer<?, ?, ?>) context;
        var accessor = (LivingEntityRendererAccessor) renderer;

        boolean bodyVisible = accessor.invokeIsBodyVisible(state);
        boolean forceTransparent = !bodyVisible && !state.isInvisibleToPlayer;
        boolean glowing = state.appearsGlowing();

        RenderType type = accessor.invokeGetRenderType(state, bodyVisible, forceTransparent, glowing);
        // Convenience method end
        if(type == null) return; // only render if the player is visible in some capacity

        int baseColor = forceTransparent ? 0x26FFFFFF : CommonColors.WHITE;
        //Note: We ignore LivingEntityRenderer#getModelTint as it is always WHITE for entities we attach the layer to

        //Note: While the living entity renderer uses the light coords, we mirror Deadmau5EarsLayer and use the passed in light coords
        // (which the renderer passes the state's light coords in, but we might as well be consistent with how vanilla does extra body parts as a layer)
        var model = side.forSide(lBreast, rBreast);
        nodeCollector.submitModel(new BreastModel(model, deformationX, deformationY), state, poseStack, type, lightCoords, overlayCoords, baseColor, null, state.outlineColor, null);

        if (genderState.hasJacketLayer) {
            var jacketModel = side.forSide(lBreastWear, rBreastWear);
            nodeCollector.order(1).submitModel(new BreastModel(jacketModel, deformationX, deformationY), state, poseStack, type, lightCoords, overlayCoords, baseColor, null, state.outlineColor, null);
        }

        int shirt = ServerAppearanceSync.cosmetics(genderState.entityUUID).shirt();
        var custom = GarmentClient.texture(genderState.entityUUID, 0);
        if ((shirt != 0 || custom != null) && !HumanoidArmorLayer.shouldRender(state.chestEquipment, EquipmentSlot.CHEST)) {
            int color = custom == null ? CosmeticLayer.shirtColor(shirt) : 0xFFFFFFFF;
            if (forceTransparent) color = (color & 0x00FFFFFF) | 0x26000000;
            var shirtModel = side.forSide(lBreastShirt, rBreastShirt);
            nodeCollector.order(2).submitModel(new BreastModel(shirtModel, deformationX, deformationY), state, poseStack,
                RenderTypes.entityCutout(custom == null ? CosmeticLayer.FABRIC : custom), lightCoords, overlayCoords,
                color, null, state.outlineColor, null);
        }
    }

    protected void renderSides(STATE state, MODEL model, GenderRenderState genderState, PoseStack matrixStack, SubmitNodeCollector nodeCollector, int lightCoords,
        int overlayCoords, BreastSideRenderer<STATE> renderer) {
        for (final BreastSide breastSide : BREAST_SIDES) {
            matrixStack.pushPose();
            try {
                setupTransformations(state, model, genderState, matrixStack, breastSide);
                renderer.render(state, genderState, matrixStack, nodeCollector, lightCoords, overlayCoords, breastSide);
            } finally {
                matrixStack.popPose();
            }
        }
    }

    @FunctionalInterface
    protected interface BreastSideRenderer<STATE extends HumanoidRenderState> {

        void render(STATE state, GenderRenderState genderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, int lightCoords, int overlayCoords, BreastSide side);
    }
}
