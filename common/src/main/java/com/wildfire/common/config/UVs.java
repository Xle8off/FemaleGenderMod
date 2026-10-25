package com.wildfire.common.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildfire.api.uvs.UVLayout;
import com.wildfire.api.uvs.UVQuad;
import com.wildfire.common.WildfireHelper;
import com.wildfire.common.config.value.ConfigKey;
import com.wildfire.common.config.value.ConfigValue;
import io.netty.buffer.ByteBuf;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import net.minecraft.network.codec.StreamCodec;

public record UVs(UVs.Layer skin, UVs.Layer overlay) implements Iterable<UVLayout> {
   private static final ConfigKey<UVLayout> LEFT_BREAST_UV_LAYOUT = createConfigKey(
      new UVLayout(new UVQuad(24, 21, 27, 26), new UVQuad(16, 21, 20, 26), new UVQuad(20, 17, 24, 21), new UVQuad(20, 25, 24, 27), new UVQuad(20, 21, 24, 26))::copy
   );
   private static final ConfigKey<UVLayout> RIGHT_BREAST_UV_LAYOUT = createConfigKey(
      new UVLayout(new UVQuad(28, 21, 32, 26), new UVQuad(21, 21, 24, 26), new UVQuad(24, 17, 28, 21), new UVQuad(24, 25, 28, 27), new UVQuad(24, 21, 28, 26))::copy
   );
   private static final ConfigKey<UVLayout> LEFT_BREAST_OVERLAY_UV_LAYOUT = createConfigKey(
      new UVLayout(UVQuad.UNUSED, new UVQuad(17,37,20,42),new UVQuad(20,34,24,37),new UVQuad(20,41,24,44),new UVQuad(20,37,24,42))::copy);
   private static final ConfigKey<UVLayout> RIGHT_BREAST_OVERLAY_UV_LAYOUT = createConfigKey(
      new UVLayout(new UVQuad(28,37,31,42),UVQuad.UNUSED,new UVQuad(24,34,28,37),new UVQuad(24,41,28,44),new UVQuad(24,37,28,42))::copy);
   public static final Codec<UVs> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            UVs.Layer.codec(LEFT_BREAST_UV_LAYOUT, RIGHT_BREAST_UV_LAYOUT).fieldOf("skin").forGetter(UVs::skin),
            UVs.Layer.codec(LEFT_BREAST_OVERLAY_UV_LAYOUT, RIGHT_BREAST_OVERLAY_UV_LAYOUT).fieldOf("overlay").forGetter(UVs::overlay)
         )
         .apply(instance, UVs::new)
   );
   public static final MapCodec<UVs> LEGACY_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            UVs.Layer.legacyCodec(LEFT_BREAST_UV_LAYOUT, "leftBreastUVLayout", RIGHT_BREAST_UV_LAYOUT, "rightBreastUVLayout").forGetter(UVs::skin),
            UVs.Layer.legacyCodec(LEFT_BREAST_OVERLAY_UV_LAYOUT, "leftBreastOverlayUVLayout", RIGHT_BREAST_OVERLAY_UV_LAYOUT, "rightBreastOverlayUVLayout")
               .forGetter(UVs::overlay)
         )
         .apply(instance, UVs::new)
   );
   public static final MapCodec<UVs> CODEC_OR_LEGACY = WildfireHelper.withAlternative(CODEC.fieldOf("uvs"), LEGACY_CODEC);
   public static final StreamCodec<ByteBuf, UVs> STREAM_CODEC;

   private static ConfigKey<UVLayout> createConfigKey(Supplier<UVLayout> defaultValueSupplier) {
      return new ConfigKey<>(defaultValueSupplier, UVLayout.MUTABLE_CONFIG_CODEC, UVLayout.STREAM_CODEC);
   }

   @Override
   public Iterator<UVLayout> iterator() {
      return new UVs.LayoutIterator();
   }

   public boolean reset() {
      return this.skin.reset() | this.overlay.reset();
   }

   static {
      STREAM_CODEC = StreamCodec.composite(
         UVs.Layer.streamCodec(LEFT_BREAST_UV_LAYOUT, RIGHT_BREAST_UV_LAYOUT),
         UVs::skin,
         UVs.Layer.streamCodec(LEFT_BREAST_OVERLAY_UV_LAYOUT, RIGHT_BREAST_OVERLAY_UV_LAYOUT),
         UVs::overlay,
         UVs::new
      );
   }

   public record Layer(ConfigValue<UVLayout> left, ConfigValue<UVLayout> right) {
      private static Codec<UVs.Layer> codec(ConfigKey<UVLayout> leftKey, ConfigKey<UVLayout> rightKey) {
         return RecordCodecBuilder.create(
            instance -> instance.group(
                  leftKey.codec().fieldOf("left").forGetter(layer -> layer.left.get()), rightKey.codec().fieldOf("right").forGetter(layer -> layer.right.get())
               )
               .apply(instance, (left, right) -> new UVs.Layer(leftKey.createValueHandler(left), rightKey.createValueHandler(right)))
         );
      }

      private static MapCodec<UVs.Layer> legacyCodec(ConfigKey<UVLayout> leftKey, String leftName, ConfigKey<UVLayout> rightKey, String rightName) {
         return RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                  leftKey.codecOrDefault(leftName).forGetter(layer -> layer.left.get()),
                  rightKey.codecOrDefault(rightName).forGetter(layer -> layer.right.get())
               )
               .apply(instance, (left, right) -> new UVs.Layer(leftKey.createValueHandler(left), rightKey.createValueHandler(right)))
         );
      }

      private static StreamCodec<ByteBuf, UVs.Layer> streamCodec(ConfigKey<UVLayout> leftKey, ConfigKey<UVLayout> rightKey) {
         return StreamCodec.composite(
            leftKey.streamCodec(),
            layer -> layer.left.get(),
            rightKey.streamCodec(),
            layer -> layer.right.get(),
            (left, right) -> new UVs.Layer(leftKey.createValueHandler(left), rightKey.createValueHandler(right))
         );
      }

      public boolean reset() {
         return this.left.reset() | this.right.reset();
      }
   }

   private class LayoutIterator implements Iterator<UVLayout> {
      private int index = 0;

      @Override
      public boolean hasNext() {
         return this.index < 4;
      }

      public UVLayout next() {
         return switch (this.index++) {
            case 0 -> (UVLayout)UVs.this.skin().left().get();
            case 1 -> (UVLayout)UVs.this.skin().right().get();
            case 2 -> (UVLayout)UVs.this.overlay().left().get();
            case 3 -> (UVLayout)UVs.this.overlay().right().get();
            default -> throw new NoSuchElementException("No more UVLayouts");
         };
      }
   }
}
