package com.wildfire.client.render;

import java.lang.System.Logger.Level;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.joml.Vector3f;

public final class EmoteMotionBridge {
   private static final ClassValue<EmoteMotionBridge.ManagerAccess> MANAGERS = new ClassValue<EmoteMotionBridge.ManagerAccess>() {
      protected EmoteMotionBridge.ManagerAccess computeValue(Class<?> type) {
         try {
            return new EmoteMotionBridge.ManagerAccess(
               type.getMethod("handleAnimations", float.class, boolean.class, boolean.class),
               type.getMethod("isActive"),
               type.getMethod("get3DTransform", String.class)
            );
         } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
         }
      }
   };
   private static final ClassValue<EmoteMotionBridge.BoneAccess> BONES = new ClassValue<EmoteMotionBridge.BoneAccess>() {
      protected EmoteMotionBridge.BoneAccess computeValue(Class<?> type) {
         try {
            return new EmoteMotionBridge.BoneAccess(type.getField("position"), type.getField("rotation"), type.getField("bend"));
         } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
         }
      }
   };
   private static boolean reported;

   private EmoteMotionBridge() {
   }

   public static boolean blocksGesture(Object renderState) {
      Method getter = EmoteMotionBridge.OptionalAccess.GET;
      if (getter != null && getter.getDeclaringClass().isInstance(renderState)) {
         try {
            Object manager = getter.invoke(renderState);
            return manager != null && Boolean.TRUE.equals(MANAGERS.get(manager.getClass()).active.invoke(manager));
         } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            report(failure);
            return true;
         }
      } else {
         return false;
      }
   }

   public static EmoteMotionBridge.Pose read(Object renderState, float partialTick) {
      Method getter = EmoteMotionBridge.OptionalAccess.GET;
      if (getter != null && getter.getDeclaringClass().isInstance(renderState)) {
         try {
            return readManager(getter.invoke(renderState), partialTick);
         } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            report(failure);
            return null;
         }
      } else {
         return null;
      }
   }

   public static EmoteMotionBridge.Pose readManager(Object manager, float partialTick) {
      if (manager == null) {
         return null;
      }

      try {
         EmoteMotionBridge.ManagerAccess api = MANAGERS.get(manager.getClass());
         api.refresh.invoke(manager, partialTick, false, false);
         if (!Boolean.TRUE.equals(api.active.invoke(manager))) {
            return null;
         }

         Object body = api.bone.invoke(manager, "body");
         Object torso = api.bone.invoke(manager, "torso");
         EmoteMotionBridge.BoneAccess b = BONES.get(body.getClass());
         EmoteMotionBridge.BoneAccess t = BONES.get(torso.getClass());
         Vector3f bp = (Vector3f)b.position.get(body);
         Vector3f tp = (Vector3f)t.position.get(torso);
         Vector3f br = (Vector3f)b.rotation.get(body);
         Vector3f tr = (Vector3f)t.rotation.get(torso);
         EmoteMotionBridge.Pose pose = new EmoteMotionBridge.Pose(
            br.y + tr.y, br.x + tr.x + b.bend.getFloat(body) + t.bend.getFloat(torso), br.z + tr.z, bp.x + tp.x, -(bp.y + tp.y)
         );
         return Float.isFinite(pose.yaw) && Float.isFinite(pose.pitch) && Float.isFinite(pose.roll) && Float.isFinite(pose.x) && Float.isFinite(pose.y)
            ? pose
            : null;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
         report(failure);
         return null;
      }
   }

   private static void report(Throwable failure) {
      if (!reported) {
         reported = true;
         System.getLogger("SpringPlusCosmetics").log(Level.DEBUG, "Optional PAL motion bridge unavailable; using vanilla pose physics", failure);
      }
   }

   private record BoneAccess(Field position, Field rotation, Field bend) {
   }

   private record ManagerAccess(Method refresh, Method active, Method bone) {
   }

   private static final class OptionalAccess {
      static final Method GET = resolve();

      private static Method resolve() {
         try {
            return Class.forName("com.zigythebird.playeranim.accessors.IAvatarAnimationState").getMethod("playerAnimLib$getAnimManager");
         } catch (ReflectiveOperationException | LinkageError absent) {
            return null;
         }
      }
   }

   public record Pose(float yaw, float pitch, float roll, float x, float y) {
   }
}
