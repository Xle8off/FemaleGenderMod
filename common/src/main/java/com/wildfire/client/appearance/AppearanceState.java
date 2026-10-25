package com.wildfire.client.appearance;

import com.wildfire.api.Gender;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.minecraft.resources.Identifier;

public final class AppearanceState {
   private static final Map<UUID, BreastShape> SHAPES = new ConcurrentHashMap<>();
   private static final Map<UUID, AppearanceState.Cosmetics> COSMETICS = new ConcurrentHashMap<>();
   private static volatile boolean active;
   private static volatile BiFunction<UUID, Integer, Identifier> garmentTextures = (uuid, slot) -> null;

   private AppearanceState() {
   }

   public static boolean isActive() {
      return active;
   }

   public static void activate() {
      active = true;
   }

   public static void deactivate() {
      active = false;
      SHAPES.clear();
      COSMETICS.clear();
   }

   public static void resetProfiles() {
      SHAPES.clear();
      COSMETICS.clear();
   }

   public static BreastShape shape(UUID uuid) {
      return SHAPES.getOrDefault(uuid, BreastShape.ROUND);
   }

   public static void preview(UUID uuid, BreastShape shape) {
      SHAPES.put(uuid, shape);
   }

   public static AppearanceState.Cosmetics cosmetics(UUID uuid) {
      return COSMETICS.getOrDefault(uuid, AppearanceState.Cosmetics.NONE);
   }

   public static void preview(UUID uuid, AppearanceState.Cosmetics cosmetics) {
      COSMETICS.put(uuid, cosmetics);
   }

   public static void setGarmentTextures(BiFunction<UUID, Integer, Identifier> provider) {
      garmentTextures = provider;
   }

   public static Identifier garmentTexture(UUID uuid, int slot) {
      return garmentTextures.apply(uuid, slot);
   }

   public static void apply(UUID uuid, boolean visible, boolean female, float size, int shape, int shirt, int pants, int headwear, int pet) {
      PlayerConfigHolder holder = WildfireGender.getOrAddPlayerById(uuid);
      holder.gender().update(visible && female ? Gender.FEMALE : Gender.MALE);
      holder.breasts().bustSize().update(visible && female ? Math.clamp(size, 0.0F, 0.8F) : 0.0F);
      if (visible) {
         SHAPES.put(uuid, BreastShape.fromId(shape));
         COSMETICS.put(uuid, new AppearanceState.Cosmetics(shirt, pants, headwear, pet));
      } else {
         SHAPES.remove(uuid);
         COSMETICS.remove(uuid);
      }

      holder.syncStatus = PlayerConfigHolder.SyncStatus.SYNCED;
      holder.needsSync = false;
      holder.needsCloudSync = false;
   }

   public record Cosmetics(int shirt, int pants, int headwear, int pet) {
      public static final AppearanceState.Cosmetics NONE = new AppearanceState.Cosmetics(0, 0, 0, 0);
   }
}
