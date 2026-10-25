package com.wildfire.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

public interface IFancyFontRenderer {
   default Font font() {
      return Minecraft.getInstance().font;
   }

   long getTimeOpened();

   default void drawScrollingString(
      GuiGraphicsExtractor graphics,
      Component text,
      int x,
      int y,
      IFancyFontRenderer.TextAlignment alignment,
      int color,
      int width,
      int maxLengthPad,
      boolean shadow
   ) {
      this.drawScrollingString(graphics, text.getVisualOrderText(), x, y, alignment, color, width, maxLengthPad, shadow, -1L);
   }

   default void drawScrollingString(
      GuiGraphicsExtractor graphics,
      FormattedCharSequence text,
      int x,
      int y,
      IFancyFontRenderer.TextAlignment alignment,
      int color,
      int width,
      int maxLengthPad,
      boolean shadow,
      long visibleFor
   ) {
      this.drawScrollingString(graphics, text, x, y, alignment, color, width, 9, maxLengthPad, shadow, visibleFor);
   }

   default void drawScrollingString(
      GuiGraphicsExtractor graphics,
      Component text,
      int x,
      int y,
      IFancyFontRenderer.TextAlignment alignment,
      int color,
      int width,
      int height,
      int maxLengthPad,
      boolean shadow
   ) {
      this.drawScrollingString(graphics, text.getVisualOrderText(), x, y, alignment, color, width, height, maxLengthPad, shadow, -1L);
   }

   default void drawScrollingString(
      GuiGraphicsExtractor graphics,
      FormattedCharSequence text,
      int x,
      int y,
      IFancyFontRenderer.TextAlignment alignment,
      int color,
      int width,
      int height,
      int maxLengthPad,
      boolean shadow,
      long visibleFor
   ) {
      this.drawScrollingString(graphics, text, x + maxLengthPad, y, x + width - maxLengthPad, y + height, alignment, color, shadow, visibleFor);
   }

   default void drawScrollingString(
      GuiGraphicsExtractor graphics,
      Component text,
      int minX,
      int minY,
      int maxX,
      int maxY,
      IFancyFontRenderer.TextAlignment alignment,
      int color,
      boolean shadow
   ) {
      this.drawScrollingString(graphics, text.getVisualOrderText(), minX, minY, maxX, maxY, alignment, color, shadow, -1L);
   }

   default void drawScrollingString(
      GuiGraphicsExtractor graphics,
      FormattedCharSequence text,
      int minX,
      int minY,
      int maxX,
      int maxY,
      IFancyFontRenderer.TextAlignment alignment,
      int color,
      boolean shadow,
      long visibleFor
   ) {
      Font font = this.font();
      int textWidth = font.width(text);
      int areaWidth = maxX - minX;
      boolean isScrolling = textWidth > areaWidth;
      float targetY = (minY + maxY - 9) / 2.0F;
      float targetX;
      if (isScrolling) {
         targetX = prepScrollingString(
            graphics, font, textWidth, areaWidth, minX, minY, maxX, maxY, visibleFor == -1L ? Util.getMillis() - this.getTimeOpened() : visibleFor
         );
      } else {
         targetX = alignment.getTarget(font, minX, maxX, textWidth);
      }

      Matrix3x2fStack pose = graphics.pose();
      pose.pushMatrix();
      pose.translate(targetX, targetY);
      graphics.text(font, text, 0, 0, color, shadow);
      pose.popMatrix();
      if (isScrolling) {
         graphics.disableScissor();
      }
   }

   default void drawScaledScrollingString(
      GuiGraphicsExtractor graphics,
      Component text,
      int x,
      int y,
      IFancyFontRenderer.TextAlignment alignment,
      int color,
      int width,
      int maxLengthPad,
      boolean shadow,
      float scale
   ) {
      this.drawScaledScrollingString(graphics, text, x, y, alignment, color, width, 9, maxLengthPad, shadow, scale);
   }

   default void drawScaledScrollingString(
      GuiGraphicsExtractor graphics,
      Component text,
      int x,
      int y,
      IFancyFontRenderer.TextAlignment alignment,
      int color,
      int width,
      int height,
      int maxLengthPad,
      boolean shadow,
      float scale
   ) {
      this.drawScaledScrollingString(graphics, text, x + maxLengthPad, y, x + width - maxLengthPad, y + height, alignment, color, shadow, scale);
   }

