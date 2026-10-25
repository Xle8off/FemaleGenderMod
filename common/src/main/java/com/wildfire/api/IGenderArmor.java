package com.wildfire.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildfire.api.impl.GenderArmor;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.TriState;
import org.jetbrains.annotations.ApiStatus.Internal;

public interface IGenderArmor {
   IGenderArmor DEFAULT = new IGenderArmor() {};
   IGenderArmor EMPTY = new IGenderArmor() {
      @Override
      public boolean coversBreasts() {
         return false;
      }

      @Override
      public float physicsResistance() {
         return 0.0F;
      }

      @Override
      public boolean armorStandsCopySettings() {
         return false;
      }
   };
   @Internal
   Codec<IGenderArmor> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            ExtraCodecs.floatRange(0.0F, 1.0F).optionalFieldOf("resistance", 0.5F).forGetter(IGenderArmor::physicsResistance),
            ExtraCodecs.floatRange(0.0F, 1.0F).optionalFieldOf("tightness", 0.0F).forGetter(IGenderArmor::tightness),
            Codec.BOOL.optionalFieldOf("covers_breasts", true).forGetter(IGenderArmor::coversBreasts),
            Codec.BOOL.optionalFieldOf("hide_breasts", false).forGetter(IGenderArmor::alwaysHidesBreasts),
            TriState.CODEC
               .optionalFieldOf("render_on_armor_stands", TriState.DEFAULT)
               .forGetter(armor -> armor.armorStandsCopySettings() ? TriState.TRUE : TriState.FALSE),
            IBreastArmorTexture.CODEC.optionalFieldOf("texture", IBreastArmorTexture.DEFAULT).forGetter(IGenderArmor::texture)
         )
         .apply(
            instance,
            (resistance, tightness, covers, hideBreasts, armorStands, texture) -> !covers
               ? EMPTY
               : new GenderArmor(resistance, tightness, true, hideBreasts, armorStands.toBoolean(resistance == 1.0F), texture)
         )
   );

   default boolean coversBreasts() {
      return true;
   }

   default boolean alwaysHidesBreasts() {
      return false;
   }

   default float physicsResistance() {
      return 0.5F;
   }

   default float tightness() {
      return 0.0F;
   }

   default boolean armorStandsCopySettings() {
      return !this.alwaysHidesBreasts() && this.coversBreasts() && this.physicsResistance() == 1.0F;
   }

   default IBreastArmorTexture texture() {
      return IBreastArmorTexture.DEFAULT;
   }
}
