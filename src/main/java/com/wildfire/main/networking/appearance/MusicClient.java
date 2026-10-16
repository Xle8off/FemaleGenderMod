package com.wildfire.main.networking.appearance;

public final class MusicClient {
    private static String id = "";
    private static String title = "";
    private static String position = "";
    private static long remainingMillis;
    private static long receivedAt;
    private static long trackStartedAt;
    private static long trackDurationMillis = -1;
    private MusicClient() {}
    public static void accept(MusicPackets.NowPlaying playing) {
        String incomingPosition = playing.position();
        long announcedDuration = -1;
        int separator = incomingPosition.lastIndexOf('|');
        if (separator >= 0) {
            try { announcedDuration = Long.parseLong(incomingPosition.substring(separator + 1)) * 1_000L; }
            catch (NumberFormatException ignored) { announcedDuration = -1; }
            incomingPosition = incomingPosition.substring(0, separator);
        }
        boolean changed = !playing.id().isEmpty() && !playing.id().equals(id);
        if (changed) {
            NotificationInbox.add("Музыка", playing.title(), "Трек " + incomingPosition);
            ModUpdate.musicFeedback();
        }
        id = playing.id();
        title = playing.title();
        position = incomingPosition;
        try { remainingMillis = Long.parseLong(playing.remaining()); }
        catch (NumberFormatException ignored) { remainingMillis = -1; }
        receivedAt = System.currentTimeMillis();
        if (changed || announcedDuration > 0 && trackDurationMillis != announcedDuration) {
            trackDurationMillis = announcedDuration > 0 ? announcedDuration : remainingMillis > 0 ? remainingMillis : -1;
            trackStartedAt = receivedAt - (announcedDuration > 0 && remainingMillis >= 0
                ? Math.max(0, announcedDuration - remainingMillis) : 0);
        }
    }
    public static boolean serverActive() {
        long elapsed = System.currentTimeMillis() - receivedAt;
        // The server refreshes playback roughly every eight seconds. Keep the HUD stable
        // across a late refresh; an explicit empty-id stop packet still clears it at once.
        return !id.isEmpty() && elapsed < (remainingMillis > 0 ? Math.max(remainingMillis, 10_000L) : 15_000L);
    }
    public static boolean spotifyActive() {
        var web = com.wildfire.client.music.SpotifyWebPlayer.view();
        return !serverActive() && web.running() && !web.title().isBlank();
    }
    public static int accent() {
        return serverActive() || !personalActive() ? 0xFFB681D4 : spotifyActive() ?
            com.wildfire.client.music.SpotifyWebPlayer.website().accent : 0xFFFFCC33;
    }
    public static boolean paused() { return spotifyActive() ? !com.wildfire.client.music.SpotifyWebPlayer.view().playing() :
        personalActive() && !com.wildfire.client.music.PersonalMusicPlayer.view().playing(); }
    public static void control(int button) {
        if (serverActive()) return;
        if (spotifyActive()) {
            if (button == 0) com.wildfire.client.music.SpotifyWebPlayer.previous();
            else if (button == 1) com.wildfire.client.music.SpotifyWebPlayer.toggle();
            else com.wildfire.client.music.SpotifyWebPlayer.next();
        } else if (personalActive()) {
            if (button == 0) com.wildfire.client.music.PersonalMusicPlayer.previous();
            else if (button == 1) com.wildfire.client.music.PersonalMusicPlayer.pause();
            else com.wildfire.client.music.PersonalMusicPlayer.next();
        }
    }
    public static boolean personalActive() { return spotifyActive() || !serverActive() && com.wildfire.client.music.PersonalMusicPlayer.view().track() != null; }
    public static boolean active() { return serverActive() || personalActive(); }
    public static boolean canSeek() { return personalActive() && progress() >= 0; }
    public static void seek(double ratio) {
        if (!canSeek() || !Double.isFinite(ratio)) return;
        ratio = Math.clamp(ratio, 0d, 1d);
        if (spotifyActive()) com.wildfire.client.music.SpotifyWebPlayer.seek(ratio);
        else {
            var player = com.wildfire.client.music.PersonalMusicPlayer.view();
            if (player.track() != null) com.wildfire.client.music.PersonalMusicPlayer.seek((long)(ratio * player.track().durationMillis()));
        }
    }
    public static String title() {
        if (spotifyActive()) return com.wildfire.client.music.SpotifyWebPlayer.view().title();
        var player = com.wildfire.client.music.PersonalMusicPlayer.view();
        return serverActive() ? title : player.track() != null ? player.track().title() : "";
    }
    public static String position() { return spotifyActive() ? "Личный · " + com.wildfire.client.music.SpotifyWebPlayer.website().label : personalActive() ? "Личный · Яндекс" : serverActive() ? position : ""; }
    public static String timeLabel() {
        if (spotifyActive()) {
            var web = com.wildfire.client.music.SpotifyWebPlayer.view();
            return clock(web.position()) + " / " + clock(web.duration());
        }
        if (personalActive()) {
            var player = com.wildfire.client.music.PersonalMusicPlayer.view();
            if (player.track() == null) return "";
            return clock(player.position()) + " / " + clock(player.track().durationMillis());
        }
        if (!active() || trackDurationMillis <= 0) return "";
        long elapsed = Math.clamp(System.currentTimeMillis() - trackStartedAt, 0L, trackDurationMillis);
        return clock(elapsed) + " / " + clock(trackDurationMillis);
    }
    private static String clock(long millis) {
        long seconds = Math.max(0L, Math.round(millis / 1_000d));
        return (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }
    public static long remainingMillis() { return remainingMillis < 0 ? -1 : Math.max(0, remainingMillis - (System.currentTimeMillis() - receivedAt)); }
    public static float progress() {
        if (spotifyActive()) {
            var web = com.wildfire.client.music.SpotifyWebPlayer.view();
            return web.duration() <= 0 ? -1f : Math.clamp((float) web.position() / web.duration(), 0f, 1f);
        }
        if (personalActive()) {
            var player = com.wildfire.client.music.PersonalMusicPlayer.view();
            if (player.track() == null) return -1f;
            return player.track().durationMillis() <= 0 ? -1f : Math.clamp((float) player.position() / player.track().durationMillis(), 0f, 1f);
        }
        if (!active() || trackDurationMillis <= 0) return -1f;
        return Math.clamp((float) (System.currentTimeMillis() - trackStartedAt) / trackDurationMillis, 0f, 1f);
    }
    public static void clear() { id = ""; title = ""; position = ""; receivedAt = 0; trackDurationMillis = -1; }
}
