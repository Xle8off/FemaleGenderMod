package com.wildfire.api.uvs;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.UnmodifiableView;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jspecify.annotations.Nullable;

public class UVLayout {
   public static final Codec<UVLayout> CODEC = Codec.unboundedMap(UVDirection.CODEC, UVQuad.CODEC).xmap(UVLayout::createImmutable, UVLayout::getQuads);
   public static final Codec<UVLayout> MUTABLE_CONFIG_CODEC = Codec.unboundedMap(UVDirection.CODEC, UVQuad.OR_LEGACY).xmap(UVLayout::new, UVLayout::getQuads);
   public static final StreamCodec<ByteBuf, UVLayout> STREAM_CODEC = ByteBufCodecs.map(
         var0 -> new EnumMap<>(UVDirection.class), UVDirection.STREAM_CODEC, UVQuad.STREAM_CODEC, UVDirection.values().length
      )
      .map(UVLayout::new, UVLayout::getQuads);
   private final EnumMap<UVDirection, @Nullable UVQuad> quads = new EnumMap<>(UVDirection.class);

   private UVLayout(Map<UVDirection, @Nullable UVQuad> map) {
      this.quads.putAll(map);
      this.fillMissing();
   }

   public UVLayout(UVQuad east, UVQuad west, UVQuad down, UVQuad up, UVQuad north) {
      this.quads.put(UVDirection.EAST, east);
      this.quads.put(UVDirection.WEST, west);
      this.quads.put(UVDirection.DOWN, down);
      this.quads.put(UVDirection.UP, up);
      this.quads.put(UVDirection.NORTH, north);
   }

   public UVLayout() {
      this(Collections.emptyMap());
   }

   private void fillMissing() {
      this.quads.putIfAbsent(UVDirection.EAST, null);
      this.quads.putIfAbsent(UVDirection.WEST, null);
      this.quads.putIfAbsent(UVDirection.DOWN, null);
      this.quads.putIfAbsent(UVDirection.UP, null);
      this.quads.putIfAbsent(UVDirection.NORTH, null);
   }

   public void put(UVDirection dir, UVQuad quad) {
      this.quads.put(dir, quad);
   }

   public @Nullable UVQuad get(UVDirection dir) {
      return this.quads.get(dir);
   }

   public boolean has(UVDirection dir) {
      return this.quads.containsKey(dir) && this.quads.get(dir) != null;
   }

   @Internal
   public EnumMap<UVDirection, UVQuad> getQuads() {
      EnumMap<UVDirection, UVQuad> clone = this.quads.clone();
      clone.entrySet().removeIf(entry -> entry.getValue() == null);
      return clone;
   }

   public @UnmodifiableView Map<UVDirection, @Nullable UVQuad> getAllSides() {
      return Collections.unmodifiableMap(this.quads);
   }

   public UVLayout copy() {
      UVLayout copy = new UVLayout();
      copy.quads.putAll(this.quads);
      return copy;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return obj instanceof UVLayout other ? this.quads.equals(other.quads) : false;
      }
   }

   @Override
   public int hashCode() {
      return this.quads.hashCode();
   }

   private static UVLayout.Immutable createImmutable(Map<UVDirection, @Nullable UVQuad> quads) {
      return new UVLayout.Immutable(quads);
   }

   public static final class Immutable extends UVLayout {
      public Immutable(Map<UVDirection, @Nullable UVQuad> quads) {
         super(quads);
      }

      @Override
      public void put(UVDirection dir, UVQuad quad) {
         throw new UnsupportedOperationException();
      }
   }
}
