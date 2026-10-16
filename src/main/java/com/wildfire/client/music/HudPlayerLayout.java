package com.wildfire.client.music;

/** Shared geometry for drawing and input; independent of visibility and render order. */
public record HudPlayerLayout(int x, int y, int width, int height) {
    public static HudPlayerLayout of(int screenWidth) {
        int x = Math.max(8, screenWidth - 258);
        return new HudPlayerLayout(x, 10, Math.max(0, screenWidth - x - 8), 125);
    }
    public boolean fits(int screenHeight) { return width >= 140 && screenHeight >= y + height + 24; }
}
