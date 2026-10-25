package com.wildfire.client.music;

import java.util.function.BooleanSupplier;

/** Fail closed until the current multiplayer connection completes the SpringPlus handshake. */
public final class SpringPlusSession {
    private static volatile BooleanSupplier permitted = () -> false;
    private static boolean wasEnabled;
    private SpringPlusSession() {}
    public static void configure(BooleanSupplier check) { permitted = java.util.Objects.requireNonNull(check); }
    public static boolean enabled() { return permitted.getAsBoolean(); }
    public static void tick() {
        boolean enabled = enabled();
        if (!enabled && (wasEnabled || SpotifyWebPlayer.view().running() || SpotifyWebPlayer.view().busy()
            || SpotifyWebPlayer.externalLoginOpen() || PersonalMusicPlayer.view().track() != null)) {
            SpotifyWebPlayer.disconnect();
            PersonalMusicPlayer.stop();
        }
        wasEnabled = enabled;
    }
}
