package com.wildfire.api.impl;

import com.wildfire.api.IBreastArmorTexture;
import com.wildfire.api.IGenderArmor;

public record GenderArmor(
   float physicsResistance, float tightness, boolean coversBreasts, boolean alwaysHidesBreasts, boolean armorStandsCopySettings, IBreastArmorTexture texture
) implements IGenderArmor {
   public GenderArmor(float physicsResistance, float tightness) {
      this(physicsResistance, tightness, true, false, physicsResistance == 1.0F, IBreastArmorTexture.DEFAULT);
   }

   public GenderArmor(float physicsResistance, float tightness, boolean armorStandsCopySettings) {
      this(physicsResistance, tightness, true, false, armorStandsCopySettings, IBreastArmorTexture.DEFAULT);
   }
}
