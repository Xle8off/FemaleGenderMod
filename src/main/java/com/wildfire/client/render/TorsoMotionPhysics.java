package com.wildfire.client.render;

import java.util.*;
import net.minecraft.client.model.geom.ModelPart;

/** Bounded secondary rotation from the final animated pose; never displaces the attachment. */
public final class TorsoMotionPhysics {
    public record Motion(float pitch, float roll) {}
    private static final Map<UUID, State> states = new LinkedHashMap<>();
    private TorsoMotionPhysics() {}
    public static synchronized Motion sample(UUID id, ModelPart root, ModelPart body, float time) {
        if (!states.containsKey(id) && states.size() >= 512) states.remove(states.keySet().iterator().next());
        return states.computeIfAbsent(id, key -> new State()).sample(
            root.xRot + body.xRot, root.zRot + body.zRot, root.x + body.x, root.y + body.y, time);
    }
    public static synchronized void clear() { states.clear(); IndependentBreastMotion.clear(); }
    static final class State {
        boolean initialized;
        float lastTime,lastPitch,lastRoll,lastX,lastY,pitch,roll,pitchVelocity,rollVelocity;
        Motion sample(float p,float r,float x,float y,float time) {
            if (initialized && Math.abs(time-lastTime)<.0001f) return new Motion(pitch,roll);
            if (!initialized || time<lastTime || time-lastTime>2) {
                initialized=true;lastTime=time;lastPitch=p;lastRoll=r;lastX=x;lastY=y;
                pitch=roll=pitchVelocity=rollVelocity=0;return new Motion(0,0);
            }
            float dt=Math.clamp(time-lastTime,.05f,1f);
            pitchVelocity+=Math.clamp(-wrap(p-lastPitch)*5-(y-lastY)*.15f,-1.5f,1.5f)-pitch*.22f*dt;
            rollVelocity+=Math.clamp(-wrap(r-lastRoll)*5-(x-lastX)*.15f,-1.5f,1.5f)-roll*.22f*dt;
            float damping=(float)Math.pow(.68,dt);
            pitchVelocity*=damping;rollVelocity*=damping;
            pitch=Math.clamp(pitch+pitchVelocity*dt,-3f,3f);
            roll=Math.clamp(roll+rollVelocity*dt,-3f,3f);
            lastTime=time;lastPitch=p;lastRoll=r;lastX=x;lastY=y;
            return new Motion(pitch,roll);
        }
        private static float wrap(float angle) {return (float)Math.atan2(Math.sin(angle),Math.cos(angle));}
    }
}
