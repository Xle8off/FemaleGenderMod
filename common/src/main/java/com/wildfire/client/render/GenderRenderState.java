package com.wildfire.client.render;

import com.wildfire.api.Gender;
import com.wildfire.api.IGenderArmor;
import com.wildfire.api.uvs.UVLayout;
import com.wildfire.client.WildfireClientHelper;
import com.wildfire.client.WildfireGenderClient;
import com.wildfire.client.physics.BothBreastsPhysics;
import com.wildfire.client.physics.BreastPhysics;
import com.wildfire.common.entitydata.BreastState;
import com.wildfire.common.entitydata.EntityConfigHolder;
import com.wildfire.common.entitydata.PlayerConfig;
import java.util.UUID;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

public class GenderRenderState {
   public final UUID entityUUID;
   public final BreastState breasts;
   public final boolean uniboob;
   public final GenderRenderState.BreastPhysicsState leftBreastPhysics;
   public final GenderRenderState.BreastPhysicsState rightBreastPhysics;
   public final float partialTicks;
   public final Gender gender;
   public final float bustSize;
   public final boolean hasBreastPhysics;
   public final float bounceMultiplier;
   public final float floppyMultiplier;
   public final boolean showBreastsInArmor;
   public final boolean hasJacketLayer;
   public final UVLayout leftBreastUVLayout;
   public final UVLayout rightBreastUVLayout;
   public final UVLayout leftBreastOverlayUVLayout;
   public final UVLayout rightBreastOverlayUVLayout;
   public final IGenderArmor armor;
   public final boolean isBreathing;
   public final @Nullable Component nametag;

   public GenderRenderState(EntityConfigHolder<?> entityConfig, LivingEntity entity, HumanoidRenderState entityState, float partialTicks) {
      this.entityUUID = entity.getUUID();
      this.breasts = new BreastState(entityConfig.breasts());
      this.uniboob = entityConfig.breasts().physics().uniboob().get();
      BothBreastsPhysics breastPhysics = entityConfig.breastPhysics();
      this.leftBreastPhysics = new GenderRenderState.BreastPhysicsState(breastPhysics.left());
      this.rightBreastPhysics = new GenderRenderState.BreastPhysicsState(breastPhysics.right());
      this.partialTicks = partialTicks;
      this.gender = entityConfig.gender().get();
      this.bustSize = entityConfig.breasts().bustSize().get();
      this.hasBreastPhysics = entityConfig.breasts().physics().enabled().get();
      this.bounceMultiplier = entityConfig.breasts().physics().bounceMultiplier().get();
      this.floppyMultiplier = entityConfig.breasts().physics().floppiness().get();
      if (entityState instanceof AvatarRenderState avatarState) {
         this.hasJacketLayer = avatarState.showJacket;
      } else {
         this.hasJacketLayer = entityConfig.config() instanceof PlayerConfig || entityConfig.hasJacketLayer();
      }

      if (entityConfig.config() instanceof PlayerConfig playerConfig) {
         this.showBreastsInArmor = playerConfig.showBreastsInArmor.get();
      } else {
         this.showBreastsInArmor = true;
      }

      this.leftBreastUVLayout = entityConfig.uvs().skin().left().get().copy();
      this.rightBreastUVLayout = entityConfig.uvs().skin().right().get().copy();
      this.leftBreastOverlayUVLayout = entityConfig.uvs().overlay().left().get().copy();
      this.rightBreastOverlayUVLayout = entityConfig.uvs().overlay().right().get().copy();
      this.armor = WildfireClientHelper.getArmorConfig(entityState.chestEquipment);
      this.isBreathing = !entity.isUnderWater()
         || MobEffectUtil.hasWaterBreathing(entity)
         || entity.level().getBlockState(entity.blockPosition()).is(Blocks.BUBBLE_COLUMN);
      this.nametag = entity instanceof Player ? WildfireGenderClient.getNametag(entity.getUUID()) : null;
   }

   public class BreastPhysicsState {
      private final float prePositionY;
      private final float positionY;
      private final float prePositionX;
      private final float positionX;
      private final float preBounceRotation;
      private final float bounceRotation;
      private final float preBreastSize;
      private final float breastSize;

      private BreastPhysicsState(BreastPhysics breastPhysics) {
         this.prePositionY = breastPhysics.getPrePositionY();
         this.positionY = breastPhysics.getPositionY();
         this.prePositionX = breastPhysics.getPrePositionX();
         this.positionX = breastPhysics.getPositionX();
         this.preBounceRotation = breastPhysics.getPreBounceRotation();
         this.bounceRotation = breastPhysics.getBounceRotation();
         this.preBreastSize = breastPhysics.getPreBreastSize();
         this.breastSize = breastPhysics.getBreastSize();
      }

      public float getPositionY() {
         return Mth.lerp(GenderRenderState.this.partialTicks, this.prePositionY, this.positionY);
      }

      public float getPositionX() {
         return Mth.lerp(GenderRenderState.this.partialTicks, this.prePositionX, this.positionX);
      }

      public float getBounceRotation() {
         return Mth.lerp(GenderRenderState.this.partialTicks, this.preBounceRotation, this.bounceRotation);
      }

      public float getBreastSize() {
         return Mth.lerp(GenderRenderState.this.partialTicks, this.preBreastSize, this.breastSize);
      }
   }
}
