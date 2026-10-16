package com.wildfire.client.moderation;

/** Bounded placement for mouse-only shortcuts; never occupies the chat composer. */
public final class InFlowHostLayout {
    public static final int SIZE = 20;
    public record Placement(int panelX, int wheelX, int y, boolean visible) {}
    private InFlowHostLayout() {}
    public static Placement overlay(int width, int height) {
        return new Placement(Math.max(0, width - 52), Math.max(0, width - 28), 8,
            width >= 128 && height >= 64);
    }
    public static boolean visible(boolean authorized, boolean currentScreen, boolean fits) {
        return authorized && currentScreen && fits;
    }
}
