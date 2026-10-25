package com.wildfire.api.uvs;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum FaceDirection implements StringRepresentable {
   INNER("inner"),
   OUTER("outer"),
   TOP("top"),
   BOTTOM("bottom"),
   FRONT("front");

   public static final Codec<UVDirection> CODEC = StringRepresentable.fromEnum(UVDirection::values);
   private final String saveName;

   FaceDirection(String saveName) {
      this.saveName = saveName;
   }

   public String getSerializedName() {
      return this.saveName;
   }

   public String getTranslationKey() {
      return "female_gender_mod.uv_editor.faces." + this.saveName;
   }
}
