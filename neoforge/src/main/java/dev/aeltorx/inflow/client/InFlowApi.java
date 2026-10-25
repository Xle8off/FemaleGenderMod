package dev.aeltorx.inflow.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Stable host entry points. Opening a screen never grants server permissions. */
public final class InFlowApi {
    private InFlowApi() {}
    private static long lastDenied;
    public static int apiVersion() { return 1; }
    public static boolean allowed() { return ModerationService.allowed(); }
    private static boolean authorize() {
        if (allowed()) return true;
        long now = System.nanoTime();
        if (now - lastDenied > 1_000_000_000L) {
            lastDenied = now;
            ModerationService.notice("У вас нет доступа.");
        }
        return false;
    }
    public static void openPanel(Screen parent) {
        if (authorize()) ModerationWorkspace.open(parent);
    }
    public static void openWheel() {
        if (authorize()) Minecraft.getInstance().gui.setScreen(new ModerationWheelScreen());
    }
    public static void openPlayer(Screen parent, String name) {
        if (!authorize() || !name.matches("[A-Za-z0-9_]{1,16}")) return;
        var panel = new ModerationPanelScreen(parent);
        var player = ModerationService.players().stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst();
        if (player.isPresent()) panel.select(player.get());
        else panel.searchPlayer(name);
        ModerationWorkspace.present(panel);
    }
}
