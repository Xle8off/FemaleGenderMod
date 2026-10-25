package com.wildfire.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.entitydata.EntityConfig;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.util.Util;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.jspecify.annotations.Nullable;

public final class WildfireAPI {
   public static final String MODID = "female_gender_mod";
   private static final Codec<Vector2ic> VEC2I_LEGACY_CODEC = RecordCodecBuilder.create(
      instance -> instance.group(Codec.INT.fieldOf("x").forGetter(Vector2ic::x), Codec.INT.fieldOf("y").forGetter(Vector2ic::y)).apply(instance, Vector2i::new)
   );
   static final Codec<Vector2ic> VECTOR_2I_CODEC = Codec.withAlternative(
      Codec.INT_STREAM.comapFlatMap(stream -> Util.fixedSize(stream, 2).map(Vector2i::new), vec2i -> IntStream.of(vec2i.x(), vec2i.y())), VEC2I_LEGACY_CODEC
   );

   public static @Nullable PlayerConfigHolder getPlayerById(UUID uuid) {
      return WildfireGender.getPlayerById(uuid);
   }

   public static Gender getPlayerGender(UUID uuid) {
      PlayerConfigHolder cfg = getPlayerById(uuid);
      return cfg == null ? EntityConfig.GENDER.defaultValue() : cfg.gender().get();
   }
}
