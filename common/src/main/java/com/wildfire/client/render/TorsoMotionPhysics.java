package com.wildfire.client.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.geom.ModelPart;

public final class TorsoMotionPhysics {
   private static final Map<UUID, TorsoMotionPhysics.State> states = new LinkedHashMap<>();

   private TorsoMotionPhysics() {
   }

   public static synchronized TorsoMotionPhysics.Motion sample(UUID id, ModelPart root, ModelPart body, float time) {
      if (!states.containsKey(id) && states.size() >= 512) {
         states.remove(states.keySet().iterator().next());
      }

      return states.computeIfAbsent(id, key -> new TorsoMotionPhysics.State())
         .sample(root.xRot + body.xRot, root.zRot + body.zRot, root.x + body.x, root.y + body.y, time);
   }

   public static synchronized void clear() {
      states.clear();
      IndependentBreastMotion.clear();
   }

   public record Motion(float pitch, float roll) {
   }

   static final class State {
      boolean initialized;
      float lastTime;
      float lastPitch;
      float lastRoll;
      float lastX;
      float lastY;
      float pitch;
      float roll;
      float pitchVelocity;
      float rollVelocity;

      TorsoMotionPhysics.Motion sample(float p, float r, float x, float y, float time) {
         if (this.initialized && Math.abs(time - this.lastTime) < 1.0E-4F) {
            return new TorsoMotionPhysics.Motion(this.pitch, this.roll);
         } else if (this.initialized && !(time < this.lastTime) && !(time - this.lastTime > 2.0F)) {
            float dt = Math.clamp(time - this.lastTime, 0.05F, 1.0F);
            this.pitchVelocity = this.pitchVelocity
               + (Math.clamp(-wrap(p - this.lastPitch) * 5.0F - (y - this.lastY) * 0.15F, -1.5F, 1.5F) - this.pitch * 0.22F * dt);
            this.rollVelocity = this.rollVelocity
               + (Math.clamp(-wrap(r - this.lastRoll) * 5.0F - (x - this.lastX) * 0.15F, -1.5F, 1.5F) - this.roll * 0.22F * dt);
            float damping = (float)Math.pow(0.68, dt);
            this.pitchVelocity *= damping;
            this.rollVelocity *= damping;
            this.pitch = Math.clamp(this.pitch + this.pitchVelocity * dt, -3.0F, 3.0F);
            this.roll = Math.clamp(this.roll + this.rollVelocity * dt, -3.0F, 3.0F);
            this.lastTime = time;
            this.lastPitch = p;
            this.lastRoll = r;
            this.lastX = x;
            this.lastY = y;
            return new TorsoMotionPhysics.Motion(this.pitch, this.roll);
         } else {
            this.initialized = true;
            this.lastTime = time;
            this.lastPitch = p;
            this.lastRoll = r;
            this.lastX = x;
            this.lastY = y;
            this.pitch = this.roll = this.pitchVelocity = this.rollVelocity = 0.0F;
            return new TorsoMotionPhysics.Motion(0.0F, 0.0F);
         }
      }

      private static float wrap(float angle) {
         return (float)Math.atan2(Math.sin(angle), Math.cos(angle));
      }
   }
}
