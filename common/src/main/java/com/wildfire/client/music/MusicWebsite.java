package com.wildfire.client.music;

import java.net.URI;

/** Explicit allowlist of embedded music websites. */
public enum MusicWebsite {
    SPOTIFY("Spotify", "https://open.spotify.com/", "spotify", 0xFF1DB954, 1),
    YANDEX("Яндекс", "https://music.yandex.ru/", "yandex", 0xFFFFCC33, 2),
    SOUNDCLOUD("SoundCloud", "https://soundcloud.com/", "soundcloud", 0xFFFF5500, 3),
    YOUTUBE("YT Music", "https://music.youtube.com/", "youtube-music", 0xFFFF3434, 4);
    public final String label, url, profile;
    public final int accent, platform;
    MusicWebsite(String label, String url, String profile, int accent, int platform) {
        this.label=label; this.url=url; this.profile=profile; this.accent=accent; this.platform=platform;
    }
    public boolean accepts(String value) {
        try {
            URI u=URI.create(value);
            return "https".equals(u.getScheme()) && URI.create(url).getHost().equals(u.getHost())
                && u.getUserInfo()==null && (u.getPort()==-1||u.getPort()==443);
        } catch(RuntimeException ignored) { return false; }
    }
}
