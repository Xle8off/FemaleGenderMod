package com.wildfire.client.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.joml.Vector3f;

/** Optional PAL integration: observe animation only; never apply its transforms a second time. */
public final class EmoteMotionBridge {
    public record Pose(float yaw,float pitch,float roll,float x,float y) {}
    private EmoteMotionBridge() {}
    private static final class OptionalAccess {
        static final Method GET = resolve();
        private static Method resolve() {
            try {
                return Class.forName("com.zigythebird.playeranim.accessors.IAvatarAnimationState")
                    .getMethod("playerAnimLib$getAnimManager");
            } catch (ReflectiveOperationException | LinkageError absent) { return null; }
        }
    }
    private record ManagerAccess(Method refresh,Method active,Method bone) {}
    private record BoneAccess(Field position,Field rotation,Field bend) {}
    private static final ClassValue<ManagerAccess> MANAGERS = new ClassValue<>() {
        @Override protected ManagerAccess computeValue(Class<?> type) {
            try {
                return new ManagerAccess(type.getMethod("handleAnimations",float.class,boolean.class,boolean.class),
                    type.getMethod("isActive"),type.getMethod("get3DTransform",String.class));
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    };
    private static final ClassValue<BoneAccess> BONES = new ClassValue<>() {
        @Override protected BoneAccess computeValue(Class<?> type) {
            try {
                return new BoneAccess(type.getField("position"),type.getField("rotation"),type.getField("bend"));
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    };
    private static boolean reported;
    public static boolean blocksGesture(Object renderState) {
        Method getter=OptionalAccess.GET;
        if(getter==null||!getter.getDeclaringClass().isInstance(renderState))return false;
        try {
            Object manager=getter.invoke(renderState);
            return manager!=null && Boolean.TRUE.equals(MANAGERS.get(manager.getClass()).active.invoke(manager));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError failure) {
            report(failure);return true;
        }
    }
    public static Pose read(Object renderState,float partialTick) {
        Method getter=OptionalAccess.GET;
        if(getter==null || !getter.getDeclaringClass().isInstance(renderState)) return null;
        try {
            return readManager(getter.invoke(renderState),partialTick);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            report(failure);return null;
        }
    }

    /** Separate entry point permits contract tests without booting Minecraft or installing PAL. */
    public static Pose readManager(Object manager,float partialTick) {
        if(manager==null) return null;
        try {
            var api=MANAGERS.get(manager.getClass());
            // PAL deduplicates setup at tick+partialTick. false prevents advancing the game tick.
            api.refresh.invoke(manager,partialTick,false,false);
            if(!Boolean.TRUE.equals(api.active.invoke(manager))) return null;
            Object body=api.bone.invoke(manager,"body"),torso=api.bone.invoke(manager,"torso");
            var b=BONES.get(body.getClass());var t=BONES.get(torso.getClass());
            var bp=(Vector3f)b.position.get(body);var tp=(Vector3f)t.position.get(torso);
            var br=(Vector3f)b.rotation.get(body);var tr=(Vector3f)t.rotation.get(torso);
            // PAL's Y points up; model Y points down. Bend is an extra rotation absent from ModelPart.
            Pose pose=new Pose(br.y+tr.y,br.x+tr.x+b.bend.getFloat(body)+t.bend.getFloat(torso),
                br.z+tr.z,bp.x+tp.x,-(bp.y+tp.y));
            return Float.isFinite(pose.yaw)&&Float.isFinite(pose.pitch)&&Float.isFinite(pose.roll)
                &&Float.isFinite(pose.x)&&Float.isFinite(pose.y) ? pose : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            report(failure);return null;
        }
    }
    private static void report(Throwable failure) {
        if(!reported) {
            reported=true;
            System.getLogger("SpringPlusCosmetics").log(System.Logger.Level.DEBUG,
                "Optional PAL motion bridge unavailable; using vanilla pose physics",failure);
        }
    }
}
