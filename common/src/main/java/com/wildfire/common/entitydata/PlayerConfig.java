package com.wildfire.common.entitydata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildfire.api.Gender;
import com.wildfire.common.config.UVs;
import com.wildfire.common.config.value.ConfigKey;
import com.wildfire.common.config.value.ConfigValue;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class PlayerConfig extends EntityConfig {
   private static final ConfigKey<Boolean> SHOW_IN_ARMOR = ConfigKey.DEFAULT_TRUE;
   public static final Codec<PlayerConfig> CODEC = RecordCodecBuilder.create(
      instance -> codecGroup(instance)
         .and(Sounds.CODEC_OR_LEGACY.forGetter(config -> config.sounds))
         .and(SHOW_IN_ARMOR.codecOrDefault("show_in_armor").forGetter(config -> config.showBreastsInArmor.get()))
         .apply(instance, PlayerConfig::new)
   );
   public static final StreamCodec<ByteBuf, PlayerConfig> STREAM_CODEC = StreamCodec.composite(
      GENDER.streamCodec(),
      config -> config.gender.get(),
      Breasts.STREAM_CODEC,
      config -> config.breasts,
      UVs.STREAM_CODEC,
      config -> config.uvs,
      Sounds.STREAM_CODEC,
      config -> config.sounds,
      SHOW_IN_ARMOR.streamCodec(),
      config -> config.showBreastsInArmor.get(),
      PlayerConfig::new
   );
   public static final StreamCodec<ByteBuf, PlayerConfig> COMPACT_STREAM_CODEC = new StreamCodec<ByteBuf, PlayerConfig>() {
      public PlayerConfig decode(ByteBuf input) {
         return input.readBoolean() ? (PlayerConfig)PlayerConfig.STREAM_CODEC.decode(input) : PlayerConfig.createDefault();
      }

      public void encode(ByteBuf output, PlayerConfig config) {
         if (config.gender.get() == Gender.MALE) {
            output.writeBoolean(false);
         } else {
            output.writeBoolean(true);
            PlayerConfig.STREAM_CODEC.encode(output, config);
         }
      }
   };
   public static final Codec<PlayerConfig> CLOUD_SYNC_CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Gender.BY_ID_CODEC.fieldOf("gender").orElseGet(GENDER::defaultValue).forGetter(config -> config.gender.get()),
            Breasts.LEGACY_CODEC.forGetter(config -> config.breasts),
            UVs.CODEC_OR_LEGACY.forGetter(config -> config.uvs),
            Sounds.LEGACY_CODEC.forGetter(config -> config.sounds),
            SHOW_IN_ARMOR.codecOrDefault("show_in_armor").forGetter(config -> config.showBreastsInArmor.get())
         )
         .apply(instance, PlayerConfig::new)
   );
   public final ConfigValue<Boolean> showBreastsInArmor;
   public final Sounds sounds;

   public static PlayerConfig createDefault() {
      return (PlayerConfig)CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.emptyMap()).getOrThrow();
   }

   private PlayerConfig(Gender gender, Breasts breasts, UVs uvs, Sounds sounds, boolean showBreastsInArmor) {
      this.sounds = sounds;
      this.showBreastsInArmor = SHOW_IN_ARMOR.createValueHandler(showBreastsInArmor);
      super(gender, breasts, uvs);
   }
}
