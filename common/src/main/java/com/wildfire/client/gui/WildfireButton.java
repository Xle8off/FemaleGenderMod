package com.wildfire.client.gui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.wildfire.gui.FlatButtons;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button.CreateNarration;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class WildfireButton extends Button implements IFancyFontRenderer {
   private final WildfireButton.@Nullable ButtonRenderer renderer;
   private final Supplier<Component> messageSupplier;
   public boolean transparent = false;
   private long lastMSInitialized;

   private WildfireButton(
      int x,
      int y,
      int w,
      int h,
      Supplier<Component> text,
      OnPress onPress,
      CreateNarration narrationSupplier,
      WildfireButton.@Nullable ButtonRenderer renderer
   ) {
      super(x, y, w, h, text.get(), onPress, narrationSupplier);
      this.messageSupplier = text;
      this.renderer = renderer;
   }

   public void updateMessage() {
      this.setMessage(this.messageSupplier.get());
   }

   protected void drawInner(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
      if (this.renderer != null) {
         this.renderer.render(this, graphics, mouseX, mouseY, partialTicks);
      } else {
         int textColor = this.active ? -1 : -10066330;
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
      }
   }

   protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
      if (!this.transparent) {
         FlatButtons.plate(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.active, this.isHoveredOrFocused(), false, -4418330);
      }

      this.drawInner(graphics, mouseX, mouseY, partialTicks);
      if (this.isHovered()) {
         graphics.requestCursor(this.active ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
      }
   }

   public WildfireButton setTransparent(boolean b) {
      this.transparent = b;
      return this;
   }

   public WildfireButton setActive(boolean b) {
      this.active = b;
      return this;
   }

   public WildfireButton setVisible(boolean visible) {
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
      private Supplier<Component> messageSupplier;
      private int x;
      private int y;
      private int width;
      private int height;
      private WildfireButton.PressAction onPress;
      private CreateNarration narrationSupplier = WildfireButton.DEFAULT_NARRATION;
      private @Nullable Tooltip tooltip = null;
      private WildfireButton.@Nullable ButtonRenderer renderer = null;
      private boolean active = true;

      public WildfireButton.Builder message(Supplier<Component> messageSupplier) {
         this.messageSupplier = messageSupplier;
         return this;
      }

      public WildfireButton.Builder position(int x, int y) {
         this.x = x;
         this.y = y;
         return this;
      }

      public WildfireButton.Builder size(int width, int height) {
         this.width = width;
         this.height = height;
         return this;
      }

      public WildfireButton.Builder onPress(WildfireButton.PressAction onPress) {
         this.onPress = onPress;
         return this;
      }

      public WildfireButton.Builder narration(CreateNarration narrationSupplier) {
         this.narrationSupplier = narrationSupplier;
         return this;
      }

      public WildfireButton.Builder tooltip(@Nullable Tooltip tooltip) {
         this.tooltip = tooltip;
         return this;
      }

      public WildfireButton.Builder active(Supplier<Boolean> activeSupplier) {
         return this.active(activeSupplier.get());
      }

      public WildfireButton.Builder active(boolean active) {
         this.active = active;
         return this;
      }

      public WildfireButton.Builder renderer(WildfireButton.@Nullable ButtonRenderer renderer) {
         this.renderer = renderer;
         return this;
      }

      public WildfireButton build(long msInitialized) {
         WildfireButton built = new WildfireButton(
            this.x, this.y, this.width, this.height, this.messageSupplier, this.onPress, this.narrationSupplier, this.renderer
         );
         built.lastMSInitialized = msInitialized;
         built.setActive(this.active);
         if (this.tooltip != null) {
            built.setTooltip(this.tooltip);
         }

         return built;
      }
   }

   @FunctionalInterface
   public interface ButtonRenderer {
      void render(WildfireButton var1, GuiGraphicsExtractor var2, int var3, int var4, float var5);
   }

   @FunctionalInterface
   public interface PressAction extends OnPress {
      default void onPress(Button button) {
         this.onPress((WildfireButton)button);
      }

      void onPress(WildfireButton var1);
   }
}
