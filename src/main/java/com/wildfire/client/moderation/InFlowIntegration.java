package com.wildfire.client.moderation;

import com.wildfire.client.music.ChatOverlayButton;
import com.wildfire.gui.FlatButtons;
import com.wildfire.gui.UiTheme;
import com.wildfire.gui.screen.NotificationCenterScreen;
import dev.aeltorx.inflow.client.InFlowApi;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Host UI only. InFlow remains the sole owner of channels, tick handlers and F9/F10. */
public final class InFlowIntegration {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private InFlowIntegration() {}
    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof NotificationCenterScreen)) return;
            var panel = new Shortcut(screen, false);
            var wheel = new Shortcut(screen, true);
            Screens.getWidgets(screen).add(panel);
            if (!(screen instanceof NotificationCenterScreen)) Screens.getWidgets(screen).add(wheel);
            sync(screen, panel, wheel);
            ScreenEvents.beforeExtract(screen).register((current, graphics, mx, my, delta) ->
                sync(current, panel, wheel));
        });
    }
    private static void sync(Screen screen, Shortcut panel, Shortcut wheel) {
        var layout = InFlowHostLayout.overlay(screen.width, screen.height);
        boolean fits = layout.visible();
        int panelX = layout.panelX(), wheelX = layout.wheelX(), y = layout.y();
        if (screen instanceof NotificationCenterScreen center) {
            panelX = center.inflowControlX(false);
            wheelX = center.inflowControlX(true);
            fits = center.inflowControlsVisible();
        }
        boolean show = InFlowHostLayout.visible(InFlowApi.allowed(),
            net.minecraft.client.Minecraft.getInstance().gui.screen() == screen, fits);
        panel.setX(panelX); panel.setY(y);
        int size=screen instanceof NotificationCenterScreen?18:InFlowHostLayout.SIZE;
        panel.setWidth(size);panel.setHeight(size);
        wheel.setX(wheelX); wheel.setY(y);
        panel.visible = panel.active = show;
        wheel.visible = wheel.active = show && !(screen instanceof NotificationCenterScreen);
    }
    private static final class Shortcut extends ChatOverlayButton {
        private final boolean wheel;
        Shortcut(Screen parent, boolean wheel) {
            super(0, 8, InFlowHostLayout.SIZE, InFlowHostLayout.SIZE,
                Component.literal(wheel ? "Колесо модерации InFlow" : "Панель модерации InFlow"),
                ignored -> { if (wheel) InFlowApi.openWheel(); else InFlowApi.openPanel(parent); });
            this.wheel = wheel;
            visible = active = false;
            setTooltip(Tooltip.create(getMessage()));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            FlatButtons.plate(g, getX(), getY(), getWidth(), getHeight(), active,
                isHoveredOrFocused(), false, UiTheme.ACCENT);
            int x = getX() + (getWidth()-12)/2, y = getY() + (getHeight()-12)/2, color = UiTheme.ACCENT;
            if (wheel) {
                // Radial action wheel, code-native at the current GUI resolution.
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    int px = x + 5 + (int)Math.round(Math.cos(a) * 5);
                    int py = y + 5 + (int)Math.round(Math.sin(a) * 5);
                    g.fill(px, py, px + 2, py + 2, color);
                }
                g.fill(x + 4, y + 4, x + 8, y + 8, color);
            } else {
                // Shield: a distinct moderation entry, not another account/avatar button.
                g.fill(x + 1, y, x + 11, y + 2, color);
                g.fill(x + 1, y + 2, x + 3, y + 8, color);
                g.fill(x + 9, y + 2, x + 11, y + 8, color);
                for (int i = 0; i < 4; i++) g.fill(x + 2 + i, y + 8 + i, x + 10 - i, y + 9 + i, color);
                g.fill(x + 5, y + 3, x + 7, y + 8, color);
            }
        }
    }
}
