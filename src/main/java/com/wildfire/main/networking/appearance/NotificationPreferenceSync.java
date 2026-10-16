package com.wildfire.main.networking.appearance;

import com.wildfire.client.config.CosmeticPreferences;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Reannounces preference after backend switches; a missing packet leaves chat untouched. */
public final class NotificationPreferenceSync {
    private static long lastSent;
    private NotificationPreferenceSync() {}
    public static void tick() {
        com.wildfire.client.music.ApiNoticeInbox.tick();
        com.wildfire.gui.WebStickers.tick();
        com.wildfire.client.overhead.OverheadCameraTextures.tick();
        com.wildfire.client.overhead.CameraClient.tick();
        PrivateMessageInbox.pollReadReceipts();
        long now = System.nanoTime();
        if (now - lastSent < 10_000_000_000L) return;
        if (!ClientPlayNetworking.canSend(AppearancePackets.NotificationPreference.TYPE)) return;
        lastSent = now;
        ClientPlayNetworking.send(new AppearancePackets.NotificationPreference((byte)(CosmeticPreferences.popupOnly() ? 1 : 0)));
    }
    public static void syncNow() { lastSent = 0; tick(); }
    public static void reset() { lastSent = 0; com.wildfire.client.music.ApiNoticeInbox.reset(); }
}
