package com.wildfire.gui;

import com.wildfire.client.config.CosmeticPreferences;
import com.wildfire.gui.screen.NotificationCenterScreen;
import com.wildfire.gui.RoundedPanels;
import com.wildfire.main.networking.appearance.MusicClient;
import com.wildfire.main.networking.appearance.NotificationInbox;
import net.minecraft.client.Minecraft;
import com.wildfire.main.networking.appearance.ModUpdate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Right-side indicators; clickable copies are supplied by chat/inventory screens. */
public final class QuickHud {
    private QuickHud() {}
    private static final com.wildfire.gui.MiniPlayerReveal trackReveal=new com.wildfire.gui.MiniPlayerReveal();
    private static Object trackConnection;

    public static void draw(GuiGraphicsExtractor graphics) {
        if (!com.wildfire.client.music.SpringPlusSession.enabled()) return;
        NotificationCenterScreen.cacheOnlineSkins();
        int x = 10;
        if (CosmeticPreferences.hudBell() && NotificationInbox.unread() > 0 && !ModUpdate.popupVisible())
            drawBell(graphics, x, 14 + ModUpdate.topInset(), false);
        if (ModUpdate.updateHudState() != 0 && !ModUpdate.popupVisible())
            com.wildfire.client.music.UpdateHudButton.drawIcon(graphics, x, 38 + ModUpdate.topInset(), ModUpdate.updateHudState());
        drawTrack(graphics);
    }

    public static void drawBell(GuiGraphicsExtractor graphics, int x, int y) {
        drawBell(graphics, x, y, true);
    }

    public static void drawBellIcon(GuiGraphicsExtractor graphics, int x, int y) {
        drawBell(graphics, x, y, false);
    }

    private static void drawBell(GuiGraphicsExtractor graphics, int x, int y, boolean background) {
        if (background) circle(graphics, x + 10, y + 10, 10, 0xE92C3541);
        graphics.item(new ItemStack(Items.BELL), x + 2, y + 2);
        int unread = NotificationInbox.unread();
        if (unread <= 0) return;
        int badgeX = x + 19, badgeY = y + 5;
        badgeCircle(graphics, badgeX, badgeY, 0xFFE13251);
        String label = unread > 99 ? "99+" : Integer.toString(unread);
        var font = Minecraft.getInstance().font;
        graphics.pose().pushMatrix();
        graphics.pose().translate(badgeX, badgeY);
        float scale = Math.min(0.52f, 7f / Math.max(1, font.width(label)));
        graphics.pose().scale(scale, scale);
        graphics.text(font, label, -font.width(label) / 2, -4, 0xFFFFFFFF, false);
        graphics.pose().popMatrix();
    }

    public static void drawSettings(GuiGraphicsExtractor graphics, int x, int y) {
        circle(graphics, x + 10, y + 10, 10, 0xE92C3541);
        drawSettingsIcon(graphics, x, y);
    }

    public static void drawSettingsIcon(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.item(new ItemStack(Items.COMPARATOR), x + 2, y + 2);
    }

    public static void drawTrack(GuiGraphicsExtractor graphics) {
        Object connection=Minecraft.getInstance().getConnection();
        if(connection!=trackConnection){trackConnection=connection;trackReveal.reset();}
        if (!com.wildfire.client.music.SpringPlusSession.enabled()){trackReveal.reset();return;}
        boolean allowed=CosmeticPreferences.hudTrack()
            &&!(Minecraft.getInstance().gui.screen() instanceof NotificationCenterScreen)
            &&!NotificationCenterScreen.hudPlayerVisible();
        var live=MusicClient.active()?new com.wildfire.gui.MiniPlayerReveal.Track(
            MusicClient.title(),MusicClient.timeLabel(),MusicClient.accent(),MusicClient.progress()):null;
        var frame=trackReveal.update(live,allowed,System.nanoTime());
        if(!allowed||frame==null||frame.visibility()<=0)return;
        var font = Minecraft.getInstance().font;
        String title = frame.track().title();
        String time = frame.track().time();
        int timeWidth = font.width(time);
        int desiredWidth = 27 + font.width(title) + (time.isEmpty() ? 0 : timeWidth + 8);
        int width = Math.min(Math.max(46, desiredWidth), Math.min(205, graphics.guiWidth() - 20));
        if (width < 46) return;
        int x = graphics.guiWidth() - width - 10, y = graphics.guiHeight() - 32;
        graphics.pose().pushMatrix();
        graphics.pose().translate(0,(float)(32*Math.max(1f-frame.visibility(),HudReveal.remaining())));
        RoundedPanels.fill(graphics, x, y, x + width, y + 22, 0xE92C3541);
        int accent = frame.track().accent();
        com.wildfire.client.music.MusicPlatformIcon.draw(graphics, accent, x + 3, y + 3);
        int available = Math.max(0, width - 27 - (time.isEmpty() ? 0 : timeWidth + 8));
        if (font.width(title) > available && !title.isEmpty()) {
            String loop = title + "   ·   ";
            int offset = (int) ((System.currentTimeMillis() / 180L) % loop.length());
            title = loop.substring(offset) + loop.substring(0, offset) + loop;
        }
        graphics.text(font, font.plainSubstrByWidth(title, available), x + 22, y + 7, com.wildfire.gui.UiTheme.TEXT, false);
        if (!time.isEmpty()) graphics.text(font, time, x + width - timeWidth - 5, y + 7, accent, false);
        float progress = frame.track().progress();
        if (progress >= 0) {
            // One pixel along the bottom row, clipped to the card's radius-4 contour.
            int left = x + 3, right = x + width - 3;
            graphics.fill(left, y + 21, right, y + 22, 0xFF343A43);
            graphics.fill(left, y + 21, left + Math.round((right - left) * Math.clamp(progress, 0f, 1f)), y + 22, accent);
        }
        graphics.pose().popMatrix();
    }

    private static void circle(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
        }
    }

    private static void badgeCircle(GuiGraphicsExtractor graphics, int x, int y, int color) {
            int[] halfWidths = {1, 2, 3, 4, 4, 4, 3, 2, 1};
            for (int row = 0; row < halfWidths.length; row++)
                graphics.fill(x - halfWidths[row], y + row - 4, x + halfWidths[row] + 1, y + row - 3, color);
    }
}
