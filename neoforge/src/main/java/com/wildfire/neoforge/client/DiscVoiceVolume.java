package com.wildfire.neoforge.client;

import java.lang.reflect.Method;

/** Optional access to Plasmo Voice's "discs" source-line volume. */
public final class DiscVoiceVolume {
    private DiscVoiceVolume() {}

    private static Object config() throws ReflectiveOperationException {
        Class<?> client = Class.forName("su.plo.voice.client.ModVoiceClient");
        Object instance = client.getField("INSTANCE").get(null);
        if (instance == null) throw new IllegalStateException("Plasmo Voice is not initialized");
        return instance.getClass().getMethod("getConfig").invoke(instance);
    }

    private static Object volumes(Object config) throws ReflectiveOperationException {
        Object voice = config.getClass().getMethod("getVoice").invoke(config);
        return voice.getClass().getMethod("getVolumes").invoke(voice);
    }

    public static double get() {
        try {
            Object volume = volumes(config());
            Object entry = volume.getClass().getMethod("getVolume", String.class).invoke(volume, "discs");
            return Math.clamp(((Number) entry.getClass().getMethod("value").invoke(entry)).doubleValue(), 0d, 1d);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return -1d;
        }
    }

    public static boolean set(double value, boolean save) {
        try {
            Object config = config();
            Object volume = volumes(config);
            Method setter = volume.getClass().getMethod("setVolume", String.class, double.class);
            setter.invoke(volume, "discs", Math.clamp(value, 0d, 1d));
            if (save) config.getClass().getMethod("save", boolean.class).invoke(config, true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }
}