   default void drawScaledScrollingString(
      GuiGraphicsExtractor graphics,
      Component text,
      int minX,
      int minY,
      int maxX,
      int maxY,
      IFancyFontRenderer.TextAlignment alignment,
      int color,
      boolean shadow,
      float scale
   ) {
      this.drawScaledScrollingString(graphics, text, minX, minY, maxX, maxY, alignment, color, shadow, scale, -1L);
   }

   default void drawScaledScrollingString(
      GuiGraphicsExtractor graphics,
      Component text,
      int minX,
      int minY,
      int maxX,
      int maxY,
      IFancyFontRenderer.TextAlignment alignment,
      int color,
      boolean shadow,
      float scale,
      long visibleFor
   ) {
      if (Mth.equal(scale, 1.0F)) {
         this.drawScrollingString(graphics, text.getVisualOrderText(), minX, minY, maxX, maxY, alignment, color, shadow, visibleFor);
      } else {
         Font font = this.font();
         float textWidth = font.width(text) * scale;
         int areaWidth = maxX - minX;
         boolean isScrolling = textWidth > areaWidth;
         float targetY = (minY + maxY - 9) / 2.0F;
         float targetX;
         if (isScrolling) {
            targetX = prepScrollingString(
               graphics, font, textWidth, areaWidth, minX, minY, maxX, maxY, visibleFor == -1L ? Util.getMillis() - this.getTimeOpened() : visibleFor
            );
         } else {
            targetX = alignment.getTarget(font, minX, maxX, textWidth);
         }

         Matrix3x2fStack matrix = prepTextScale(graphics, font, targetX, targetY, scale);
         graphics.text(font, text, 0, 0, color, shadow);
         matrix.popMatrix();
         if (isScrolling) {
            graphics.disableScissor();
         }
      }
   }

   private static float prepScrollingString(
      GuiGraphicsExtractor graphics, Font font, float textWidth, int areaWidth, int minX, int minY, int maxX, int maxY, long visibleDuration
   ) {
      graphics.enableScissor(minX, minY, maxX, maxY);
      return minX - (float)getOverflowedBy(font, textWidth - areaWidth, visibleDuration);
   }

   private static double getOverflowedBy(Font font, double maxPosition, long visibleDuration) {
      double seconds = visibleDuration / 1000.0;
      double scrollPeriod = Math.max(maxPosition * 0.5, 3.0);
      double scrollSpeedModifier = Math.cos((Math.PI * 2) * seconds / scrollPeriod);
      if (!font.isBidirectional()) {
         scrollSpeedModifier = -scrollSpeedModifier;
      }

      double scrolledSoFar = Math.sin((Math.PI / 2) * scrollSpeedModifier) / 2.0 + 0.5;
      return scrolledSoFar * maxPosition;
   }

   private static Matrix3x2fStack prepTextScale(GuiGraphicsExtractor graphics, Font font, float x, float y, float scale) {
      Matrix3x2fStack matrix = graphics.pose();
      matrix.pushMatrix();
      float halfLineHeight = 9.0F / 2.0F;
      float yAdd = halfLineHeight - halfLineHeight * scale;
      matrix.translate(x, y + yAdd);
      matrix.scale(scale);
      return matrix;
   }

   default void drawCenteredTextWrapped(GuiGraphicsExtractor graphics, FormattedText text, int x, int y, int width, int color) {
      Font font = this.font();

      for (FormattedCharSequence line : font.split(text, width)) {
         int centeredX = x - font.width(line) / 2;
         graphics.text(font, line, centeredX, y, color, false);
         y += 9;
      }
   }

   enum TextAlignment {
      LEFT,
      CENTER,
      RIGHT,
      RELATIVE;

      public float getTarget(Font font, int minX, int maxX, float textWidth) {
         return switch (this) {
            case LEFT -> minX;
            case CENTER -> minX + (maxX - minX - textWidth) / 2.0F;
            case RIGHT -> maxX - textWidth;
            case RELATIVE -> font.isBidirectional() ? maxX - textWidth : minX;
         };
      }
   }
}
