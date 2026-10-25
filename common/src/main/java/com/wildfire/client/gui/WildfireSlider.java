package com.wildfire.client.gui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.wildfire.common.WildfireHelper;
import com.wildfire.common.config.validator.ConfigRange;
import com.wildfire.common.config.validator.ConfigValidator;
import com.wildfire.common.config.value.ConfigValue;
import it.unimi.dsi.fastutil.floats.Float2ObjectFunction;
import it.unimi.dsi.fastutil.floats.FloatConsumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class WildfireSlider extends AbstractWidget implements IFancyFontRenderer {
   private double value;
   private final double minValue;
   private final double maxValue;
   private final FloatConsumer valueUpdate;
   private final Float2ObjectFunction<Component> messageUpdate;
   private final FloatConsumer onSave;
   private long lastMSInitialized;
   private float lastValue;
   private boolean changed;
   private boolean dragging;
   private double mouseStep = 0.0;
   private double arrowKeyStep = 0.05;

   private WildfireSlider(
      int xPos,
      int yPos,
      int width,
      int height,
      double minVal,
      double maxVal,
      double currentVal,
      FloatConsumer valueUpdate,
      Float2ObjectFunction<Component> messageUpdate,
      FloatConsumer onSave
   ) {
      super(xPos, yPos, width, height, CommonComponents.EMPTY);
      this.minValue = minVal;
      this.maxValue = maxVal;
      this.valueUpdate = valueUpdate;
      this.messageUpdate = messageUpdate;
      this.onSave = onSave;
      this.setValueInternal(currentVal);
   }

   public void setArrowKeyStep(double arrowKeyStep) {
      this.arrowKeyStep = arrowKeyStep;
   }

   private void setMouseStep(double mouseStep) {
      this.mouseStep = mouseStep;
   }

   protected void updateMessage() {
      this.setMessage((Component)this.messageUpdate.get(this.lastValue));
   }

   protected void applyValue() {
      float newValue = this.getFloatValue();
      if (this.lastValue != newValue) {
         this.valueUpdate.accept(newValue);
         this.lastValue = newValue;
         this.changed = true;
      }
   }

   public void save() {
      if (this.changed) {
         this.onSave.accept(this.lastValue);
         this.changed = false;
      }
   }

   public void onRelease(MouseButtonEvent event) {
      this.dragging = false;
      this.save();
   }

   public void onClick(MouseButtonEvent event, boolean doubleClick) {
      this.dragging = true;
      this.setValueFromMouse(event.x());
   }

   public boolean keyPressed(KeyEvent event) {
      int keyCode = event.key();
      if (keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_LEFT && keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) {
         return super.keyPressed(event);
      }

      this.value = this.value + (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT ? -this.arrowKeyStep : this.arrowKeyStep);
      this.value = WildfireHelper.snapToStep(Math.clamp(this.value, 0.0, 1.0), this.arrowKeyStep);
      this.applyValue();
      this.updateMessage();
      return true;
   }

   protected void onDrag(MouseButtonEvent event, double d, double e) {
      this.setValueFromMouse(event.x());
   }

   public boolean keyReleased(KeyEvent event) {
      int keyCode = event.key();
      if (keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_LEFT && keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) {
         return super.keyReleased(event);
      }

      this.save();
      return true;
   }

   protected MutableComponent createNarrationMessage() {
      return Component.translatable("gui.narrate.slider", new Object[]{this.getMessage()});
   }

   protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      if (this.visible) {
         int xP = this.getX() + 2;
         graphics.fill(xP - 2, this.getY(), this.getRight(), this.getBottom(), -2145246686);
         int xPos = this.getX() + 2 + (int)(this.value * (this.width - 3));
         graphics.fill(this.getX() + 1, this.getY() + 1, xPos - 1, this.getBottom() - 1, this.active ? -1272831386 : -1273949901);
         if (this.active) {
            int xPos2 = this.getX() + 3 + (int)(this.value * (this.width - 4));
            graphics.fill(xPos2 - 2, this.getY() + 1, xPos2, this.getBottom() - 1, ARGB.white(120));
         }

         int textColor = (!this.isHoveredOrFocused() || !this.active) && !this.changed ? -1 : -171;
         if (!this.active) {
            textColor = -10066330;
         }

         this.drawScrollingString(
            graphics,
            this.getMessage(),
            this.getX(),
            this.getY(),
            this.getRight() + 1,
            this.getBottom() + 1,
            IFancyFontRenderer.TextAlignment.CENTER,
            textColor,
            false
         );
         if (this.isHovered() || this.dragging) {
            if (!this.active) {
               graphics.requestCursor(CursorTypes.NOT_ALLOWED);
            } else {
               graphics.requestCursor(this.dragging ? CursorTypes.RESIZE_EW : CursorTypes.POINTING_HAND);
            }
         }
      }
   }

   public float getFloatValue() {
      return (float)this.getValue();
   }

   public double getValue() {
      return this.value * (this.maxValue - this.minValue) + this.minValue;
   }

   public void setValue(double value) {
      this.setValueInternal(value);
      this.applyValue();
   }

   private void setValueInternal(double value) {
      this.value = Math.clamp((value - this.minValue) / (this.maxValue - this.minValue), 0.0, 1.0);
      this.lastValue = (float)value;
      this.updateMessage();
   }

   public void updateWidgetNarration(NarrationElementOutput builder) {
      builder.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.slider", new Object[]{this.getMessage()}));
      if (this.active) {
         if (this.isFocused()) {
            builder.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.focused"));
         } else {
            builder.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.hovered"));
         }
      }
   }

   private void setValueFromMouse(double mouseX) {
      this.value = (mouseX - (this.getX() + 4)) / (this.getWidth() - 8);
      this.value = Math.clamp(this.value, 0.0, 1.0);
      if (this.mouseStep > 0.0) {
         double snapped = Math.round(this.value / this.mouseStep) * this.mouseStep;
         this.value = Math.clamp(snapped, 0.0, 1.0);
      }

      this.applyValue();
      this.updateMessage();
   }

   public WildfireSlider setVisible(boolean visible) {
      if (this.visible != visible) {
         this.visible = visible;
         if (visible) {
            this.lastMSInitialized = Util.getMillis();
         }
      }

      return this;
   }

   @Override
   public long getTimeOpened() {
      return this.lastMSInitialized;
   }

   public static final class Builder {
      private int x;
      private int y;
      private int width;
      private int height;
      private float min;
      private float max;
      private double value;
      private @Nullable Double step = null;
      private @Nullable Double mouseStep = null;
      private boolean active = true;
      private Float2ObjectFunction<Component> messageSupplier;
      private FloatConsumer onUpdate;
      private FloatConsumer onSave;

      public WildfireSlider.Builder message(Float2ObjectFunction<Component> messageSupplier) {
         this.messageSupplier = messageSupplier;
         return this;
      }

      public WildfireSlider.Builder position(int x, int y) {
         this.x = x;
         this.y = y;
         return this;
      }

      public WildfireSlider.Builder size(int width, int height) {
         this.width = width;
         this.height = height;
         return this;
      }

      public WildfireSlider.Builder update(FloatConsumer onUpdate) {
         this.onUpdate = onUpdate;
         return this;
      }

      public WildfireSlider.Builder save(FloatConsumer onSave) {
         this.onSave = onSave;
         return this;
      }

      public WildfireSlider.Builder range(ConfigValidator<Float> validator) {
         if (validator instanceof ConfigRange(Comparable var9, Comparable var11)) {
            Float var5 = (Float)var9;
            Float minInclusive = var5;
            var5 = (Float)var11;
            Float maxInclusive = var5;
            return this.range(minInclusive, maxInclusive);
         } else {
            return this;
         }
      }

      public WildfireSlider.Builder range(float min, float max) {
         this.min = min;
         this.max = max;
         return this;
      }

      public WildfireSlider.Builder current(double value) {
         this.value = value;
         return this;
      }

      public WildfireSlider.Builder forConfig(Supplier<? extends ConfigValue<Float>> configValue) {
         ConfigValue<Float> currentValue = (ConfigValue<Float>)configValue.get();
         return this.range(currentValue.validator()).current(currentValue.get().floatValue()).update(value -> configValue.get().update(value));
      }

      public WildfireSlider.Builder active(Supplier<Boolean> initiallyActive) {
         return this.active(initiallyActive.get());
      }

      public WildfireSlider.Builder active(boolean active) {
         this.active = active;
         return this;
      }

      public WildfireSlider.Builder step(double step) {
         this.step = step;
         return this;
      }

      public WildfireSlider.Builder mouseStep(double step) {
         this.mouseStep = step;
         return this;
      }

      public WildfireSlider build(long msInitialized) {
         WildfireSlider built = new WildfireSlider(
            this.x, this.y, this.width, this.height, this.min, this.max, this.value, this.onUpdate, this.messageSupplier, this.onSave
         );
         built.active = this.active;
         built.lastMSInitialized = msInitialized;
         if (this.step != null) {
            built.setArrowKeyStep(this.step);
         }

         if (this.mouseStep != null) {
            built.setMouseStep(this.mouseStep);
         }

         return built;
      }
   }
}
