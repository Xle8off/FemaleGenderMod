package com.wildfire.api.uvs;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record UVQuad(int x1, int y1, int x2, int y2) {
   public static final UVQuad UNUSED = new UVQuad(0, 0, 0, 0);
   public static final Codec<UVQuad> CODEC = Codec.INT.listOf(4, 4).xmap(UVQuad::fromIntList, UVQuad::toIntList);
   private static final Codec<UVQuad> LEGACY_CONFIG_CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.INT.fieldOf("x1").forGetter(UVQuad::x1),
            Codec.INT.fieldOf("y1").forGetter(UVQuad::y1),
            Codec.INT.fieldOf("x2").forGetter(UVQuad::x2),
            Codec.INT.fieldOf("y2").forGetter(UVQuad::y2)
         )
         .apply(instance, UVQuad::new)
   );
   public static final Codec<UVQuad> OR_LEGACY = CODEC.withAlternative(LEGACY_CONFIG_CODEC);
   public static final StreamCodec<ByteBuf, UVQuad> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, UVQuad::x1, ByteBufCodecs.VAR_INT, UVQuad::y1, ByteBufCodecs.VAR_INT, UVQuad::x2, ByteBufCodecs.VAR_INT, UVQuad::y2, UVQuad::new
   );

   public UVQuad addX1(int x1) {
      return new UVQuad(this.x1 + x1, this.y1, this.x2, this.y2);
   }

   public UVQuad addY1(int y1) {
      return new UVQuad(this.x1, this.y1 + y1, this.x2, this.y2);
   }

   public UVQuad addX2(int x2) {
      return new UVQuad(this.x1, this.y1, this.x2 + x2, this.y2);
   }

   public UVQuad addY2(int y2) {
      return new UVQuad(this.x1, this.y1, this.x2, this.y2 + y2);
   }

   public List<Integer> toIntList() {
      return List.of(this.x1, this.y1, this.x2, this.y2);
   }

   public static UVQuad fromIntList(List<Integer> list) {
      Preconditions.checkArgument(list.size() == 4, "Expected exactly 4 integer elements, got %s instead", list.size());
      return new UVQuad(list.get(0), list.get(1), list.get(2), list.get(3));
   }
}
