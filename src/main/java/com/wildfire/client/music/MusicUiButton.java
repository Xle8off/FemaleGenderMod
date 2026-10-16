package com.wildfire.client.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import com.wildfire.gui.UiTheme;

/** Code-drawn music controls: no font-dependent Unicode playback symbols. */
public final class MusicUiButton extends Button {
    private final int accent;
    private final boolean selected;
    private long pressedUntil;
    private boolean providerIcon;
    public MusicUiButton providerIcon() {
        providerIcon = true;
        return this;
    }
    public MusicUiButton(int x, int y, int width, String label, int accent, boolean selected, OnPress action) {
        super(x, y, width, 22, Component.literal(label), action, DEFAULT_NARRATION);
        this.accent = accent; this.selected = selected;
    }
    @Override public void onPress(net.minecraft.client.input.InputWithModifiers event) {
        pressedUntil = System.nanoTime() + 120_000_000L; super.onPress(event);
    }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean hot = active && isHoveredOrFocused(), pressed = System.nanoTime() < pressedUntil;
        plate(g, getX(), getY(), getWidth(), getHeight(), accent, selected || hot, pressed);
        var font = Minecraft.getInstance().font;
        if (providerIcon) {
            MusicPlatformIcon.draw(g, accent, getX() + (getWidth()-16)/2, getY() + (getHeight()-16)/2);
            return;
        }
        int color = selected ? accent : active ? UiTheme.TEXT : UiTheme.MUTED;
        g.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2,
            getY() + (getHeight()-font.lineHeight)/2 + (pressed ? 1 : 0), color, false);
    }
    public static void plate(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, boolean hot, boolean pressed) {
        com.wildfire.gui.FlatButtons.plate(g,x,y,w,h,true,hot,pressed,accent);
    }
    private static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 3, y, x + w - 3, y + h, color);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        g.fill(x, y + 3, x + w, y + h - 3, color);
    }
    public static int controlLeft(int center, int index) { return center - 58 + (index == 0 ? 0 : index == 1 ? 38 : 84); }
    public static int controlWidth(int index) { return index == 1 ? 40 : 32; }
    public static int hit(double mx, double my, int center) {
        if (my < 107 || my >= 131) return -1;
        for (int i = 0; i < 3; i++) if (mx >= controlLeft(center,i) && mx < controlLeft(center,i) + controlWidth(i)) return i;
        return -1;
    }
    public static void playback(GuiGraphicsExtractor g, int x, int y, int w, int index, boolean paused,
                                int accent, boolean hover, boolean pressed) {
        plate(g, x, y, w, 24, accent, index == 1 || hover, pressed);
        int cx = x + w / 2, cy = y + 12 + (pressed ? 1 : 0), color = UiTheme.TEXT;
        if (index == 1 && !paused) {
            g.fill(cx - 4, cy - 5, cx - 1, cy + 5, color);
            g.fill(cx + 2, cy - 5, cx + 5, cy + 5, color);
        } else {
            int direction = index == 0 ? -1 : 1;
            for (int row = -5; row <= 5; row++) {
                int len = 6 - Math.abs(row);
                if (direction > 0) g.fill(cx - 3, cy + row, cx - 3 + len, cy + row + 1, color);
                else g.fill(cx + 3 - len, cy + row, cx + 3, cy + row + 1, color);
            }
            if (index != 1) {
                int bx = cx + direction * 5;
                g.fill(bx, cy - 5, bx + 2, cy + 6, color);
            }
        }
    }
}
