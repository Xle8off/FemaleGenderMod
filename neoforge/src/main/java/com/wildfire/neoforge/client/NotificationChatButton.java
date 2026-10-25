package com.wildfire.neoforge.client;

import com.wildfire.neoforge.appearance.NotificationInbox;
import com.wildfire.neoforge.appearance.ModUpdate;
import com.wildfire.neoforge.appearance.SocialClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import com.wildfire.client.config.CosmeticPreferences;
import com.wildfire.client.gui.screen.CosmeticSettingsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Bell shown only while chat is open. */
public final class NotificationChatButton extends com.wildfire.client.music.ChatOverlayButton {
    private NotificationChatButton(Screen parent) {
        super(10, 14, 20, 20, Component.literal("Уведомления"), button ->
            Minecraft.getInstance().gui.setScreen(new NotificationCenterScreen(parent)));
        setTooltip(Tooltip.create(Component.literal("Уведомления SpringPlus")));
    }

    @Override public ComponentPath nextFocusPath(FocusNavigationEvent event) { return null; }

    public static void onInit(ScreenEvent.Init.Post event) {
        if (!com.wildfire.client.music.SpringPlusSession.enabled()) return;
        Screen screen = event.getScreen();
        if (!(screen instanceof ChatScreen)) return;
        event.addListener(NotificationCenterScreen.hudPlayerWidget(screen));
        event.addListener(new NotificationChatButton(screen));
        event.addListener(new StreamHudButton(screen));
        event.addListener(new com.wildfire.client.music.UpdateHudButton(ModUpdate::updateHudState, ModUpdate::activateUpdateHud));
        for (int slot = 0; slot < 3; slot++) {
            final int toastSlot=slot;
            event.addListener(new com.wildfire.gui.ToastDismissButton(()->ModUpdate.popupId(toastSlot),
                ()->ModUpdate.popupTop(toastSlot),ModUpdate::popupControlsReveal,ModUpdate::dismissPopup));
            event.addListener(new PopupActionButton(screen, true, slot));
            event.addListener(new PopupActionButton(screen, false, slot));
        }
    }

    public static void onRender(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen)) return;
        for (var child : event.getScreen().children())
            if (child instanceof NotificationChatButton button) {
                boolean bellVisible=!ModUpdate.popupVisible();
                button.visible=bellVisible;
                button.setY(14 + ModUpdate.topInset());
            } else if (child instanceof StreamHudButton button) {
                button.sync(14 + ModUpdate.topInset(), !ModUpdate.popupVisible());
            } else if (child instanceof com.wildfire.client.music.UpdateHudButton button) {
                button.sync(38 + ModUpdate.topInset(),
                    com.wildfire.client.music.SpringPlusSession.enabled() && !ModUpdate.popupVisible());
            } else if(child instanceof com.wildfire.gui.ToastDismissButton button){
                button.sync(event.getScreen().width);
            } else if (child instanceof PopupActionButton button) {
                var action = ModUpdate.popupAction(button.slot);
                button.displayedId = ModUpdate.popupId(button.slot);
                boolean single = action != null && ("message".equals(action.kind()) || "friend_message".equals(action.kind()) || "stream".equals(action.kind()));
                button.visible = action != null && (button.primary || !single);
                if (action != null) {
                    button.setWidth(button.primary && ("friend_message".equals(action.kind())||"stream".equals(action.kind())) ? 160 : button.primary ? 100 : 106);
                    button.setX(button.primary ? single ? 10 + (212 - button.getWidth()) / 2 : 10 : 116);
                    button.setY(ModUpdate.popupActionY(button.slot));
                    button.setMessage(Component.literal(button.primary
                        ? ("stream".equals(action.kind())?"Смотреть стрим":"friend_message".equals(action.kind()) ? "Написать сообщение" : "notification".equals(action.kind()) ? "Посмотреть" : "message".equals(action.kind()) ? "Ответить" : "Принять")
                        : ("notification".equals(action.kind()) ? "Закрыть" : "message".equals(action.kind()) ? "В друзья" : "Отклонить")));
                }
            }
        if (event.getScreen() instanceof ChatScreen) QuickHud.drawTrack(event.getGuiGraphics());
    }

    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        com.wildfire.gui.FlatButtons.plate(graphics,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,com.wildfire.gui.UiTheme.ACCENT);
        QuickHud.drawBellIcon(graphics, getX(), getY());
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        return !com.wildfire.neoforge.appearance.ModUpdate.popupVisible() && super.mouseClicked(click, doubled);
    }

    private static final class PopupActionButton extends com.wildfire.gui.ToastControlButton {
        private final boolean primary;
        private final int slot;
        private long displayedId = -1;

        private PopupActionButton(Screen parent, boolean primary, int slot) {
            super(primary ? 10 : 116, 0, primary ? 100 : 106, 20, Component.empty(),
                button -> ((PopupActionButton)button).runAction(parent, primary),ModUpdate::popupControlsReveal);
            this.primary = primary;
            this.slot = slot;
            visible = false;
        }

        @Override public ComponentPath nextFocusPath(FocusNavigationEvent event) { return null; }

        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractContents(graphics,mouseX,mouseY,delta);
        }

        private void runAction(Screen parent, boolean primary) {
            if (displayedId != ModUpdate.popupId(slot)) return;
            var action = ModUpdate.popupAction(slot);
            if (action == null) return;
            if ("notification".equals(action.kind())) {
                if(primary){var center=new NotificationCenterScreen(parent);center.openNotification(action.target());Minecraft.getInstance().gui.setScreen(center);}
            } else if(primary&&"stream".equals(action.kind())) {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(action.target()));
                } catch (Exception ignored) {
                }
            }
            else if (primary && ("message".equals(action.kind()) || "friend_message".equals(action.kind())))
                Minecraft.getInstance().gui.setScreen(new NotificationCenterScreen(parent, action.target()));
            else SocialClient.action("message".equals(action.kind()) ? "add" : primary ? "add" : "reject", action.target());
            ModUpdate.dismissPopup(displayedId);
        }
    }
}
