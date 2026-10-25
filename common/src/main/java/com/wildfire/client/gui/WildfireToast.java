package com.wildfire.client.gui;

import com.wildfire.client.WildfireKeyBindings;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.client.gui.screen.BaseWildfireScreen;
import com.wildfire.common.WildfireGender;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

public class WildfireToast implements Toast, IFancyFontRenderer {
   private static final Identifier TEXTURE = Identifier.withDefaultNamespace("toast/advancement");
   private static final Identifier ICON = WildfireGender.id("bc_ribbon");
   private final List<FormattedCharSequence> text;
   private Visibility visibility = Visibility.SHOW;

   public WildfireToast(Font textRenderer, Component title, @Nullable Component description) {
      this.text = new ArrayList<>(2);
      this.text.addAll(textRenderer.split(title.copy().withColor(-13108), 126));
      if (description != null) {
         this.text.addAll(textRenderer.split(description, 126));
      }
   }

   public Visibility getWantedVisibility() {
      return this.visibility;
   }

   public void update(ToastManager manager, long time) {
      if (this.shouldHide()) {
         this.hide();
         if (ClientConfig.config().showToast().update(false)) {
            CompletableFuture.runAsync(ClientConfig.INSTANCE::save);
         }
      }
   }

   public int height() {
      return 7 + this.getTextHeight() + 3;
   }

   private int getTextHeight() {
      return Math.max(this.text.size(), 2) * 11;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
      int i = this.height();
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, this.width(), i);
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ICON, 6, 6, 20, 20);
      int j = this.text.size() * 11;
      int lineY = 7 + (this.getTextHeight() - j) / 2;

      for (FormattedCharSequence line : this.text) {
         this.drawScrollingString(graphics, line, 26, lineY, IFancyFontRenderer.TextAlignment.RELATIVE, -1, this.width() - 35, 2, false, fullyVisibleForMs);
         lineY += 11;
      }
   }

   private boolean shouldHide() {
      Minecraft client = Minecraft.getInstance();
      return client.gui.screen() instanceof BaseWildfireScreen ? true : WildfireKeyBindings.INSTANCE.configKey().isDown();
   }

   public void hide() {
      this.visibility = Visibility.HIDE;
   }

   @Override
   public long getTimeOpened() {
      return 0L;
   }
}
