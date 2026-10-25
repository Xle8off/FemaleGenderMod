package com.wildfire.client.music;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Provider-owned metadata; no credentials or requests pass through Minecraft servers. */
public final class MusicLibrary {
    public enum Section { SEARCH, FAVORITES, PLAYLISTS, RECOMMENDATIONS, PLAYLIST, QUEUE }
    public record Entry(String id, String owner, String title, String subtitle, String url,
                        MusicAccounts.Track track) {
        public boolean playlist() { return track == null; }
    }
    public record Page(List<Entry> entries, boolean more, String message) {}
    private MusicLibrary() {}
    public static void load(MusicAccounts.Provider provider, Section section, String text, Entry playlist,
                            int page, Consumer<Page> callback) {
        if (page < 0 || page > 100) { callback.accept(new Page(List.of(), false, "Достигнут предел страниц")); return; }
        String key = section + ":" + text + ":" + (playlist == null ? "" : playlist.owner() + ":" + playlist.id()) + ":" + page;
        MusicAccounts.library(provider, key, lease -> {
            JsonObject response;
            boolean spotify = provider == MusicAccounts.Provider.SPOTIFY;
            if (section == Section.SEARCH) {
                String query = text.trim();
                if (query.isEmpty() || query.length() > 128) throw new IllegalArgumentException();
                response = lease.get(spotify ? "/search?type=track&limit=10&offset=" + page * 10 + "&q=" + enc(query)
                    : "/search?type=track&page=" + page + "&text=" + enc(query));
                JsonObject container = object(spotify ? response : object(response, "result"), "tracks");
                JsonArray items = array(container, spotify ? "items" : "results");
                return tracks(provider, items, spotify ? hasNext(container) : items.size() >= 20);
            }
            if (section == Section.FAVORITES) {
                if (spotify) {
                    response = lease.get("/me/tracks?limit=50&offset=" + page * 50);
                    return tracks(provider, array(response, "items"), hasNext(response));
                }
                JsonObject likes = object(object(lease.get("/users/" + uid(lease) + "/likes/tracks"), "result"), "library");
                JsonArray ids = array(likes, "tracks");
                return resolve(lease, ids, page);
            }
            if (section == Section.PLAYLISTS) {
                response = lease.get(spotify ? "/me/playlists?limit=50&offset=" + page * 50
                    : "/users/" + uid(lease) + "/playlists/list");
                JsonArray items = spotify ? array(response, "items") : resultArray(response);
                List<Entry> result = new ArrayList<>();
                int start = spotify ? 0 : page * 50, end = Math.min(items.size(), start + 50);
                for (int i = start; i < end; i++) if (items.get(i).isJsonObject()) {
                    JsonObject item = items.get(i).getAsJsonObject();
                    String id = str(item, spotify ? "id" : "kind");
                    String owner = str(object(item, "owner"), spotify ? "id" : "uid");
                    if (!validId(provider, id) || (!spotify && !owner.matches("[0-9]{1,20}"))) continue;
                    String title = str(item, spotify ? "name" : "title");
                    String url = spotify ? "https://open.spotify.com/playlist/" + id
                        : "https://music.yandex.ru/users/" + enc(str(object(item, "owner"), "login")) + "/playlists/" + id;
                    int count = number(item, "trackCount", number(object(item, "items"), "total", number(object(item, "tracks"), "total", 0)));
                    result.add(new Entry(id, owner, title, count + " треков", url, null));
                }
                return new Page(List.copyOf(result), spotify ? hasNext(response) : end < items.size(), "Плейлисты: " + result.size());
            }
            if (section == Section.RECOMMENDATIONS) {
                if (spotify) return new Page(List.of(), false, "Рекомендации Spotify недоступны этому приложению. Откройте сервис.");
                JsonObject personal = object(lease.get("/playlists/personal/daily"), "result");
                JsonObject item = object(personal, "playlist");
                if (item.size() == 0) item = personal;
                String owner = str(object(item, "owner"), "uid"), id = str(item, "kind");
                if (array(item, "tracks").size() > 0) return resolve(lease, array(item, "tracks"), page);
                if (!owner.matches("[0-9]{1,20}") || !validId(provider, id))
                    return new Page(List.of(), false, "Персональный плейлист пока не сформирован");
                response = lease.get("/users/" + owner + "/playlists/" + id);
                return resolve(lease, array(object(response, "result"), "tracks"), page);
            }
            if (playlist == null || !validId(provider, playlist.id())) throw new IllegalArgumentException();
            if (spotify) {
                response = lease.get("/playlists/" + playlist.id() + "/items?limit=50&offset=" + page * 50);
                return tracks(provider, array(response, "items"), hasNext(response));
            }
            if (!playlist.owner().matches("[0-9]{1,20}")) throw new IllegalArgumentException();
            response = lease.get("/users/" + playlist.owner() + "/playlists/" + playlist.id());
            return resolve(lease, array(object(response, "result"), "tracks"), page);
        }, callback);
    }
    private static Page resolve(MusicAccounts.Lease lease, JsonArray refs, int page) throws Exception {
        int start = page * 50, end = Math.min(refs.size(), start + 50);
        List<String> ids = new ArrayList<>();
        for (int i = start; i < end; i++) {
            JsonObject item = refs.get(i).isJsonObject() ? refs.get(i).getAsJsonObject() : new JsonObject();
            String id = str(item, "id");
            if (id.matches("[0-9]{1,20}")) ids.add(id);
        }
        if (ids.isEmpty()) return new Page(List.of(), false, "Список пуст");
        JsonArray full = resultArray(lease.send("POST", "/tracks", "track-ids=" + enc(String.join(",", ids)), false));
        Map<String, Entry> byId = new HashMap<>();
        for (Entry e : tracks(MusicAccounts.Provider.YANDEX, full, false).entries()) byId.put(e.id(), e);
        List<Entry> ordered = new ArrayList<>();
        for (String id : ids) if (byId.containsKey(id)) ordered.add(byId.get(id));
        return new Page(List.copyOf(ordered), end < refs.size(), "Треки: " + ordered.size());
    }
    public static void favorite(MusicAccounts.Provider provider, MusicAccounts.Track track, boolean save, Consumer<Page> callback) {
        if (!validId(provider, track.id())) { callback.accept(new Page(List.of(), false, "Некорректный трек")); return; }
        MusicAccounts.library(provider, "", lease -> {
            if (provider == MusicAccounts.Provider.SPOTIFY)
                lease.send(save ? "PUT" : "DELETE", "/me/library?uris=" + enc("spotify:track:" + track.id()), "", true);
            else {
                JsonObject response = lease.send("POST", "/users/" + uid(lease) + "/likes/tracks/" + (save ? "add-multiple" : "remove"),
                    "track-ids=" + track.id(), false);
                if (!object(response, "result").has("revision")) throw new java.io.IOException();
            }
            lease.invalidate();
            return new Page(List.of(), false, save ? "Добавлено в любимое" : "Удалено из любимого");
        }, callback);
    }
    static Page tracks(MusicAccounts.Provider provider, JsonArray items, boolean more) {
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : items) {
            if (!element.isJsonObject() || entries.size() >= 50) continue;
            try {
            JsonObject item = element.getAsJsonObject();
            if (item.has("item")) item = object(item, "item");
            else if (item.has("track")) item = object(item, "track");
            if (item.has("type") && !"track".equals(str(item, "type"))) continue;
            if (item.has("is_local") && item.get("is_local").getAsBoolean()) continue;
            String id = str(item, "id");
            if (!validId(provider, id)) continue;
            List<String> artists = new ArrayList<>();
            for (JsonElement artist : array(item, "artists")) if (artist.isJsonObject()) artists.add(str(artist.getAsJsonObject(), "name"));
            String title = str(item, provider == MusicAccounts.Provider.SPOTIFY ? "name" : "title");
            long duration = number(item, provider == MusicAccounts.Provider.SPOTIFY ? "duration_ms" : "durationMs", 0);
            String url = provider == MusicAccounts.Provider.SPOTIFY ? "https://open.spotify.com/track/" + id : "https://music.yandex.ru/track/" + id;
            var track = new MusicAccounts.Track(id, title, String.join(", ", artists), duration, url);
            entries.add(new Entry(id, "", title, track.artist(), url, track));
            } catch (RuntimeException ignored) { /* Malformed item must not discard the page. */ }
        }
        return new Page(List.copyOf(entries), more, entries.isEmpty() ? "Список пуст" : "Треки: " + entries.size());
    }
    static boolean validId(MusicAccounts.Provider provider, String id) {
        return id.matches(provider == MusicAccounts.Provider.SPOTIFY ? "[A-Za-z0-9]{22}" : "[0-9]{1,20}");
    }
    static JsonObject object(JsonObject o, String key) { return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : new JsonObject(); }
    static JsonArray array(JsonObject o, String key) { return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray(); }
    static JsonArray resultArray(JsonObject o) { return array(o, "result"); }
    static String str(JsonObject o, String key) {
        try { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : ""; } catch (RuntimeException ignored) { return ""; }
    }
    static int number(JsonObject o, String key, int fallback) {
        try { return Math.max(0, o.get(key).getAsInt()); } catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean hasNext(JsonObject o) { return o.has("next") && !o.get("next").isJsonNull(); }
    private static String uid(MusicAccounts.Lease lease) { if (!lease.uid.matches("[0-9]{1,20}")) throw new IllegalArgumentException(); return lease.uid; }
    static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
