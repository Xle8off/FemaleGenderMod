package com.wildfire.client.physics;

import com.wildfire.api.IGenderArmor;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.common.entitydata.EntityConfigHolder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class BreastPhysics {
   public static final float TIGHTNESS_REDUCTION_FACTOR = 0.15F;
   private float bounceVelX = 0.0F;
   private float targetBounceX = 0.0F;
   private float velocityX = 0.0F;
   private float positionX;
   private float prePositionX;
   private float bounceVel = 0.0F;
   private float targetBounceY = 0.0F;
   private float velocity = 0.0F;
   private float positionY;
   private float prePositionY;
   private float bounceRotVel = 0.0F;
   private float targetRotVel = 0.0F;
   private float rotVelocity = 0.0F;
   private float bounceRotation;
   private float preBounceRotation;
   private float breastSize = 0.0F;
   private float preBreastSize = 0.0F;
   private @Nullable Pose lastPose;
   private int lastSwingDuration = 6;
   private int lastSwingTick = 0;
   private @Nullable Vec3 prePos;
   private final EntityConfigHolder<?> entityConfig;
   private int randomB = 1;
   private double lastVerticalMoveVelocity;

   public BreastPhysics(EntityConfigHolder<?> entityConfig) {
      this.entityConfig = entityConfig;
   }

   private static boolean vehicleSuppressesRotation(Entity vehicle) {
      return switch (vehicle) {
         case Chicken var5 -> true;
         case AbstractHorse horse when !horse.isSaddled() -> true;
         case Camel camel when camel.refuseToMove() -> true;
         default -> false;
      };
   }

   private static boolean shouldUseVehicleYaw(LivingEntity rider, Entity vehicle) {
      return vehicle.hasControllingPassenger() || vehicle instanceof Boat || vehicle.getVisualRotationYInDegrees() == rider.getVisualRotationYInDegrees();
   }

   private float calcRotation(LivingEntity entity, float bounceIntensity) {
      Entity vehicle = entity.getVehicle();
      if (vehicle != null) {
         if (vehicleSuppressesRotation(vehicle)) {
            return 0.0F;
         }

         if (shouldUseVehicleYaw(entity, vehicle)) {
            float previous = vehicle instanceof LivingEntity living ? living.yBodyRotO : vehicle.yRotO;
            return -((vehicle.getVisualRotationYInDegrees() - previous) / 15.0F) * bounceIntensity;
         }
      }

      return -((entity.yBodyRot - entity.yBodyRotO) / 15.0F) * bounceIntensity;
   }

   public void update(LivingEntity entity, IGenderArmor armor) {
      boolean armorPhysicsOverride = ClientConfig.config().overrides().armorPhysics().get();
      if (!(entity instanceof ArmorStand) && !this.entityConfig.forceSimplifiedPhysics) {
         RandomSource random = RandomSource.createThreadLocalInstance();
         this.prePositionY = this.positionY;
         this.prePositionX = this.positionX;
         this.preBounceRotation = this.bounceRotation;
         this.preBreastSize = this.breastSize;
         if (this.prePos == null) {
            this.prePos = entity.position();
         } else {
            float targetBreastSize = this.entityConfig.breasts().bustSize().get();
            float breastWeight = targetBreastSize * 1.25F;
            if (!this.entityConfig.gender().get().canHaveBreasts()) {
               targetBreastSize = 0.0F;
            } else {
               float tightness = Math.clamp(armor.tightness(), 0.0F, 1.0F);
               if (armorPhysicsOverride) {
                  tightness = 0.0F;
               }

               targetBreastSize *= 1.0F - 0.15F * tightness;
            }

            this.breastSize = this.breastSize
               + (
                  this.breastSize < targetBreastSize
                     ? Math.abs(this.breastSize - targetBreastSize) / 2.0F
                     : -Math.abs(this.breastSize - targetBreastSize) / 2.0F
               );
            Vec3 motion = entity.position().subtract(this.prePos);
            this.prePos = entity.position();
            float bounceIntensity = targetBreastSize
               * 3.0F
               * Math.round(this.entityConfig.breasts().physics().bounceMultiplier().get() * 3.0F * 100.0F)
               / 100.0F;
            float resistance = Math.clamp(armor.physicsResistance(), 0.0F, 1.0F);
            if (armorPhysicsOverride) {
               resistance = 0.0F;
            }

            bounceIntensity *= 1.0F - resistance;
            if (!this.entityConfig.breasts().physics().uniboob().get()) {
               bounceIntensity *= Mth.randomBetween(random, 0.5F, 2.5F);
            }

            this.tickMovement(entity, random, motion, bounceIntensity, breastWeight);
            this.tickPose(entity, bounceIntensity);
            this.tickVehicle(entity, random, bounceIntensity, breastWeight);
            this.tickArmSwing(entity, random, bounceIntensity);
            this.finishTick();
         }
      } else {
         this.simplifiedTick(armor, armorPhysicsOverride);
      }
   }

   private void simplifiedTick(IGenderArmor armor, boolean armorPhysicsOverride) {
      if (this.entityConfig.gender().get().canHaveBreasts()) {
         this.breastSize = this.entityConfig.breasts().bustSize().get();
         if (!armorPhysicsOverride) {
            float tightness = Math.clamp(armor.tightness(), 0.0F, 1.0F);
            this.breastSize *= 1.0F - 0.15F * tightness;
         }

         this.preBreastSize = this.breastSize;
      } else {
         this.preBreastSize = this.breastSize = 0.0F;
      }
   }

   private void tickMovement(LivingEntity entity, RandomSource random, Vec3 motion, float bounceIntensity, float breastWeight) {
      double vertVelocity = entity.getDeltaMovement().y;
      if (this.lastVerticalMoveVelocity <= 0.0 && vertVelocity > 0.0 || this.lastVerticalMoveVelocity < 0.0 && vertVelocity == 0.0) {
         this.randomB = random.nextBoolean() ? -1 : 1;
      }

      this.lastVerticalMoveVelocity = vertVelocity;
      this.targetBounceY = (float)motion.y * bounceIntensity;
      this.targetBounceY += breastWeight;
      this.targetRotVel = this.calcRotation(entity, bounceIntensity);
      this.targetRotVel = this.targetRotVel + (float)motion.y * bounceIntensity * this.randomB;
      this.targetBounceX = -this.calcRotation(entity, bounceIntensity) / 10.0F;
      float speedValue = (float)entity.getDeltaMovement().lengthSqr() / 0.2F;
      speedValue = Mth.cube(speedValue);
      if (speedValue < 1.0F) {
         speedValue = 1.0F;
      }

      this.targetBounceY = this.targetBounceY
         + Mth.cos(entity.walkAnimation.position() * 0.6662F + (float) Math.PI) * 0.5F * entity.walkAnimation.speed() * 0.5F / speedValue;
   }

   private void tickPose(LivingEntity entity, float bounceIntensity) {
      Pose pose = entity.getPose();
      if (pose != this.lastPose) {
         if (pose == Pose.CROUCHING || this.lastPose == Pose.CROUCHING) {
            this.targetBounceY += bounceIntensity;
         } else if (pose == Pose.SLEEPING || this.lastPose == Pose.SLEEPING) {
            this.targetBounceY = bounceIntensity;
         }

         this.lastPose = pose;
      }
   }

   private void tickVehicle(LivingEntity entity, RandomSource random, float bounceIntensity, float breastWeight) {
      switch (entity.getVehicle()) {
         case Boat boat:
            int leftTime = (int)boat.getRowingTime(0, entity.walkAnimation.position());
            int rightTime = (int)boat.getRowingTime(1, entity.walkAnimation.position());
            float rotationL = Mth.clampedLerp((float) (-Math.PI / 3), (float) (-Math.PI / 12), (Mth.sin(-rightTime) + 1.0F) / 2.0F);
            float rotationR = Mth.clampedLerp((float) (-Math.PI / 4), (float) (Math.PI / 4), (Mth.sin(-leftTime + 1.0F) + 1.0F) / 2.0F);
            if (rotationL < -1.0F || rotationR < -0.6F) {
               this.targetBounceY = bounceIntensity / 3.25F;
            }
            break;
         case Minecart cart:
            float speed = (float)cart.getDeltaMovement().lengthSqr();
            if (speed > 0.2F && random.nextDouble() * speed < 0.5) {
               this.targetBounceY = (random.nextBoolean() ? -bounceIntensity : bounceIntensity) / 6.0F;
               this.targetBounceY += breastWeight;
            }
            break;
         case AbstractHorse horse:
            float movementx = (float)horse.getDeltaMovement().lengthSqr();
            if (horse.getAge() % this.clampMovement(movementx) == 5 && movementx > 0.05F) {
               this.targetBounceY = bounceIntensity / 4.0F;
               this.targetBounceY += breastWeight;
            }
            break;
         case Pig pig:
            float movement = (float)pig.getDeltaMovement().lengthSqr();
            if (pig.getAge() % this.clampMovement(movement) == 5 && movement > 0.002F) {
               this.targetBounceY = bounceIntensity * Math.clamp(movement * 75.0F, 0.1F, 1.0F) / 4.0F;
               this.targetBounceY += breastWeight;
            }
            break;
         case Strider strider:
            float animSpeed = Math.min(0.25F, strider.walkAnimation.speed());
            float animPos = strider.walkAnimation.position();
            float offset = 0.12F * Mth.cos(animPos * 1.5F) * 2.0F * animSpeed;
            double heightOffset = strider.getBbHeight() - 0.19 + offset;
            this.targetBounceY += ((float)(heightOffset * 3.0) - 4.5F) * bounceIntensity;
            break;
         case null:
         default:
      }
   }

   private void tickArmSwing(LivingEntity entity, RandomSource random, float bounceIntensity) {
      int swingDuration = entity.getCurrentSwingDuration();
      if ((swingDuration > 1 || this.lastSwingDuration > 1) && entity.getPose() != Pose.SLEEPING) {
         float rawAmplifier = 0.0F;
         if (swingDuration < 6) {
            rawAmplifier = 0.15F * (6 - swingDuration);
         } else if (swingDuration > 6) {
            rawAmplifier = -0.055F * (swingDuration - 6);
         }

         float amplifier = Math.clamp(1.0F + rawAmplifier, 0.6F, 1.3F);
         HumanoidArm swingingArm = entity.swingingArm == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
         int swingTickDelta = entity.swingTime - this.lastSwingTick;
         float swingProgress = distanceFromMedian(0, this.lastSwingDuration, Math.clamp(this.lastSwingTick, 0, this.lastSwingDuration));
         HumanoidArm swingingToward = swingProgress > -0.2F ? swingingArm.getOpposite() : swingingArm;
         int everyNthTick = Math.clamp(swingDuration - 1, 1, 5);
         if (entity.swinging && entity.tickCount % everyNthTick == 0) {
            this.targetBounceY = this.targetBounceY + (random.nextBoolean() ? -0.25F : 0.25F) * amplifier * bounceIntensity;
            float xAmp = Math.clamp(1.0F + rawAmplifier * (rawAmplifier < 0.0F ? 1.625F : 0.8F), 0.25F, 1.225F);
            this.targetBounceX = 0.325F * xAmp * bounceIntensity * (swingingArm == HumanoidArm.RIGHT ? -1.0F : 1.0F);
         }

         if (swingTickDelta < 0 && this.lastSwingTick != this.lastSwingDuration - 1) {
            this.targetRotVel = this.targetRotVel + (swingingArm == HumanoidArm.RIGHT ? -4.0F : 4.0F) * Math.abs(swingProgress) * bounceIntensity;
         } else if (entity.swinging && swingDuration > 1) {
            this.targetRotVel = this.targetRotVel + (swingingToward == HumanoidArm.RIGHT ? -0.2F : 0.2F) * amplifier * bounceIntensity;
         }

         this.lastSwingTick = entity.swingTime;
      }

      if (!entity.swinging) {
         this.lastSwingTick = 0;
      }

      this.lastSwingDuration = Math.max(swingDuration, 1);
   }

   private void finishTick() {
      float percent = this.entityConfig.breasts().physics().floppiness().get();
      float bounceAmount = 0.45F * (1.0F - percent) + 0.15F;
      bounceAmount = Math.clamp(bounceAmount, 0.15F, 0.6F);
      float delta = 2.25F - bounceAmount;
      float distanceFromMin = Math.abs(this.bounceVel + 1.5F) * 0.5F;
      float distanceFromMax = Math.abs(this.bounceVel - 2.65F) * 0.5F;
      if (this.bounceVel < -0.5F) {
         this.targetBounceY += distanceFromMin;
      }

      if (this.bounceVel > 2.5F) {
         this.targetBounceY -= distanceFromMax;
      }

      this.targetBounceY = Math.clamp(this.targetBounceY, -1.5F, 2.5F);
      this.targetRotVel = Math.clamp(this.targetRotVel, -25.0F, 25.0F);
      this.velocity = Mth.lerp(bounceAmount, this.velocity, (this.targetBounceY - this.bounceVel) * delta);
      this.bounceVel = this.bounceVel + this.velocity * percent * 1.1625F;
      this.velocityX = Mth.lerp(bounceAmount, this.velocityX, (this.targetBounceX - this.bounceVelX) * delta);
      this.bounceVelX = this.bounceVelX + this.velocityX * percent;
      this.rotVelocity = Mth.lerp(bounceAmount, this.rotVelocity, (this.targetRotVel - this.bounceRotVel) * delta);
      this.bounceRotVel = this.bounceRotVel + this.rotVelocity * percent;
      this.bounceRotation = this.bounceRotVel;
      this.positionX = this.bounceVelX;
      this.positionY = this.bounceVel;
      if (this.positionY < -0.5F) {
         this.positionY = -0.5F;
      }

      if (this.positionY > 1.5F) {
         this.positionY = 1.5F;
         this.velocity = 0.0F;
      }
   }

   public float getPrePositionY() {
      return this.prePositionY;
   }

   public float getPositionY() {
      return this.positionY;
   }

   public float getPrePositionX() {
      return this.prePositionX;
   }

   public float getPositionX() {
      return this.positionX;
   }

   public float getBounceRotation() {
      return this.bounceRotation;
   }

   public float getPreBounceRotation() {
      return this.preBounceRotation;
   }

   public float getBreastSize() {
      return this.breastSize;
   }

   public float getPreBreastSize() {
      return this.preBreastSize;
   }

   private int clampMovement(float movement) {
      return Math.max((int)(10.0F - 2.0F * movement), 1);
   }

   private static float distanceFromMedian(int p1, int p2, float point) {
      if (p1 >= p2) {
         throw new IllegalArgumentException("p2 must be greater than p1");
      }

      if (point < p1 || point > p2) {
         throw new IllegalArgumentException(point + " is not within bounds of (" + p1 + ", " + p2 + ")");
      }

      if (point != p1 && point != p2) {
         float median = (p2 - p1) / 2.0F;
         point -= p1;
         if (point > median) {
            point = -(median - (point - median));
         }

         return point / median;
      } else {
         return 0.0F;
      }
   }
}
