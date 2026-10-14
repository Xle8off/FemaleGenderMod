package com.wildfire.neoforge.appearance;
public final class ListeningSync {
    private ListeningSync() {}
    public static void tick() {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        int menuCategory = client.gui.screen() instanceof com.wildfire.neoforge.client.NotificationCenterScreen menu ? menu.worldMenuCategory() : -1;
        com.wildfire.client.music.MenuPresence.tick(client.player.getUUID(), menuCategory, json -> { if (client.getConnection() != null && client.getConnection().hasChannel(MenuPacket.TYPE)) net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new MenuPacket(json)); });
        boolean web = MusicClient.spotifyActive();
        var player = com.wildfire.client.music.SpotifyWebPlayer.view();
        com.wildfire.client.music.ListeningState.tick(client.player.getUUID(), MusicClient.title(), web ? player.artist() : "",
            MusicClient.serverActive() ? 0 : web ? com.wildfire.client.music.SpotifyWebPlayer.website().platform : 2,
            MusicClient.active() && !MusicClient.paused(), json -> { net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new ListeningPacket(json)); });
    }
}
