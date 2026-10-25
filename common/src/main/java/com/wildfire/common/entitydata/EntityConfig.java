package com.wildfire.common.entitydata;

import com.mojang.datafixers.Products.P3;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import com.wildfire.api.Gender;
import com.wildfire.common.config.UVs;
import com.wildfire.common.config.value.ConfigKey;
import com.wildfire.common.config.value.ConfigValue;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

public class EntityConfig {
   public static final ConfigKey<Gender> GENDER = new ConfigKey<>(Gender.MALE, Gender.CODEC_OR_LEGACY, Gender.STREAM_CODEC);
   public static final Codec<EntityConfig> CODEC = RecordCodecBuilder.create(instance -> codecGroup(instance).apply(instance, EntityConfig::new));
   public final ConfigValue<Gender> gender;
   public final Breasts breasts;
   public final UVs uvs;

   public static boolean isSupportedEntity(LivingEntity entity) {
      return entity instanceof Avatar || entity instanceof ArmorStand;
   }

   protected static <CONFIG extends EntityConfig> P3<Mu<CONFIG>, Gender, Breasts, UVs> codecGroup(Instance<CONFIG> instance) {
      return instance.group(
         GENDER.codecOrDefault("gender").forGetter(config -> config.gender.get()),
         Breasts.CODEC_OR_LEGACY.forGetter(config -> config.breasts),
         UVs.CODEC_OR_LEGACY.forGetter(config -> config.uvs)
      );
   }

   protected EntityConfig(Gender gender, Breasts breasts, UVs uvs) {
      this.gender = GENDER.createValueHandler(gender);
      this.breasts = breasts;
      this.uvs = uvs;
   }
}
