package com.wildfire.client.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class IndependentBreastMotion {
   private static final IndependentBreastMotion.Pair STILL = new IndependentBreastMotion.Pair(
      new IndependentBreastMotion.Motion(0.0F, 0.0F), new IndependentBreastMotion.Motion(0.0F, 0.0F)
   );
   private static final Map<UUID, IndependentBreastMotion.State> STATES = new LinkedHashMap<>();

   private IndependentBreastMotion() {
   }

   public static synchronized IndependentBreastMotion.Pair sample(
      UUID id, float time, float yaw, float pitch, float roll, float leftX, float leftY, float rightX, float rightY
   ) {
      return sample(id, time, yaw, pitch, roll, leftX, leftY, rightX, rightY, 0.0F, 0.0F);
   }

   public static synchronized IndependentBreastMotion.Pair sample(
      UUID id, float time, float yaw, float pitch, float roll, float leftX, float leftY, float rightX, float rightY, float torsoX, float torsoY
   ) {
      if (Float.isFinite(time)
         && Float.isFinite(yaw)
         && Float.isFinite(pitch)
         && Float.isFinite(roll)
         && Float.isFinite(leftX)
         && Float.isFinite(leftY)
         && Float.isFinite(rightX)
         && Float.isFinite(rightY)
         && Float.isFinite(torsoX)
         && Float.isFinite(torsoY)) {
         if (!STATES.containsKey(id) && STATES.size() >= 512) {
            STATES.remove(STATES.keySet().iterator().next());
         }

         return STATES.computeIfAbsent(id, IndependentBreastMotion.State::new).sample(time, yaw, pitch, roll, leftX, leftY, rightX, rightY, torsoX, torsoY);
      } else {
         return STILL;
      }
   }

   public static synchronized void clear() {
      STATES.clear();
   }

   public record Motion(float x, float y) {
   }

   public record Pair(IndependentBreastMotion.Motion left, IndependentBreastMotion.Motion right) {
   }

   private static final class Spring {
      final float stiffness;
      final float damping;
      final float response;
      float x;
      float y;
      float vx;
      float vy;

      Spring(float stiffness, float damping, float response) {
         this.stiffness = stiffness;
         this.damping = damping;
         this.response = response;
      }

      void reset() {
         this.x = this.y = this.vx = this.vy = 0.0F;
      }

      void impulse(float turn, float tilt, float lean, float side, float dx, float dy) {
         this.vx = this.vx + Math.clamp(-turn * this.response * (1.0F + side * 0.12F) - lean * 0.3F - dx * this.response * 0.18F, -0.45F, 0.45F);
         this.vy = this.vy + Math.clamp(-tilt * this.response * 0.7F + turn * side * 0.12F - dy * this.response * 0.22F, -0.4F, 0.4F);
      }

      void advance(float dt, float targetX, float targetY) {
         int steps = Math.max(1, (int)Math.ceil(dt / 0.25F));
         float h = dt / steps;
         float decay = (float)Math.pow(this.damping, h);

         for (int i = 0; i < steps; i++) {
            this.vx = (this.vx + (Math.clamp(targetX * 0.65F, -0.75F, 0.75F) - this.x) * this.stiffness * h) * decay;
            this.vy = (this.vy + (Math.clamp(targetY * 0.65F, -0.85F, 0.85F) - this.y) * this.stiffness * h) * decay;
            this.x = this.x + this.vx * h;
            this.y = this.y + this.vy * h;
            if (Math.abs(this.x) > 1.0F) {
               this.x = Math.clamp(this.x, -1.0F, 1.0F);
               this.vx = 0.0F;
            }

            if (Math.abs(this.y) > 1.1F) {
               this.y = Math.clamp(this.y, -1.1F, 1.1F);
               this.vy = 0.0F;
            }
         }
      }

      IndependentBreastMotion.Motion motion() {
         return new IndependentBreastMotion.Motion(this.x, this.y);
      }
   }

   private static final class State {
      final IndependentBreastMotion.Spring left;
      final IndependentBreastMotion.Spring right;
      boolean initialized;
      float lastTime;
      float lastYaw;
      float lastPitch;
      float lastRoll;
      float lastX;
      float lastY;
      IndependentBreastMotion.Pair cached = IndependentBreastMotion.STILL;

      State(UUID id) {
         boolean swap = (id.getLeastSignificantBits() & 1L) != 0L;
         this.left = new IndependentBreastMotion.Spring(swap ? 0.19F : 0.24F, swap ? 0.82F : 0.76F, swap ? 0.87F : 1.05F);
         this.right = new IndependentBreastMotion.Spring(swap ? 0.24F : 0.19F, swap ? 0.76F : 0.82F, swap ? 1.05F : 0.87F);
      }

      IndependentBreastMotion.Pair sample(float time, float yaw, float pitch, float roll, float lx, float ly, float rx, float ry, float x, float y) {
         if (this.initialized && Math.abs(time - this.lastTime) < 1.0E-4F) {
            return this.cached;
         }

         if (this.initialized && !(time < this.lastTime) && !(time - this.lastTime > 2.0F)) {
            float dt = Math.min(time - this.lastTime, 1.0F);
            float turn = wrap(yaw - this.lastYaw);
            float tilt = wrap(pitch - this.lastPitch);
            float lean = wrap(roll - this.lastRoll);
            float dx = x - this.lastX;
            float dy = y - this.lastY;
            if (!(Math.abs(turn) > 1.6F) && !(Math.abs(tilt) > 1.6F) && !(Math.abs(lean) > 1.6F) && !(Math.abs(dx) > 12.0F) && !(Math.abs(dy) > 12.0F)) {
               this.left.impulse(turn, tilt, lean, -1.0F, dx, dy);
               this.right.impulse(turn, tilt, lean, 1.0F, dx, dy);
               this.left.advance(dt, lx, ly);
               this.right.advance(dt, rx, ry);
            } else {
               this.left.reset();
               this.right.reset();
            }

            this.lastTime = time;
            this.lastYaw = yaw;
            this.lastPitch = pitch;
            this.lastRoll = roll;
            this.lastX = x;
            this.lastY = y;
            return this.cached = new IndependentBreastMotion.Pair(this.left.motion(), this.right.motion());
         } else {
            this.initialized = true;
            this.left.reset();
            this.right.reset();
            this.lastTime = time;
            this.lastYaw = yaw;
            this.lastPitch = pitch;
            this.lastRoll = roll;
            this.lastX = x;
            this.lastY = y;
            return this.cached = IndependentBreastMotion.STILL;
         }
      }

      private static float wrap(float a) {
         return (float)Math.atan2(Math.sin(a), Math.cos(a));
      }
   }
}
