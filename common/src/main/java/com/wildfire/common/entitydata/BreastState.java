package com.wildfire.common.entitydata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.joml.Vector3fc;

public record BreastState(float breastSize, float cleavage, Vector3fc offsets) {
   public static final Codec<BreastState> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Breasts.BUST_SIZE.codec().fieldOf("size").forGetter(BreastState::breastSize),
            Breasts.BREASTS_CLEAVAGE.codec().fieldOf("cleavage").forGetter(BreastState::cleavage),
            Breasts.OFFSET_CODEC.fieldOf("offset").forGetter(BreastState::offsets)
         )
         .apply(instance, BreastState::new)
   );

   public BreastState(BreastDataComponent component) {
      this(component.breastSize(), component.cleavage(), component.offsets());
   }

   public BreastState(Breasts breasts) {
      this(breasts.bustSize().get(), breasts.cleavage().get(), breasts.offset());
   }
}
