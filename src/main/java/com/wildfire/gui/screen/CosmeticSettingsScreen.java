package com.wildfire.gui.screen;

import com.wildfire.client.config.CosmeticPreferences;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.UUID;

/** Local options that do not alter what other players see. */
public final class CosmeticSettingsScreen extends BaseWildfireScreen {
    public CosmeticSettingsScreen(Screen parent, UUID player) {
        super(Component.literal("Настройки SpringPlusCosmetics"), parent, player);
    }

    @Override protected void init() {
        super.init();
        int x = width / 2 - 100, y = height / 2 - 54;
        addButton(builder -> builder.message(() -> Component.literal("Звук урона: " +
                (CosmeticPreferences.modHurtSound() ? "мод" : "Minecraft")))
            .position(x, y).size(200, 22)
            .onPress(button -> { CosmeticPreferences.toggleHurtSound(); button.updateMessage(); }));
        addButton(builder -> builder.message(() -> Component.literal("Косметика: " +
                (CosmeticPreferences.cosmeticsVisible() ? "показывать" : "скрывать")))
            .position(x, y + 28).size(200, 22)
            .onPress(button -> { CosmeticPreferences.toggleCosmetics(); button.updateMessage(); }));
        addButton(builder -> builder.message(() -> Component.literal("Колокольчик: " +
                (CosmeticPreferences.hudBell() ? "показывать" : "скрыть")))
            .position(x, y + 56).size(200, 22)
            .onPress(button -> { CosmeticPreferences.toggleHudBell(); button.updateMessage(); }));
        addButton(builder -> builder.message(() -> Component.literal("Строка трека: " +
                (CosmeticPreferences.hudTrack() ? "показывать" : "скрыть")))
            .position(x, y + 84).size(200, 22)
            .onPress(button -> { CosmeticPreferences.toggleHudTrack(); button.updateMessage(); }));
        addButton(builder -> builder.message(() -> Component.literal("Готово"))
            .position(x + 50, y + 116).size(100, 20).onPress(_ -> onClose()));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(graphics);
        graphics.fill(width / 2 - 116, height / 2 - 83, width / 2 + 116, height / 2 + 92, 0xE51A1B25);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(font, "Настройки SpringPlusCosmetics", width / 2 - 82, height / 2 - 72, 0xFFFFFFFF, false);
        graphics.text(font, "Скрытие косметики влияет только на ваш клиент", width / 2 - 100,
            height / 2 + 58, 0xFFADB4C3, false);
    }
}
