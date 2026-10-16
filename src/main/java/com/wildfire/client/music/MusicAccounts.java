package com.wildfire.client.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Account and library gateway. Tokens are memory-only and never sent to game servers. */
public final class MusicAccounts {
    public enum Provider { SPOTIFY, YANDEX }
    public record View(String status, String account, boolean pending, boolean connected) {}
    private static final ScheduledExecutorService WORK = Executors.newScheduledThreadPool(2, r -> {
        Thread thread = new Thread(r, "SpringPlus-Music-Accounts"); thread.setDaemon(true); return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SpringPlus-Music-Timeouts"); t.setDaemon(true); return t;
    });
    private static final Map<Provider, Session> SESSIONS = new EnumMap<>(Provider.class);
    private static final String SPOTIFY_CLIENT_ID = "d55d95a82a30414188259fb988d2022f";
    public static final String REDIRECT = "http://127.0.0.1:43821/spotify/callback";
    // Published by MarshalX/yandex-music-api: credentials of the official Android client,
    // NOT a SpringPlus application. This experimental flow is explained before login.
    private static final String YM_ID = "23cabbbdc6cd418abb4b39c32c41195d";
    private static final String YM_SECRET = "53bc75238f0c4d08a118e51fe9203300";
    private MusicAccounts() {}
    public record Track(String id, String title, String artist, long durationMillis, String url) {}
    public record SearchResult(List<Track> tracks, String message) {}
    /** Explicit searches only: one in-flight request per account and a one-second cooldown. */
    public static void search(Provider provider, String query, Consumer<SearchResult> callback) {
        String text = query.trim();
        final String access;
        final int generation;
        synchronized (MusicAccounts.class) {
            Session s = session(provider);
            if (!s.connected || text.isEmpty() || text.length() > 128) {
                callback.accept(new SearchResult(List.of(), "Сначала подключите аккаунт и введите название / исполнителя")); return;
            }
            if (s.searching || System.currentTimeMillis() < s.searchAfter) {
                callback.accept(new SearchResult(List.of(), "Подождите завершения запроса / ограничения сервиса")); return;
            }
            s.searching = true; s.searchAfter = System.currentTimeMillis() + 1000;
            access = s.access; generation = s.generation;
        }
        WORK.execute(() -> {
            SearchResult result;
            try {
                String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
                JsonObject response = provider == Provider.SPOTIFY
                    ? get("https://api.spotify.com/v1/search?type=track&limit=10&q=" + encoded, "Bearer", access)
                    : get("https://api.music.yandex.net/search?type=track&page=0&text=" + encoded, "OAuth", access);
                List<Track> tracks = parseTracks(provider, response);
                result = new SearchResult(tracks, tracks.isEmpty() ? "Ничего не найдено" : "Найдено: " + tracks.size());
            } catch (ApiFailure error) {
                synchronized (MusicAccounts.class) {
                    if (current(provider, generation) && error.code == 429)
                        session(provider).searchAfter = System.currentTimeMillis() + error.retrySeconds * 1000L;
                }
                result = new SearchResult(List.of(), switch (error.code) {
                    case 401 -> "Вход истёк. Переподключите аккаунт.";
                    case 403 -> "Сервис запретил доступ. Spotify: проверьте Users Management / Premium.";
                    case 429 -> "Лимит сервиса. Повторите через " + error.retrySeconds + " с.";
                    default -> "Сервис недоступен. Повторите позже.";
                });
            } catch (Exception ignored) { result = new SearchResult(List.of(), "Не удалось загрузить поиск. Проверьте сеть."); }
            synchronized (MusicAccounts.class) {
                if (!current(provider, generation)) { callback.accept(new SearchResult(List.of(), "Аккаунт отключён")); return; }
                session(provider).searching = false;
            }
            callback.accept(result);
        });
    }
    static List<Track> parseTracks(Provider provider, JsonObject response) {
        List<Track> result = new ArrayList<>();
        JsonObject root = provider == Provider.YANDEX ? response.getAsJsonObject("result") : response;
        if (root == null || !root.has("tracks") || !root.get("tracks").isJsonObject()) return List.of();
        JsonObject tracks = root.getAsJsonObject("tracks");
        String key = provider == Provider.SPOTIFY ? "items" : "results";
        if (!tracks.has(key) || !tracks.get(key).isJsonArray()) return List.of();
        for (var element : tracks.getAsJsonArray(key)) {
            if (!element.isJsonObject() || result.size() >= 20) continue;
            JsonObject item = element.getAsJsonObject();
            try {
                String id = string(item, "id", "");
                if (!id.matches(provider == Provider.SPOTIFY ? "[A-Za-z0-9]{22}" : "[0-9]{1,20}")) continue;
                List<String> artists = new ArrayList<>();
                if (item.has("artists") && item.get("artists").isJsonArray())
                    for (var artist : item.getAsJsonArray("artists"))
                        if (artist.isJsonObject()) artists.add(string(artist.getAsJsonObject(), "name", ""));
                String durationKey = provider == Provider.SPOTIFY ? "duration_ms" : "durationMs";
                long duration = item.has(durationKey) ? Math.max(0, item.get(durationKey).getAsLong()) : 0;
                // Canonical provider links only. Do not open arbitrary URLs from API data.
                String url = provider == Provider.SPOTIFY ? "https://open.spotify.com/track/" + id
                    : "https://music.yandex.ru/track/" + id;
                result.add(new Track(id, string(item, provider == Provider.SPOTIFY ? "name" : "title", "Трек"),
                    String.join(", ", artists), duration, url));
            } catch (RuntimeException ignored) { /* Skip malformed records; preserve valid results. */ }
        }
        return List.copyOf(result);
    }
    static final class ApiFailure extends java.io.IOException {
        final int code, retrySeconds;
        ApiFailure(int code, int retrySeconds) { this.code = code; this.retrySeconds = retrySeconds; }
    }
    private record Cached(long expires, MusicLibrary.Page page) {}
    @FunctionalInterface interface LibraryWork { MusicLibrary.Page run(Lease lease) throws Exception; }
    /** One provider request pipeline, bounded cache, generation fences and Retry-After. */
    static void library(Provider provider, String key, LibraryWork work, Consumer<MusicLibrary.Page> callback) {
        final int generation;
        synchronized (MusicAccounts.class) { generation = session(provider).generation; }
        libraryAttempt(provider, key, work, callback, generation, System.currentTimeMillis() + 15000, false);
    }
    private static void libraryAttempt(Provider provider, String key, LibraryWork work, Consumer<MusicLibrary.Page> callback,
                                       int generation, long deadline, boolean queued) {
        final Lease lease;
        synchronized (MusicAccounts.class) {
            Session s = session(provider);
            if (!s.connected || s.generation != generation) { callback.accept(new MusicLibrary.Page(List.of(), false, "Сначала подключите аккаунт")); return; }
            Cached cached = s.cache.get(key);
            if (!key.isEmpty() && cached != null && cached.expires > System.currentTimeMillis()) {
                if (queued) s.deferred--;
                callback.accept(cached.page); return;
            }
            if (s.searching || System.currentTimeMillis() < s.searchAfter) {
                long now = System.currentTimeMillis();
                if (now >= deadline || s.searchAfter > deadline || (!queued && s.deferred >= 2)) {
                    if (queued) s.deferred--;
                    callback.accept(new MusicLibrary.Page(List.of(), false, "Действует лимит сервиса / очередь занята. Повторите позже.")); return;
                }
                if (!queued) s.deferred++;
                WORK.schedule(() -> libraryAttempt(provider, key, work, callback, generation, deadline, true),
                    s.searching ? 250 : Math.max(50, s.searchAfter - now), TimeUnit.MILLISECONDS);
                return;
            }
            if (queued) s.deferred--;
            s.searching = true; s.searchAfter = System.currentTimeMillis() + 1000;
            lease = new Lease(provider, s.generation, s.access, s.uid);
        }
        WORK.execute(() -> {
            MusicLibrary.Page result;
            boolean success = false;
            try { result = work.run(lease); success = true; }
            catch (ApiFailure error) {
                synchronized (MusicAccounts.class) {
                    if (current(provider, lease.generation) && error.code == 429)
                        session(provider).searchAfter = System.currentTimeMillis() + error.retrySeconds * 1000L;
                }
                result = new MusicLibrary.Page(List.of(), false, switch (error.code) {
                    case 401 -> "Переподключите аккаунт: вход истёк / не хватает разрешений";
                    case 403 -> provider == Provider.SPOTIFY
                        ? "Spotify запретил доступ: проверьте разрешения. Чужие плейлисты API не открывает."
                        : "Яндекс запретил доступ: проверьте подписку / доступность аккаунта";
                    case 429 -> "Лимит сервиса. Повторите через " + error.retrySeconds + " с.";
                    default -> "Сервис временно недоступен. Попробуйте позднее.";
                });
            } catch (Exception ignored) { result = new MusicLibrary.Page(List.of(), false, "Не удалось загрузить. Проверьте сеть / доступ API."); }
            synchronized (MusicAccounts.class) {
                if (!current(provider, lease.generation)) {
                    callback.accept(new MusicLibrary.Page(List.of(), false, "Аккаунт отключён")); return;
                }
                Session s = session(provider); s.searching = false;
                if (success && !key.isEmpty()) {
                    while (s.cache.size() >= 32) s.cache.remove(s.cache.keySet().iterator().next());
                    s.cache.put(key, new Cached(System.currentTimeMillis() + 60000, result));
                }
            }
            callback.accept(result);
        });
    }
    static final class Lease {
        final Provider provider;
        final int generation;
        final String access, uid;
        Lease(Provider provider, int generation, String access, String uid) {
            this.provider = provider; this.generation = generation; this.access = access; this.uid = uid;
        }
        void invalidate() { synchronized (MusicAccounts.class) { if (current(provider, generation)) session(provider).cache.clear(); } }
        JsonObject get(String path) throws Exception { return send("GET", path, "", false); }
        JsonObject send(String method, String path, String body, boolean json) throws Exception {
            if (!current(provider, generation)) throw new java.io.IOException();
            if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\") || path.contains("#"))
                throw new IllegalArgumentException();
            String base = provider == Provider.SPOTIFY ? "https://api.spotify.com/v1" : "https://api.music.yandex.net";
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(12))
                .header("Authorization", (provider == Provider.SPOTIFY ? "Bearer " : "OAuth ") + access)
                .header("Content-Type", json ? "application/json" : "application/x-www-form-urlencoded")
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<java.io.InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            ScheduledFuture<?> deadline = DEADLINES.schedule(() -> {
                try { response.body().close(); } catch (java.io.IOException ignored) {}
            }, 12, TimeUnit.SECONDS);
            try (var stream = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    int retry = 30;
                    try { retry = Math.clamp(Integer.parseInt(response.headers().firstValue("Retry-After").orElse("30")), 1, 3600); }
                    catch (NumberFormatException ignored) {}
                    throw new ApiFailure(response.statusCode(), retry);
                }
                byte[] bytes = stream.readNBytes(2 * 1024 * 1024 + 1);
                if (bytes.length > 2 * 1024 * 1024 || !current(provider, generation)) throw new java.io.IOException();
                if (bytes.length == 0) return new JsonObject();
                JsonObject result = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                if (result.has("error")) throw new java.io.IOException();
                return result;
            } finally { deadline.cancel(false); }
        }
    }
    private static final class Session {
        int generation;
        String status = "Не подключён", account = "", access = "", refresh = "", uid = "";
        boolean pending, connected;
        boolean searching;
        int deferred;
        long searchAfter;
        HttpServer callback;
        ScheduledFuture<?> timeout;
        final Map<String, Cached> cache = new LinkedHashMap<>();
    }
    private static Session session(Provider provider) { return SESSIONS.computeIfAbsent(provider, p -> new Session()); }
    public static synchronized View view(Provider provider) {
        Session s = session(provider); return new View(s.status, s.account, s.pending, s.connected);
    }
    public static String spotifyId() { return SPOTIFY_CLIENT_ID; }
    public static synchronized void disconnect(Provider provider) {
        if (provider == Provider.YANDEX) PersonalMusicPlayer.stop();
        Session s = session(provider); s.generation++; cleanup(s);
        s.pending = s.connected = false; s.access = s.refresh = s.account = "";
        s.searching = false; s.searchAfter = 0;
        s.uid = ""; s.deferred = 0; s.cache.clear();
        s.status = "Не подключён";
    }
    private static void cleanup(Session s) {
        if (s.callback != null) { s.callback.stop(0); s.callback = null; }
        if (s.timeout != null) { s.timeout.cancel(false); s.timeout = null; }
    }
    private static synchronized boolean current(Provider p, int generation) {
        return session(p).generation == generation;
    }
    private static synchronized void status(Provider p, int generation, String value) {
        if (current(p, generation)) session(p).status = value;
    }
    private static synchronized void fail(Provider p, int generation, String value) {
        if (!current(p, generation)) return;
        Session s = session(p); s.generation++; cleanup(s); s.pending = s.connected = false;
        if (p == Provider.YANDEX) PersonalMusicPlayer.stop();
        s.searching = false; s.searchAfter = 0;
        s.uid = ""; s.deferred = 0; s.cache.clear();
        s.access = s.refresh = s.account = ""; s.status = value;
    }
    public static void connect(Provider provider, Consumer<URI> openBrowser) {
        final int generation;
        synchronized (MusicAccounts.class) {
            Session s = session(provider);
            if (s.pending || s.connected) return;
            if (provider == Provider.SPOTIFY && !spotifyId().matches("[a-fA-F0-9]{32}")) {
                s.status = "Spotify не настроен в этой сборке"; return;
            }
            generation = ++s.generation; s.pending = true; s.status = "Подготовка входа…";
            s.timeout = WORK.schedule(() -> fail(provider, generation, "Время входа истекло"), 5, TimeUnit.MINUTES);
        }
        WORK.execute(() -> {
            try {
                if (provider == Provider.SPOTIFY) startSpotify(generation, openBrowser);
                else startYandex(generation, openBrowser);
            } catch (Exception ignored) { fail(provider, generation, "Не удалось начать вход. Проверьте сеть / доступ API."); }
        });
    }
    static String randomToken() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static String challenge(String verifier) throws NoSuchAlgorithmException {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }
    static Map<String, String> query(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null || raw.length() > 8192) throw new IllegalArgumentException();
        for (String field : raw.split("&")) {
            String[] pair = field.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException();
        }
        return result;
    }
    static boolean stateMatches(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8));
    }
    private static String form(Map<String, String> values) {
        return values.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
            + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    }
    private static JsonObject post(String endpoint, Map<String, String> values) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(12))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(values))).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.body().length() > 262144) throw new java.io.IOException();
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        if (response.statusCode() != 200 && !result.has("error")) throw new java.io.IOException();
        return result;
    }
    private static JsonObject get(String endpoint, String scheme, String access) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(12))
            .header("Authorization", scheme + " " + access).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            int retry = 30;
            try { retry = Math.clamp(Integer.parseInt(response.headers().firstValue("Retry-After").orElse("30")), 1, 3600); }
            catch (NumberFormatException ignored) {}
            throw new ApiFailure(response.statusCode(), retry);
        }
        if (response.body().length() > 262144) throw new java.io.IOException();
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }
    private static void startSpotify(int generation, Consumer<URI> browser) throws Exception {
        String verifier = randomToken(), state = randomToken(), client = spotifyId();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 43821), 4);
        server.createContext("/spotify/callback", exchange -> {
            int code = 400; String message = "Invalid callback. Return to Minecraft.";
            String authorization = null;
            boolean denied = false;
            try {
                Map<String, String> parameters = query(exchange.getRequestURI().getRawQuery());
                if ("GET".equals(exchange.getRequestMethod())
                    && "/spotify/callback".equals(exchange.getRequestURI().getPath())
                    && stateMatches(state, parameters.get("state")) && current(Provider.SPOTIFY, generation)) {
                    if (parameters.containsKey("error")) {
                        denied = true; code = 200; message = "Login cancelled. Return to Minecraft.";
                    } else if (parameters.getOrDefault("code", "").length() > 0) {
                        synchronized (MusicAccounts.class) {
                            Session s = session(Provider.SPOTIFY);
                            if (s.pending && s.status.equals("Подтвердите вход в браузере")) {
                                authorization = parameters.get("code"); s.status = "Проверяем аккаунт…";
                                code = 200; message = "Return to Minecraft. Your account is being checked.";
                            }
                        }
                    }
                }
            } catch (RuntimeException ignored) {}
            byte[] body = message.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(code, body.length); exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
            if (denied) fail(Provider.SPOTIFY, generation, "Вход отменён или отклонён");
            if (authorization != null) {
                String authCode = authorization;
                WORK.execute(() -> {
                    try {
                        JsonObject token = post("https://accounts.spotify.com/api/token", Map.of(
                            "grant_type", "authorization_code", "code", authCode,
                            "redirect_uri", REDIRECT, "client_id", client, "code_verifier", verifier));
                        acceptToken(Provider.SPOTIFY, generation, token, client);
                    } catch (Exception ignored) { fail(Provider.SPOTIFY, generation, "Spotify: вход не завершён / аккаунт не разрешён"); }
                });
            }
        });
        synchronized (MusicAccounts.class) {
            if (!current(Provider.SPOTIFY, generation) || !session(Provider.SPOTIFY).pending) { server.stop(0); return; }
            session(Provider.SPOTIFY).callback = server; server.start();
            status(Provider.SPOTIFY, generation, "Подтвердите вход в браузере");
        }
        try {
            browser.accept(URI.create("https://accounts.spotify.com/authorize?" + form(Map.of(
                "response_type", "code", "client_id", client, "redirect_uri", REDIRECT,
                "code_challenge_method", "S256", "code_challenge", challenge(verifier), "state", state,
                "scope", "user-read-private user-library-read user-library-modify playlist-read-private playlist-read-collaborative"))));
        } catch (RuntimeException ignored) { fail(Provider.SPOTIFY, generation, "Не удалось открыть браузер"); }
    }
    private static void startYandex(int generation, Consumer<URI> browser) throws Exception {
        JsonObject code = post("https://oauth.yandex.ru/device/code", Map.of(
            "client_id", YM_ID, "device_id", randomToken().substring(0, 10), "device_name", "SpringPlus experimental music"));
        if (code.has("error")) throw new java.io.IOException();
        String device = code.get("device_code").getAsString(), user = code.get("user_code").getAsString();
        String url = string(code, "verification_url", string(code, "verification_uri", ""));
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !Set.of("oauth.yandex.ru", "oauth.yandex.com").contains(uri.getHost()))
            throw new java.io.IOException();
        if (!current(Provider.YANDEX, generation) || !view(Provider.YANDEX).pending()) return;
        status(Provider.YANDEX, generation, "Код Яндекса: " + user + " — подтвердите в браузере");
        browser.accept(uri);
        int interval = code.has("interval") ? Math.max(5, code.get("interval").getAsInt()) : 5;
        pollYandex(generation, device, interval);
    }
    private static void pollYandex(int generation, String device, int interval) {
        WORK.schedule(() -> {
            if (!current(Provider.YANDEX, generation) || !view(Provider.YANDEX).pending()) return;
            try {
                JsonObject token = post("https://oauth.yandex.ru/token", Map.of(
                    "grant_type", "device_code", "code", device, "client_id", YM_ID, "client_secret", YM_SECRET));
                String error = string(token, "error", "");
                if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
                    pollYandex(generation, device, "slow_down".equals(error) ? interval + 5 : interval);
                } else if (!error.isEmpty()) fail(Provider.YANDEX, generation, "Яндекс отклонил вход / код истёк");
                else acceptToken(Provider.YANDEX, generation, token, YM_ID);
            } catch (Exception ignored) { fail(Provider.YANDEX, generation, "Не удалось проверить вход Яндекса"); }
        }, interval, TimeUnit.SECONDS);
    }
    private static void acceptToken(Provider provider, int generation, JsonObject token, String client) throws Exception {
        String access = string(token, "access_token", "");
        if (access.isEmpty() || !current(provider, generation) || !view(provider).pending()) throw new java.io.IOException();
        JsonObject profile = provider == Provider.SPOTIFY
            ? get("https://api.spotify.com/v1/me", "Bearer", access)
            : get("https://api.music.yandex.net/account/status", "OAuth", access).getAsJsonObject("result").getAsJsonObject("account");
        String name = string(profile, provider == Provider.SPOTIFY ? "display_name" : "displayName", "");
        if (name.isBlank()) name = string(profile, provider == Provider.SPOTIFY ? "id" : "login", "Аккаунт");
        synchronized (MusicAccounts.class) {
            if (!current(provider, generation) || !session(provider).pending) return;
            Session s = session(provider); cleanup(s); s.pending = false; s.connected = true;
            s.access = access; s.refresh = string(token, "refresh_token", ""); s.account = name;
            s.uid = string(profile, provider == Provider.SPOTIFY ? "id" : "uid", "");
            s.status = "Подключён: " + name;
        }
        if (token.has("expires_in")) {
            long delay = Math.max(30, token.get("expires_in").getAsLong() - 60);
            WORK.schedule(() -> refresh(provider, generation, client), delay, TimeUnit.SECONDS);
        }
    }
    private static void refresh(Provider provider, int generation, String client) {
        String refresh;
        synchronized (MusicAccounts.class) {
            if (!current(provider, generation) || !session(provider).connected) return;
            refresh = session(provider).refresh;
        }
        if (refresh.isEmpty()) { fail(provider, generation, "Сессия истекла. Подключите аккаунт снова."); return; }
        try {
            Map<String, String> values = new HashMap<>(Map.of("grant_type", "refresh_token", "refresh_token", refresh, "client_id", client));
            if (provider == Provider.YANDEX) values.put("client_secret", YM_SECRET);
            JsonObject token = post(provider == Provider.SPOTIFY ? "https://accounts.spotify.com/api/token" : "https://oauth.yandex.ru/token", values);
            synchronized (MusicAccounts.class) {
                if (!current(provider, generation) || !session(provider).connected) return;
                Session s = session(provider);
                String access = string(token, "access_token", "");
                if (access.isEmpty()) throw new java.io.IOException();
                s.access = access; s.refresh = string(token, "refresh_token", refresh);
            }
            if (token.has("expires_in")) WORK.schedule(() -> refresh(provider, generation, client),
                Math.max(30, token.get("expires_in").getAsLong() - 60), TimeUnit.SECONDS);
        } catch (Exception ignored) { fail(provider, generation, "Сессия истекла. Подключите аккаунт снова."); }
    }
}
