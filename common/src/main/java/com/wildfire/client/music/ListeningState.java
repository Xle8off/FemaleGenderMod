package com.wildfire.client.music;
import com.google.gson.*;
import com.wildfire.client.config.CosmeticPreferences;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Bounded, cached presence labels; no per-frame text creation and no account credentials. */
public final class ListeningState {
    public record Card(Component title, Component artist, String platform, int accent) {}
    private record Label(Component text, Card card, long received) {}
    private static final Map<UUID, Label> labels = new HashMap<>();
    private static String last = "";
    private static String lastLocal = "";
    private static long checked, sent;
    private ListeningState() {}
    public static void tick(UUID self, String title, String artist, int platform, boolean playing, Consumer<String> send) {
        long now = System.nanoTime();
        if (now - checked < 1_000_000_000L) return;
        checked = now;
        title = clean(title, 80); artist = clean(artist, 48);
        JsonObject data = new JsonObject();
        data.addProperty("title", title); data.addProperty("artist", artist);
        data.addProperty("platform", Math.clamp(platform, 0, 4)); data.addProperty("playing", playing && !title.isBlank());
        String local = data.toString();
        if (self != null && (!local.equals(lastLocal) || now - sent > 30_000_000_000L)) {
            put(self, title, artist, platform, playing, now); lastLocal = local;
        }
        if (!CosmeticPreferences.shareListening()) { data.addProperty("title", ""); data.addProperty("artist", ""); data.addProperty("playing", false); }
        data.addProperty("receive", CosmeticPreferences.musicLabels());
        String value = data.toString();
        if (!value.equals(last) || now - sent > 30_000_000_000L) { send.accept(value); last = value; sent = now; }
        labels.entrySet().removeIf(e -> now - e.getValue().received > 90_000_000_000L);
    }
    public static void accept(String json) {
        try {
            JsonObject data = JsonParser.parseString(json).getAsJsonObject();
            UUID uuid = UUID.fromString(data.get("uuid").getAsString());
            put(uuid, clean(data.get("title").getAsString(),80), clean(data.get("artist").getAsString(),48),
                data.get("platform").getAsInt(), data.get("playing").getAsBoolean(), System.nanoTime());
        } catch (RuntimeException ignored) {}
    }
    private static void put(UUID uuid, String title, String artist, int platform, boolean playing, long now) {
        if (!playing || title.isBlank()) { labels.remove(uuid); return; }
        if (labels.size() >= 512 && !labels.containsKey(uuid)) return;
        int color = switch(platform) { case 1 -> 0x1DB954; case 2 -> 0xFFCC33; case 3 -> 0xFF5500; case 4 -> 0xFF3434; default -> 0xB681D4; };
        Label old = labels.get(uuid);
        if (old != null && old.card.accent() == (0xFF000000 | color) && old.card.title().getString().equals(title) && old.card.artist().getString().equals(artist)) {
            labels.put(uuid, new Label(old.text, old.card, now)); return;
        }
        Card card = new Card(Component.literal(title), Component.literal(artist), switch(platform) {
            case 1 -> "Spotify"; case 2 -> "Яндекс Музыка"; case 3 -> "SoundCloud"; case 4 -> "YouTube Music"; default -> "SpringPlus";
        }, 0xFF000000 | color);
        labels.put(uuid, new Label(Component.literal(title + (artist.isBlank() ? "" : " · " + artist)).withColor(color), card, now));
    }
    public static Component label(UUID owner, UUID self) {
        if (!CosmeticPreferences.musicLabels() || owner == null || owner.equals(self) && !CosmeticPreferences.ownMusicLabel()) return null;
        Label label = labels.get(owner);
        return label == null || System.nanoTime() - label.received > 90_000_000_000L ? null : label.text;
    }
    public static Card card(UUID owner, UUID self) {
        if (label(owner, self) == null) return null;
        return labels.get(owner).card;
    }
    static String clean(String value, int limit) {
        if (value == null) return "";
        String text = value.replaceAll("[\\p{Cntrl}§]", "").strip();
        int end = Math.min(limit, text.length());
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }
    public static void reset() { labels.clear(); last = lastLocal = ""; checked = sent = 0; MenuPresence.reset(); }
}
