package com.wildfire.common.entitydata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildfire.common.WildfireHelper;
import com.wildfire.common.config.value.ConfigKey;
import com.wildfire.common.config.value.ConfigValue;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record Sounds(ConfigValue<Boolean> hurt, ConfigValue<Float> voicePitch) {
   private static final ConfigKey<Boolean> HURT_SOUNDS = ConfigKey.DEFAULT_TRUE;
   private static final ConfigKey<Float> VOICE_PITCH = ConfigKey.create(1.0F, 0.8F, 1.2F);
   public static final Codec<Sounds> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            HURT_SOUNDS.codec().fieldOf("override_hurt").forGetter(sounds -> sounds.hurt.get()),
            VOICE_PITCH.codec().fieldOf("voice_pitch").forGetter(sounds -> sounds.voicePitch.get())
         )
         .apply(instance, Sounds::new)
   );
   public static final MapCodec<Sounds> LEGACY_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            HURT_SOUNDS.codecOrDefault("hurt_sounds").forGetter(sounds -> sounds.hurt.get()),
            VOICE_PITCH.codecOrDefault("voice_pitch").forGetter(sounds -> sounds.voicePitch.get())
         )
         .apply(instance, Sounds::new)
   );
   public static final MapCodec<Sounds> CODEC_OR_LEGACY = WildfireHelper.withAlternative(CODEC.fieldOf("sound"), LEGACY_CODEC);
   public static final StreamCodec<ByteBuf, Sounds> STREAM_CODEC = StreamCodec.composite(
      HURT_SOUNDS.streamCodec(), sounds -> sounds.hurt.get(), VOICE_PITCH.streamCodec(), sounds -> sounds.voicePitch.get(), Sounds::new
   );

   private Sounds(boolean hurt, float voicePitch) {
      this(HURT_SOUNDS.createValueHandler(hurt), VOICE_PITCH.createValueHandler(voicePitch));
   }
}
