package com.wildfire.neoforge.appearance;

import com.wildfire.client.config.CosmeticPreferences;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class NotificationPreferenceSync {
    private static long lastSent;
    private NotificationPreferenceSync() {}
    public static void tick() {
        if (Minecraft.getInstance().player == null) return;
        com.wildfire.client.music.ApiNoticeInbox.tick();
        com.wildfire.gui.WebStickers.tick();
        com.wildfire.client.overhead.OverheadCameraTextures.tick();
        com.wildfire.client.overhead.CameraClient.tick();
        PrivateMessageInbox.pollReadReceipts();
        long now = System.nanoTime();
        if (now - lastSent < 10_000_000_000L) return;
        lastSent = now;
        OptionalPayloadSender.sendToServer(new AppearancePackets.NotificationPreference((byte)(CosmeticPreferences.popupOnly() ? 1 : 0)));
    }
    public static void syncNow() { lastSent = 0; tick(); }
    public static void reset() { lastSent = 0; com.wildfire.client.music.ApiNoticeInbox.reset(); com.wildfire.client.music.CardBalanceClient.reset(); com.wildfire.client.overhead.CameraClient.reset(); com.wildfire.client.overhead.OverheadCameraTextures.clear(); }
}
