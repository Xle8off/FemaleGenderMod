package com.wildfire.neoforge.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Pixel-aligned rounded panels, keeping vanilla HUD geometry and alpha. */
public final class RoundedPanels {
    private RoundedPanels() {}

    public static void fill(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        fill(graphics, left, top, right, bottom, color, 4);
    }

    public static void fill(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color, int radius) {
        fillEdges(graphics, left, top, right, bottom, color, true, true, radius);
    }

    /** Rounded ends for thin progress and volume tracks (including three-pixel bars). */
    public static void fillBar(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        if (right - left < 3 || bottom - top < 3) {
            if (right > left && bottom > top) graphics.fill(left, top, right, bottom, color);
            return;
        }
        graphics.fill(left + 1, top, right - 1, top + 1, color);
        graphics.fill(left, top + 1, right, bottom - 1, color);
        graphics.fill(left + 1, bottom - 1, right - 1, bottom, color);
    }

    /** Only outside left corners, with a square inner edge. */
    public static void fillLeftBar(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        if (right <= left || bottom <= top) return;
        int inset = right - left >= 2 && bottom - top >= 3 ? 1 : 0;
        graphics.fill(left + inset, top, right, top + 1, color);
        if (bottom - top > 2) graphics.fill(left, top + 1, right, bottom - 1, color);
        if (bottom - top > 1) graphics.fill(left + inset, bottom - 1, right, bottom, color);
    }

    /** Round only the outside right corners of a thin accent; keep the inner edge square. */
    public static void fillRightBar(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        if (right <= left || bottom <= top) return;
        int inset = right - left >= 2 && bottom - top >= 3 ? 1 : 0;
        graphics.fill(left, top, right - inset, top + 1, color);
        if (bottom - top > 2) graphics.fill(left, top + 1, right, bottom - 1, color);
        if (bottom - top > 1) graphics.fill(left, bottom - 1, right - inset, bottom, color);
    }

    /** Round only top-left and bottom-left; the right side is square. */
    public static void fillLeft(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color, int radius) {
        int width = right - left, height = bottom - top;
        if (width <= 0 || height <= 0) return;
        int r = Math.min(radius, Math.min(width, height) / 2);
        if (r < 2) { graphics.fill(left, top, right, bottom, color); return; }
        for (int row = 0; row < r; row++) {
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - (r - row - 0.5) * (r - row - 0.5)));
            graphics.fill(left + inset, top + row, right, top + row + 1, color);
            graphics.fill(left + inset, bottom - row - 1, right, bottom - row, color);
        }
        graphics.fill(left, top + r, right, bottom - r, color);
    }

    /** Square screen-facing left edge; independently rounded right corners. */
    public static void fillRightEdges(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom,
                                      int color, boolean roundTop, boolean roundBottom, int radius) {
        int width = right - left, height = bottom - top;
        if (width <= 0 || height <= 0) return;
        int r = Math.min(radius, Math.min(width, height) / 2);
        if (r < 2) { graphics.fill(left, top, right, bottom, color); return; }
        for (int row = 0; row < r; row++) {
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - (r - row - 0.5) * (r - row - 0.5)));
            graphics.fill(left, top + row, right - (roundTop ? inset : 0), top + row + 1, color);
            graphics.fill(left, bottom - row - 1, right - (roundBottom ? inset : 0), bottom - row, color);
        }
        graphics.fill(left, top + r, right, bottom - r, color);
    }

    public static void fillEdges(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom,
                                 int color, boolean roundTop, boolean roundBottom, int radius) {
        int width = right - left, height = bottom - top;
        if (width <= 0 || height <= 0) return;
        int r = Math.min(radius, Math.min(width, height) / 2);
        if (r < 2) {
            graphics.fill(left, top, right, bottom, color);
            return;
        }
        for (int row = 0; row < r; row++) {
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - (r - row - 0.5) * (r - row - 0.5)));
            graphics.fill(left + (roundTop ? inset : 0), top + row,
                right - (roundTop ? inset : 0), top + row + 1, color);
            graphics.fill(left + (roundBottom ? inset : 0), bottom - row - 1,
                right - (roundBottom ? inset : 0), bottom - row, color);
        }
        graphics.fill(left, top + r, right, bottom - r, color);
    }
}
