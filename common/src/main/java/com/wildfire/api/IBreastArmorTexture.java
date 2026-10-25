package com.wildfire.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildfire.api.impl.BreastArmorTexture;
import com.wildfire.api.uvs.UVDirection;
import com.wildfire.api.uvs.UVLayout;
import com.wildfire.api.uvs.UVMap;
import com.wildfire.api.uvs.UVQuad;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.joml.Vector2i;
import org.joml.Vector2ic;

public interface IBreastArmorTexture {
   IBreastArmorTexture DEFAULT = new IBreastArmorTexture() {};
   Vector2ic DEFAULT_TEXTURE_SIZE = new Vector2i(64, 32);
   Vector2ic DEFAULT_DIMENSIONS = new Vector2i(4, 5);
   @Deprecated
   Vector2ic DEFAULT_LEFT_UV = new Vector2i(16, 17);
   @Deprecated
   Vector2ic DEFAULT_RIGHT_UV = DEFAULT_LEFT_UV.add(DEFAULT_DIMENSIONS.x(), 0, new Vector2i());
   UVMap DEFAULT_UVS = new UVMap(
      new UVLayout.Immutable(
         Map.of(
            UVDirection.EAST,
            new UVQuad(24, 21, 28, 26),
            UVDirection.WEST,
            new UVQuad(16, 21, 20, 26),
            UVDirection.DOWN,
            new UVQuad(20, 17, 24, 21),
            UVDirection.UP,
            new UVQuad(20, 25, 24, 27),
            UVDirection.NORTH,
            new UVQuad(20, 21, 24, 26)
         )
      ),
      new UVLayout.Immutable(
         Map.of(
            UVDirection.EAST,
            new UVQuad(28, 21, 32, 26),
            UVDirection.WEST,
            new UVQuad(20, 21, 24, 26),
            UVDirection.DOWN,
            new UVQuad(24, 17, 28, 21),
            UVDirection.UP,
            new UVQuad(24, 25, 28, 27),
            UVDirection.NORTH,
            new UVQuad(24, 21, 28, 26)
         )
      )
   );
   @Internal
   Codec<IBreastArmorTexture> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            WildfireAPI.VECTOR_2I_CODEC.optionalFieldOf("texture_size", DEFAULT_TEXTURE_SIZE).forGetter(IBreastArmorTexture::textureSize),
            UVMap.CODEC.optionalFieldOf("uvs", DEFAULT_UVS).forGetter(IBreastArmorTexture::uvs)
         )
         .apply(instance, BreastArmorTexture::new)
   );

   default Vector2ic textureSize() {
      return DEFAULT_TEXTURE_SIZE;
   }

   @Deprecated
   default Vector2ic dimensions() {
      return DEFAULT_DIMENSIONS;
   }

   @Deprecated
   default Vector2ic leftUv() {
      return DEFAULT_LEFT_UV;
   }

   @Deprecated
   default Vector2ic rightUv() {
      return DEFAULT_RIGHT_UV;
   }

   default UVMap uvs() {
      return DEFAULT_UVS;
   }
}
