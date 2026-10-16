package com.wildfire.client.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Two deterministic, independently damped springs. All distances are model pixels. */
public final class IndependentBreastMotion {
    public record Motion(float x, float y) {}
    public record Pair(Motion left, Motion right) {}
    private static final Pair STILL = new Pair(new Motion(0, 0), new Motion(0, 0));
    private static final Map<UUID, State> STATES = new LinkedHashMap<>();
    private IndependentBreastMotion() {}

    public static synchronized Pair sample(UUID id, float time, float yaw, float pitch, float roll,
                                           float leftX, float leftY, float rightX, float rightY) {
        return sample(id,time,yaw,pitch,roll,leftX,leftY,rightX,rightY,0f,0f);
    }

    public static synchronized Pair sample(UUID id, float time, float yaw, float pitch, float roll,
                                           float leftX, float leftY, float rightX, float rightY,
                                           float torsoX, float torsoY) {
        if (!Float.isFinite(time) || !Float.isFinite(yaw) || !Float.isFinite(pitch)
            || !Float.isFinite(roll) || !Float.isFinite(leftX) || !Float.isFinite(leftY)
            || !Float.isFinite(rightX) || !Float.isFinite(rightY)
            || !Float.isFinite(torsoX) || !Float.isFinite(torsoY)) return STILL;
        if (!STATES.containsKey(id) && STATES.size() >= 512)
            STATES.remove(STATES.keySet().iterator().next());
        return STATES.computeIfAbsent(id, State::new).sample(time,yaw,pitch,roll,leftX,leftY,rightX,rightY,torsoX,torsoY);
    }

    public static synchronized void clear() { STATES.clear(); }

    private static final class Spring {
        final float stiffness, damping, response;
        float x,y,vx,vy;
        Spring(float stiffness,float damping,float response) {
            this.stiffness=stiffness;this.damping=damping;this.response=response;
        }
        void reset() { x=y=vx=vy=0; }
        void impulse(float turn,float tilt,float lean,float side,float dx,float dy) {
            vx += Math.clamp(-turn * response * (1f + side * .12f) - lean * .3f
                - dx * response * .18f, -.45f, .45f);
            vy += Math.clamp(-tilt * response * .7f + turn * side * .12f
                - dy * response * .22f, -.4f, .4f);
        }
        void advance(float dt,float targetX,float targetY) {
            int steps=Math.max(1,(int)Math.ceil(dt/.25f));
            float h=dt/steps, decay=(float)Math.pow(damping,h);
            for(int i=0;i<steps;i++) {
                vx=(vx+(Math.clamp(targetX*.65f,-.75f,.75f)-x)*stiffness*h)*decay;
                vy=(vy+(Math.clamp(targetY*.65f,-.85f,.85f)-y)*stiffness*h)*decay;
                x+=vx*h;y+=vy*h;
                if(Math.abs(x)>1f) { x=Math.clamp(x,-1f,1f);vx=0; }
                if(Math.abs(y)>1.1f) { y=Math.clamp(y,-1.1f,1.1f);vy=0; }
            }
        }
        Motion motion() { return new Motion(x,y); }
    }

    private static final class State {
        final Spring left,right;
        boolean initialized;
        float lastTime,lastYaw,lastPitch,lastRoll,lastX,lastY;
        Pair cached=STILL;
        State(UUID id) {
            boolean swap=(id.getLeastSignificantBits() & 1L)!=0;
            left=new Spring(swap?.19f:.24f,swap?.82f:.76f,swap?.87f:1.05f);
            right=new Spring(swap?.24f:.19f,swap?.76f:.82f,swap?1.05f:.87f);
        }
        Pair sample(float time,float yaw,float pitch,float roll,float lx,float ly,float rx,float ry,float x,float y) {
            if(initialized && Math.abs(time-lastTime)<.0001f) return cached;
            if(!initialized || time<lastTime || time-lastTime>2f) {
                initialized=true;left.reset();right.reset();
                lastTime=time;lastYaw=yaw;lastPitch=pitch;lastRoll=roll;
                lastX=x;lastY=y;
                return cached=STILL;
            }
            float dt=Math.min(time-lastTime,1f);
            float turn=wrap(yaw-lastYaw),tilt=wrap(pitch-lastPitch),lean=wrap(roll-lastRoll);
            float dx=x-lastX,dy=y-lastY;
            // Teleports / pose discontinuities must not launch the springs.
            if(Math.abs(turn)>1.6f || Math.abs(tilt)>1.6f || Math.abs(lean)>1.6f
                || Math.abs(dx)>12f || Math.abs(dy)>12f) {
                left.reset();right.reset();
            } else {
                left.impulse(turn,tilt,lean,-1f,dx,dy);right.impulse(turn,tilt,lean,1f,dx,dy);
                left.advance(dt,lx,ly);right.advance(dt,rx,ry);
            }
            lastTime=time;lastYaw=yaw;lastPitch=pitch;lastRoll=roll;
            lastX=x;lastY=y;
            return cached=new Pair(left.motion(),right.motion());
        }
        private static float wrap(float a) { return (float)Math.atan2(Math.sin(a),Math.cos(a)); }
    }
}
