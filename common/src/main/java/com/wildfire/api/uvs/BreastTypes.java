package com.wildfire.api.uvs;

public enum BreastTypes {
   LEFT,
   RIGHT,
   LEFT_OVERLAY,
   RIGHT_OVERLAY;

   public boolean isLeft() {
      return this == LEFT || this == LEFT_OVERLAY;
   }
}
