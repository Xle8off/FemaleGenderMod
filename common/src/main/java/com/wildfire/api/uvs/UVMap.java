package com.wildfire.api.uvs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record UVMap(UVLayout left, UVLayout right) {
   public static final Codec<UVMap> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(UVLayout.CODEC.fieldOf("left").forGetter(UVMap::left), UVLayout.CODEC.fieldOf("right").forGetter(UVMap::right))
         .apply(instance, UVMap::new)
   );
}
