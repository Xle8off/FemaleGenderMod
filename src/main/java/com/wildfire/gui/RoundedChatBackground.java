package com.wildfire.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Collects vanilla chat's line backgrounds and rounds only the outside corners. */
public final class RoundedChatBackground {
    private record Panel(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {}
    private static final ThreadLocal<List<Panel>> LINES = new ThreadLocal<>();

    private RoundedChatBackground() {}

    public static void begin() { LINES.set(new ArrayList<>()); }

    public static void fill(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        List<Panel> lines = LINES.get();
        if (lines == null) graphics.fill(left, top, right, bottom, color);
        else lines.add(new Panel(graphics, left, top, right, bottom, color));
    }

    public static void flush() {
        List<Panel> lines = LINES.get();
        LINES.remove();
        if (lines == null || lines.isEmpty()) return;
        // Minecraft still submits fading lines after their background alpha rounds to zero.
        // They must not keep the next visible line's corners square.
        lines.removeIf(line -> (line.color >>> 24) == 0 || line.right <= line.left || line.bottom <= line.top);
        lines.sort(Comparator.comparingInt(Panel::top));
        for (int i = 0; i < lines.size(); i++) {
            Panel line = lines.get(i);
            boolean joinsAbove = i > 0 && joins(lines.get(i - 1), line);
            boolean joinsBelow = i + 1 < lines.size() && joins(line, lines.get(i + 1));
            RoundedPanels.fillEdges(line.graphics, line.left, line.top, line.right, line.bottom,
                line.color, !joinsAbove, !joinsBelow, 3);
        }
    }

    private static boolean joins(Panel upper, Panel lower) {
        if (upper.bottom != lower.top || upper.left != lower.left || upper.right != lower.right) return false;
        int upperAlpha = upper.color >>> 24;
        int lowerAlpha = lower.color >>> 24;
        // As one message fades, expose the neighbouring message's rounded edge.
        return Math.min(upperAlpha, lowerAlpha) * 4 >= Math.max(upperAlpha, lowerAlpha) * 3;
    }
}
