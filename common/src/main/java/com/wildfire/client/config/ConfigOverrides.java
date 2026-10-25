package com.wildfire.client.config;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildfire.common.config.value.ConfigKey;
import com.wildfire.common.config.value.ConfigValue;

public record ConfigOverrides(ConfigValue<Boolean> armorPhysics, ConfigValue<Boolean> disableRendering, ConfigValue<Boolean> disableSoundReplacement) {
   public static final ConfigKey<Boolean> ARMOR_PHYSICS_OVERRIDE = ConfigKey.DEFAULT_FALSE;
   public static final ConfigKey<Boolean> DISABLE_RENDERING = ConfigKey.DEFAULT_FALSE;
   public static final ConfigKey<Boolean> DISABLE_SOUND_REPLACEMENT = ConfigKey.DEFAULT_FALSE;
   public static final MapCodec<ConfigOverrides> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            ARMOR_PHYSICS_OVERRIDE.codecOrDefault("armor_physics_override").forGetter(config -> config.armorPhysics.get()),
            DISABLE_RENDERING.codecOrDefault("disable_rendering").forGetter(config -> config.disableRendering.get()),
            DISABLE_SOUND_REPLACEMENT.codecOrDefault("disable_sound_replacement").forGetter(config -> config.disableSoundReplacement.get())
         )
         .apply(instance, ConfigOverrides::new)
   );

   public ConfigOverrides(boolean armorPhysics, boolean disableRendering, boolean disableSoundReplacement) {
      this(
         ARMOR_PHYSICS_OVERRIDE.createValueHandler(armorPhysics),
         DISABLE_RENDERING.createValueHandler(disableRendering),
         DISABLE_SOUND_REPLACEMENT.createValueHandler(disableSoundReplacement)
      );
   }
}
