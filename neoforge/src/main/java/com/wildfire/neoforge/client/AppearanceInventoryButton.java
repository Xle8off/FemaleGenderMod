package com.wildfire.neoforge.client;

import com.wildfire.client.appearance.AppearanceState;
import com.wildfire.neoforge.common.mixins.ContainerPositionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Vanilla-sized button beside the recipe book, following its animated inventory offset. */
public final class AppearanceInventoryButton extends Button {

    private AppearanceInventoryButton(int x, int y) {
        super(x, y, 20, 18, Component.literal("Образ"), button -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && AppearanceState.isActive()) client.player.connection.sendCommand("appearance");
        }, DEFAULT_NARRATION);
    }

    public static void onInit(ScreenEvent.Init.Post event) {
        if (!com.wildfire.client.music.SpringPlusSession.enabled()) return;
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        AppearanceInventoryButton button = new AppearanceInventoryButton(
            ((ContainerPositionAccessor) screen).springplus$getLeftPos() + 129, screen.height / 2 - 22);
        button.refresh(screen);
        event.addListener(button);
    }

    public static void onRender(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        for (var listener : screen.children()) {
            if (listener instanceof AppearanceInventoryButton button) button.refresh(screen);
        }
    }

    private void refresh(InventoryScreen screen) {
        setX(((ContainerPositionAccessor) screen).springplus$getLeftPos() + 129);
        setY(screen.height / 2 - 22);
        active = AppearanceState.isActive();
        setTooltip(Tooltip.create(active
            ? Component.literal("Открыть кастомизацию персонажа")
            : Component.literal("Кастомизация доступна только на сервере")
                .withStyle(style -> style.withColor(0xF3A5D8))
                .append(Component.literal("\n✦ SpringPlus.net ✦")
                    .withStyle(style -> style.withColor(0xB691FF).withBold(true)))));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        com.wildfire.gui.FlatButtons.plate(graphics,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,com.wildfire.gui.UiTheme.ACCENT);
        graphics.item(new ItemStack(Items.ARMOR_STAND), getX() + 2, getY() + 1);
    }
}
