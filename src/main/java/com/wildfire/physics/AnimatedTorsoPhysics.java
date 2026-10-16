package com.wildfire.physics;

import com.wildfire.main.LoaderAgnostics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Secondary motion driven by the final animated torso bone, including Emotecraft animations. */
public final class AnimatedTorsoPhysics {
    private static final boolean EMOTECRAFT_LOADED = LoaderAgnostics.isModLoaded("emotecraft");
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final Motion NONE = new Motion(0f, 0f, 0f, 0f);

    private AnimatedTorsoPhysics() {}

    public static Motion sample(UUID playerId, ModelPart body, float frameTime) {
        if (!EMOTECRAFT_LOADED) return NONE;
        return sample(playerId, frameTime, body.x, body.y, body.xRot, body.yRot, body.zRot);
    }

    public static Motion sample(UUID playerId, float frameTime, float x, float y, float pitch, float yaw, float roll) {
        if (!EMOTECRAFT_LOADED) return NONE;
        return STATES.computeIfAbsent(playerId, _ -> new State()).sample(x, y, pitch, yaw, roll, frameTime);
    }

    public static void clear() {
        STATES.clear();
    }

    public record Motion(float x, float y, float pitchDegrees, float rollDegrees) {}

    private static final class State {
        private boolean initialized;
        private float lastFrameTime;
        private float lastX, lastY, lastPitch, lastYaw, lastRoll;
        private float velocityX, velocityY, velocityPitch, velocityYaw, velocityRoll;
        private float springX, springY, springPitch, springRoll;
        private float springVelocityX, springVelocityY, springVelocityPitch, springVelocityRoll;
        private Motion current = NONE;

        private synchronized Motion sample(ModelPart body, float frameTime) {
            return sample(body.x, body.y, body.xRot, body.yRot, body.zRot, frameTime);
        }

        private synchronized Motion sample(float x, float y, float pitch, float yaw, float roll, float frameTime) {
            if (initialized && Math.abs(frameTime - lastFrameTime) < 0.0001f) return current;
            if (!initialized || frameTime < lastFrameTime || frameTime - lastFrameTime > 2f) {
                initialized = true;
                lastFrameTime = frameTime;
                lastX = x;
                lastY = y;
                lastPitch = pitch;
                lastYaw = yaw;
                lastRoll = roll;
                current = NONE;
                return current;
            }

            float dt = Math.clamp(frameTime - lastFrameTime, 0.05f, 1f);
            float nextVelocityX = (x - lastX) / dt;
            float nextVelocityY = (y - lastY) / dt;
            float nextVelocityPitch = angleDelta(lastPitch, pitch) / dt;
            float nextVelocityYaw = angleDelta(lastYaw, yaw) / dt;
            float nextVelocityRoll = angleDelta(lastRoll, roll) / dt;

            float impulseX = Math.clamp(nextVelocityX - velocityX, -2f, 2f);
            float impulseY = Math.clamp(nextVelocityY - velocityY, -2f, 2f);
            float impulsePitch = Math.clamp(nextVelocityPitch - velocityPitch, -0.45f, 0.45f);
            float impulseYaw = Math.clamp(nextVelocityYaw - velocityYaw, -0.45f, 0.45f);
            float impulseRoll = Math.clamp(nextVelocityRoll - velocityRoll, -0.45f, 0.45f);

            springVelocityX += -impulseX * 0.32f - springX * 0.24f;
            springVelocityY += -impulseY * 0.38f - springY * 0.27f;
            springVelocityPitch += -impulsePitch * 18f - springPitch * 0.2f;
            springVelocityRoll += -(impulseRoll + impulseYaw * 0.35f) * 14f - springRoll * 0.22f;

            float damping = (float) Math.pow(0.72f, dt);
            springVelocityX *= damping;
            springVelocityY *= damping;
            springVelocityPitch *= damping;
            springVelocityRoll *= damping;
            springX = Math.clamp(springX + springVelocityX * dt, -1.25f, 1.25f);
            springY = Math.clamp(springY + springVelocityY * dt, -1.5f, 1.5f);
            springPitch = Math.clamp(springPitch + springVelocityPitch * dt, -12f, 12f);
            springRoll = Math.clamp(springRoll + springVelocityRoll * dt, -10f, 10f);

            lastFrameTime = frameTime;
            lastX = x;
            lastY = y;
            lastPitch = pitch;
            lastYaw = yaw;
            lastRoll = roll;
            velocityX = nextVelocityX;
            velocityY = nextVelocityY;
            velocityPitch = nextVelocityPitch;
            velocityYaw = nextVelocityYaw;
            velocityRoll = nextVelocityRoll;
            current = new Motion(springX, springY, springPitch, springRoll);
            return current;
        }

        private static float angleDelta(float previous, float current) {
            return Mth.wrapDegrees((current - previous) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        }
    }
}
