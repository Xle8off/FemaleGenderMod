package com.wildfire.client.render;

public enum BreastSide {
   LEFT(true),
   RIGHT(false);

   private final boolean isLeft;

   BreastSide(boolean isLeft) {
      this.isLeft = isLeft;
   }

   public float leftOrNegate(float left) {
      return this.isLeft ? left : -left;
   }

   public float forSide(float left, float right) {
      return this.isLeft ? left : right;
   }

   public <T> T forSide(T left, T right) {
      return this.isLeft ? left : right;
   }
}
