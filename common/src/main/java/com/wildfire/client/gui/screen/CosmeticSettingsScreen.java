package com.wildfire.client.gui.screen;

import com.wildfire.client.config.CosmeticPreferences;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CosmeticSettingsScreen extends BaseWildfireScreen {
   public CosmeticSettingsScreen(Screen parent, UUID player) {
      super(Component.literal("Настройки SpringPlusCosmetics"), parent, player);
   }

   @Override
   protected void init() {
      super.init();
      int x = this.width / 2 - 100;
      int y = this.height / 2 - 54;
      this.addButton(
         builder -> builder.message(() -> Component.literal("Звук урона: " + (CosmeticPreferences.modHurtSound() ? "мод" : "Minecraft")))
            .position(x, y)
            .size(200, 22)
            .onPress(button -> {
               CosmeticPreferences.toggleHurtSound();
               button.updateMessage();
            })
      );
      this.addButton(
         builder -> builder.message(() -> Component.literal("Косметика: " + (CosmeticPreferences.cosmeticsVisible() ? "показывать" : "скрывать")))
            .position(x, y + 28)
            .size(200, 22)
            .onPress(button -> {
               CosmeticPreferences.toggleCosmetics();
               button.updateMessage();
            })
      );
      this.addButton(
         builder -> builder.message(() -> Component.literal("Колокольчик: " + (CosmeticPreferences.hudBell() ? "показывать" : "скрыть")))
            .position(x, y + 56)
            .size(200, 22)
            .onPress(button -> {
               CosmeticPreferences.toggleHudBell();
               button.updateMessage();
            })
      );
      this.addButton(
         builder -> builder.message(() -> Component.literal("Строка трека: " + (CosmeticPreferences.hudTrack() ? "показывать" : "скрыть")))
            .position(x, y + 84)
            .size(200, 22)
            .onPress(button -> {
               CosmeticPreferences.toggleHudTrack();
               button.updateMessage();
            })
      );
      this.addButton(builder -> builder.message(() -> Component.literal("Готово")).position(x + 50, y + 116).size(100, 20).onPress(var1x -> this.onClose()));
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      this.extractTransparentBackground(graphics);
      graphics.fill(this.width / 2 - 116, this.height / 2 - 83, this.width / 2 + 116, this.height / 2 + 92, -451273947);
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      super.extractRenderState(graphics, mouseX, mouseY, delta);
      graphics.text(this.font, "Настройки SpringPlusCosmetics", this.width / 2 - 82, this.height / 2 - 72, -1, false);
      graphics.text(this.font, "Скрытие косметики влияет только на ваш клиент", this.width / 2 - 100, this.height / 2 + 58, -5393213, false);
   }
}
