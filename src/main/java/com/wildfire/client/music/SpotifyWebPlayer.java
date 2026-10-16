package com.wildfire.client.music;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Opt-in experimental remote control of an isolated, normal Spotify Web Player.
 * Does not extract audio, passwords/cookies, suppress ads or impersonate Premium.
 */
public final class SpotifyWebPlayer {
    public record View(boolean running, boolean busy, boolean playing, boolean hidden, String title,
                       String artist, String status, int volume, long position, long duration) {}
    private static final ScheduledExecutorService WORK = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SpringPlus-Spotify-Web"); t.setDaemon(true); return t;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private static volatile View view = new View(false, false, false, false, "", "", "Запустите веб-плеер", 100, 0, 0);
    private static volatile Cdp cdp;
    private static volatile Process process;
    private static volatile int generation;
    private static String session = "", target = "";
    private static String popupTarget = "";
    private static volatile String authHost = "";
    public static String authHost() { return authHost; }
    public static void cancelAuth() {
        command(() -> {
            if (!popupTarget.isEmpty()) {
                JsonObject p = new JsonObject(); p.addProperty("targetId", popupTarget);
                cdp.call("Target.closeTarget", p, "");
                popupTarget = ""; authHost = ""; SpotifyBrowserViewport.returnToMusic();
            }
        });
    }
    private static int pollFailures;
    private static volatile boolean serverActive;
    private static volatile boolean yandexMode;
    private static volatile MusicWebsite website = MusicWebsite.SPOTIFY;
    public static MusicWebsite website() { return website; }
    private static volatile Process loginBrowser;
    private static volatile boolean loginLaunching;
    private static volatile boolean loginWaiting;
    public static boolean externalLoginOpen() {
        Process browser = loginBrowser;
        return loginLaunching || loginWaiting;
    }

    /** User-initiated visible browser login. No CDP, embedding, cookie reads or token transfer. */
    public static synchronized void loginInBrowser(Path gameDirectory, MusicWebsite site) {
        if (!SpringPlusSession.enabled()) return;
        if (externalLoginOpen()) return;
        Process previousBrowser = process;
        stop();
        loginLaunching = true;
        final int ticket = generation;
        WORK.execute(() -> {
            try {
                if (ticket != generation) return;
                if (previousBrowser != null && previousBrowser.isAlive() && !previousBrowser.waitFor(5, TimeUnit.SECONDS))
                    throw new IOException("Previous browser still owns the music profile");
                Path profile = gameDirectory.toAbsolutePath().normalize().resolve("config/springplus/" + site.profile + "-web-profile");
                Files.createDirectories(profile);
                if (waitForProfile(gameDirectory, site, profile, ticket)) return;
                var args = externalLoginArguments(findBrowser(), profile, site);
                loginBrowser = new ProcessBuilder(args).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                loginWaiting = true;
                status("Войдите в браузере, закройте его окно и нажмите «Вернуть плеер»");
                watchExternalLogin(gameDirectory, site, loginBrowser, ticket, false, System.nanoTime());
            } catch (Exception ignored) {
                status("Не удалось открыть браузер для входа. Попробуйте снова.");
            } finally { loginLaunching = false; }
        });
    }

    static List<String> externalLoginArguments(Path browser, Path profile, MusicWebsite site) {
        return List.of(browser.toString(), "--user-data-dir=" + profile, "--no-first-run",
            "--no-default-browser-check", "--disable-background-mode", "--new-window", site.url);
    }

    private static void watchExternalLogin(Path directory, MusicWebsite site, Process browser,
                                          int ticket, boolean seenWindow, long started) {
        WORK.schedule(() -> {
            if (browser != loginBrowser) return;
            try {
                boolean visible = SpotifyBrowserViewport.loginWindowVisible(browser);
                if (visible) {
                    watchExternalLogin(directory, site, browser, ticket, true, started);
                    return;
                }
                if (!seenWindow && browser.isAlive() && System.nanoTime() - started < TimeUnit.SECONDS.toNanos(20)) {
                    watchExternalLogin(directory, site, browser, ticket, false, started);
                    return;
                }
                if (browser.isAlive()) {
                    // Its user-visible windows are already closed; release our private profile.
                    browser.destroy();
                    if (!browser.waitFor(5, TimeUnit.SECONDS)) {
                        loginWaiting = false;
                        status("Браузер входа остаётся в фоне. Завершите его и нажмите «Вернуть плеер»");
                        return;
                    }
                }
                loginBrowser = null; loginWaiting = false;
                if (ticket == generation) start(directory, site);
            } catch (Exception ignored) {
                loginWaiting = false;
                status("Не удалось проверить окно входа. Закройте браузер и нажмите «Вернуть плеер»");
            }
        }, 500, TimeUnit.MILLISECONDS);
    }
    private static boolean resumeAfterServer;
    private static boolean waitForProfile(Path directory, MusicWebsite site, Path profile, int ticket) throws IOException {
        var holders = MusicProfileProcesses.find(profile);
        if (holders.isEmpty()) return false;
        loginWaiting = true;
        status("Закройте отдельное окно " + site.label + ": музыкальный профиль занят. Плеер вернётся автоматически.");
        watchProfile(directory, site, profile, ticket);
        return true;
    }

    private static void watchProfile(Path directory, MusicWebsite site, Path profile, int ticket) {
        WORK.schedule(() -> {
            try {
                if (ticket != generation) { loginWaiting = false; return; }
                var holders = MusicProfileProcesses.find(profile);
                if (SpotifyBrowserViewport.loginWindowVisible(holders)) {
                    watchProfile(directory, site, profile, ticket); return;
                }
                // No user-visible login windows remain. Close only holders of our exact private profile.
                for (var holder : holders) if (holder.isAlive()) holder.destroy();
                if (!MusicProfileProcesses.find(profile).isEmpty()) {
                    watchProfile(directory, site, profile, ticket); return;
                }
                loginBrowser = null; loginWaiting = false;
                start(directory, site);
            } catch (Exception failure) {
                loginWaiting = false;
                System.err.println("SpringPlus music profile handoff failed: " + failure.getClass().getSimpleName());
                status("Не удалось освободить музыкальный профиль. Закройте его окно и нажмите «Вернуть плеер»");
            }
        }, 1000, TimeUnit.MILLISECONDS);
    }
    private static final AtomicInteger queued = new AtomicInteger();
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { generation++; closeOwned(); }, "SpringPlus-Spotify-Shutdown"));
    }
    private SpotifyWebPlayer() {}
    public static View view() { return view; }
    public static boolean yandex() { return website == MusicWebsite.YANDEX; }
    private static void status(String text) {
        View v = view; view = new View(v.running(), false, v.playing(), v.hidden(), v.title(), v.artist(), text, v.volume(), v.position(), v.duration());
    }
    public static synchronized void start(Path gameDirectory) {
        start(gameDirectory, false);
    }
    public static void start(Path gameDirectory, boolean yandex) { start(gameDirectory, yandex ? MusicWebsite.YANDEX : MusicWebsite.SPOTIFY); }
    public static synchronized void start(Path gameDirectory, MusicWebsite site) {
        if (!SpringPlusSession.enabled()) return;
        if (externalLoginOpen()) {
            status("Закройте окно входа, затем нажмите «Вернуть плеер»");
            return;
        }
        if (loginBrowser != null && loginBrowser.isAlive()) {
            status("Браузер входа ещё использует профиль. Завершите его перед возвратом");
            return;
        }
        boolean yandex = site != MusicWebsite.SPOTIFY;
        if ((view.busy() || view.running()) && website != site) {
            stop();
            WORK.execute(() -> start(gameDirectory, site));
            return;
        }
        if (view.busy() || view.running()) { show(true); return; }
        if (!SpotifyBrowserViewport.supported()) { status("Встроенный браузер поддерживает только Windows"); return; }
        yandexMode = yandex;
        website = site;
        PersonalMusicPlayer.stop();
        final int ticket = ++generation;
        view = new View(false, true, false, true, "", "", "Открываем музыку…", 100, 0, 0);
        WORK.execute(() -> {
            String stage = "browser-discovery";
            try {
                Path browser = findBrowser();
                stage = "profile-check";
                Path profile = gameDirectory.toAbsolutePath().normalize().resolve("config/springplus/" + site.profile + "-web-profile");
                Files.createDirectories(profile);
                Path portFile = profile.resolve("DevToolsActivePort");
                if (waitForProfile(gameDirectory, site, profile, ticket)) return;
                Files.deleteIfExists(portFile);
                stage = "process-launch";
                Process child = new ProcessBuilder(browser.toString(), "--user-data-dir=" + profile,
                    "--remote-debugging-address=127.0.0.1", "--remote-debugging-port=0",
                    "--no-first-run", "--no-default-browser-check", "--disable-background-timer-throttling",
                    "--disable-renderer-backgrounding", "--window-position=-10000,-10000", "--window-size=1000,720",
                    "--disable-backgrounding-occluded-windows",
                    "--app=" + site.url).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                process = child;
                stage = "debug-port-wait";
                long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (!Files.isRegularFile(portFile) && System.nanoTime() < end && ticket == generation) Thread.sleep(100);
                if (ticket != generation) { child.destroy(); return; }
                List<String> lines = Files.readAllLines(portFile, StandardCharsets.UTF_8);
                stage = "debug-endpoint";
                URI endpoint = endpoint(lines);
                stage = "debug-connection";
                Cdp connection = new Cdp(endpoint);
                if (ticket != generation) { connection.close(); child.destroy(); return; }
                cdp = connection;
                pollFailures = 0;
                stage = "target-discovery";
                JsonArray targets = connection.call("Target.getTargets", new JsonObject(), "").getAsJsonArray("targetInfos");
                for (JsonElement element : targets) {
                    JsonObject info = element.getAsJsonObject();
                    if ("page".equals(MusicLibrary.str(info, "type")) && site.accepts(MusicLibrary.str(info, "url"))) {
                        target = MusicLibrary.str(info, "targetId"); break;
                    }
                }
                if (target.isEmpty()) throw new IOException("No player tab");
                JsonObject params = new JsonObject(); params.addProperty("targetId", target); params.addProperty("flatten", true);
                stage = "target-attach";
                session = connection.call("Target.attachToTarget", params, "").get("sessionId").getAsString();
                // Keep the isolated player responsive when its native window is hidden.
                try {
                    JsonObject focus = new JsonObject(); focus.addProperty("enabled", true);
                    connection.call("Emulation.setFocusEmulationEnabled", focus, session);
                } catch (Exception unsupported) {
                    System.getLogger(SpotifyWebPlayer.class.getName()).log(System.Logger.Level.DEBUG,
                        "Browser does not support focus emulation");
                }
                if (yandex) {
                    stage = "media-bridge-install";
                    JsonObject script = new JsonObject(); script.addProperty("source", WebMediaBridge.install(site));
                    connection.call("Page.addScriptToEvaluateOnNewDocument", script, session);
                    stage = "page-reload";
                    connection.call("Page.reload", new JsonObject(), session);
                }
                if (ticket != generation) { closeOwned(); return; }
                view = new View(true, false, false, true, "", "", "Войдите / выберите музыку: " + site.label, 100, 0, 0);
                stage = "native-window-attach";
                SpotifyBrowserViewport.attach(child);
                stage = "frame-measurement";
                measureBrowserFrame();
                stage = "window-show";
                setVisible(true);
                poll(ticket);
            } catch (Exception failure) {
                if (ticket != generation) { closeOwned(); return; }
                logFailure(site, stage, failure);
                closeOwned();
                view = new View(false, false, false, false, "", "", "Ошибка плеера на этапе " + stage + ": " + failure.getClass().getSimpleName() + ". Подробности в latest.log.", 100, 0, 0);
            }
        });
    }
    static Path findBrowser() throws IOException {
        List<String> paths = new ArrayList<>(List.of(
            "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
            "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "/usr/bin/google-chrome", "/usr/bin/microsoft-edge", "/usr/bin/chromium",
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"));
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) { paths.add(local + "/Microsoft/Edge/Application/msedge.exe"); paths.add(local + "/Google/Chrome/Application/chrome.exe"); }
        for (String path : paths) if (Files.isRegularFile(Path.of(path))) return Path.of(path);
        throw new IOException("Install supported browser");
    }
    static String diagnosticMessage(String value) {
        if (value == null) return "(no message)";
        String safe = value.replaceAll("(?i)(?:https?|wss?)://[^\\s]+", "[url]")
            .replaceAll("(?i)(?:cookie|authorization|password|token|secret|code)\\s*[:=]\\s*[^\\s,;]+", "[redacted]")
            .replaceAll("[\\p{Cntrl}]", " ");
        return safe.substring(0, Math.min(400, safe.length()));
    }
    private static void logFailure(MusicWebsite site, String stage, Throwable failure) {
        System.err.println("SpringPlus music startup failed provider=" + site.name() + " stage=" + stage);
        for (int cause = 0; failure != null && cause < 4; cause++, failure = failure.getCause()) {
            System.err.println("  " + failure.getClass().getName() + ": " + diagnosticMessage(failure.getMessage()));
            var stack = failure.getStackTrace();
            for (int index = 0; index < Math.min(20, stack.length); index++) System.err.println("    at " + stack[index]);
        }
    }
    static URI endpoint(List<String> lines) throws IOException {
        if (lines.size() < 2 || !lines.get(0).matches("[0-9]{1,5}")
            || !lines.get(1).matches("/devtools/browser/[A-Za-z0-9-]{1,100}")) throw new IOException();
        int port = Integer.parseInt(lines.get(0)); if (port < 1 || port > 65535) throw new IOException();
        return URI.create("ws://127.0.0.1:" + port + lines.get(1));
    }
    static boolean spotifyUrl(String value) {
        try { URI u = URI.create(value); return "https".equals(u.getScheme()) && "open.spotify.com".equals(u.getHost())
            && u.getUserInfo() == null && (u.getPort() == -1 || u.getPort() == 443); }
        catch (RuntimeException ignored) { return false; }
    }
    public static void show(boolean visible) { command(() -> setVisible(visible)); }
    static boolean providerUrl(String value, boolean yandex) {
        if (!yandex) return spotifyUrl(value);
        try { URI u = URI.create(value); return "https".equals(u.getScheme()) && "music.yandex.ru".equals(u.getHost())
            && u.getUserInfo() == null && (u.getPort() == -1 || u.getPort() == 443); }
        catch (RuntimeException ignored) { return false; }
    }
    private static void setVisible(boolean visible) throws Exception {
        if (SpotifyBrowserViewport.supported()) {
            SpotifyBrowserViewport.show(visible);
            View v = view;
            view = new View(v.running(), v.busy(), v.playing(), !visible, v.title(), v.artist(), website.label + " внутри игры", v.volume(), v.position(), v.duration());
            return;
        }
        JsonObject p = new JsonObject(); p.addProperty("targetId", target);
        int id = cdp.call("Browser.getWindowForTarget", p, "").get("windowId").getAsInt();
        p = new JsonObject(); p.addProperty("windowId", id);
        JsonObject bounds = new JsonObject(); bounds.addProperty("windowState", "normal"); p.add("bounds", bounds);
        cdp.call("Browser.setWindowBounds", p, "");
        bounds = new JsonObject(); bounds.addProperty("left", visible ? 100 : -10000); bounds.addProperty("top", visible ? 100 : -10000);
        bounds.addProperty("width", 1000); bounds.addProperty("height", 720); p.add("bounds", bounds);
        cdp.call("Browser.setWindowBounds", p, "");
        View v = view; view = new View(v.running(), v.busy(), v.playing(), !visible, v.title(), v.artist(),
            visible ? "Вход / выбор музыки: " + website.label : website.label + " в фоне", v.volume(), v.position(), v.duration());
    }
    public static void toggle() { command(() -> { resumeAfterServer = false; if (!serverActive) click("playpause"); }); }
    public static void next() { command(() -> click("next")); }
    public static void previous() { command(() -> click("previous")); }
    private static volatile long volumeHoldUntil;
    public static void adjustVolume(int delta) { volume(view.volume() + delta); }
    private static final AtomicInteger wantedVolume = new AtomicInteger(-1);
    private static final java.util.concurrent.atomic.AtomicBoolean volumeQueued = new java.util.concurrent.atomic.AtomicBoolean();
    public static synchronized void volume(int percent) {
        if (!view.running()) return;
        int next = Math.clamp(percent, 0, 100);
        View v = view;
        view = new View(v.running(), v.busy(), v.playing(), v.hidden(), v.title(), v.artist(), v.status(), next, v.position(), v.duration());
        volumeHoldUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        wantedVolume.set(next);
        queueVolume();
    }
    private static void queueVolume() {
        if (!volumeQueued.compareAndSet(false, true)) return;
        final int ticket = generation;
        WORK.schedule(() -> {
            try {
                int value = wantedVolume.getAndSet(-1);
                if (ticket == generation && cdp != null && value >= 0) setVolume(value);
            } catch (Exception ignored) { status("Не удалось изменить громкость на сайте"); }
            finally {
                volumeQueued.set(false);
                if (wantedVolume.get() >= 0) queueVolume();
            }
        }, 50, TimeUnit.MILLISECONDS);
    }
    private static void setVolume(int percent) throws Exception {
        int next = Math.clamp(percent, 0, 100);
        slider("volume", next / 100d);
        // UI already displays the latest intent. An older completed command must
        // never overwrite a newer value entered while the browser was responding.
        volumeHoldUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    }
    static int normalizeVolume(double value, double min, double max, int fallback) {
        if (!Double.isFinite(value) || !Double.isFinite(min) || !Double.isFinite(max) || max <= min) return fallback;
        return Math.clamp((int) Math.round((value - min) * 100 / (max - min)), 0, 100);
    }
    private static volatile long seekHoldUntil;
    private static volatile long seekRevision;
    public static synchronized void seek(double ratio) {
        if (!Double.isFinite(ratio) || !view.running() || view.duration() <= 0) return;
        double next = Math.clamp(ratio, 0d, 1d);
        long request = ++seekRevision;
        View v = view;
        view = new View(v.running(), v.busy(), v.playing(), v.hidden(), v.title(), v.artist(), v.status(),
            v.volume(), Math.round(v.duration() * next), v.duration());
        seekHoldUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        command(() -> {
            if (request != seekRevision) return;
            slider("seek", next);
            if (request == seekRevision) seekHoldUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(750);
        }, () -> {
            if (request == seekRevision) {
                seekHoldUntil = 0;
                status("Не удалось перемотать трек на сайте");
            }
        });
    }
    public static void navigate(String url) {
        if (!spotifyUrl(url)) return;
        command(() -> { JsonObject p = new JsonObject(); p.addProperty("url", url); cdp.call("Page.navigate", p, session); });
    }
    public static void resetLogin() {
        command(() -> {
            cdp.call("Network.clearBrowserCookies", new JsonObject(), session);
            for (String origin : List.of("https://open.spotify.com", "https://accounts.spotify.com")) {
                JsonObject p = new JsonObject(); p.addProperty("origin", origin); p.addProperty("storageTypes", "all");
                cdp.call("Storage.clearDataForOrigin", p, "");
            }
            JsonObject p = new JsonObject(); p.addProperty("url", "https://open.spotify.com/");
            cdp.call("Page.navigate", p, session); setVisible(true);
            status("Вход сброшен в отдельном профиле Spotify. Личный браузер не затронут.");
        });
    }
    public static void search(String text) { if (text != null && !text.isBlank() && text.length() <= 128)
        navigate("https://open.spotify.com/search/" + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")); }
    public static void list(boolean playlists, Consumer<MusicLibrary.Page> callback) {
        command(() -> {
            JsonElement result = evaluate("""
                (() => {
                  if(location.origin !== 'https://open.spotify.com') return [];
                  const out = [], seen = new Set();
                  for(const a of document.querySelectorAll('a[href]')) {
                    const u = new URL(a.href,location.href);
                    const m = u.pathname.match(new RegExp('^/(track|playlist)/([A-Za-z0-9]{22})$'));
                    if(u.origin !== 'https://open.spotify.com' || !m || seen.has(m[2])) continue;
                    const title = (a.innerText || a.getAttribute('aria-label') || '').trim();
                    if(!title) continue;
                    seen.add(m[2]); out.push({id:m[2],title,type:m[1]});
                    if(out.length>=50) break;
                  }
                  return out;
                })()
                """);
            List<MusicLibrary.Entry> entries = new ArrayList<>();
            if (result.isJsonArray()) for (JsonElement el : result.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();
                String id = MusicLibrary.str(item, "id"), title = MusicLibrary.str(item, "title"), type = MusicLibrary.str(item, "type");
                if (!id.matches("[A-Za-z0-9]{22}") || !type.equals(playlists ? "playlist" : "track")) continue;
                String url = "https://open.spotify.com/" + type + "/" + id;
                MusicAccounts.Track track = playlists ? null : new MusicAccounts.Track(id, title, "", 0, url);
                entries.add(new MusicLibrary.Entry(id, "", title, playlists ? "Плейлист Spotify" : "Spotify Web", url, track));
            }
            callback.accept(new MusicLibrary.Page(List.copyOf(entries), false,
                entries.isEmpty() ? "Покажите окно Spotify, войдите / выберите музыку и обновите список" : "Веб-плеер: " + entries.size()));
        }, () -> callback.accept(new MusicLibrary.Page(List.of(), false, "Веб-плеер не отвечает. Покажите его окно.")));
    }
    private static void click(String action) throws Exception {
        if (yandexMode) {
            JsonElement result = evaluate(WebMediaBridge.action(action, view.playing(), website));
            if (!result.isJsonPrimitive() || !result.getAsBoolean()) throw new IOException("Media action unavailable");
            return;
        }
        String test = switch(action) {
            case "playpause" -> "control-button-playpause"; case "next" -> "control-button-skip-forward";
            case "previous" -> "control-button-skip-back"; default -> throw new IllegalArgumentException();
        };
        JsonElement value = evaluate("(() => { if(location.origin!=='https://open.spotify.com') return false; const b=document.querySelector('[data-testid=\"" + test + "\"]'); if(!b || b.disabled) return false; b.click(); return true; })()");
        if (!value.isJsonPrimitive() || !value.getAsBoolean()) throw new IOException("Unavailable control");
    }
    private static void slider(String type, double ratio) throws Exception {
        if (type.equals("seek") && website == MusicWebsite.YANDEX) {
            JsonElement point = evaluate(YandexMediaBridge.seek(ratio));
            if (point.isJsonObject()) { dragWebsiteSlider(point.getAsJsonObject()); return; }
            JsonElement fallback = evaluate(WebMediaBridge.seek(ratio, website));
            if (!fallback.isJsonPrimitive() || !fallback.getAsBoolean()) throw new IOException("Yandex timeline unavailable");
            return;
        }
        if (type.equals("volume") && website == MusicWebsite.YOUTUBE) {
            JsonElement result = evaluate(YouTubeVolumeBridge.set(ratio));
            if (!result.isJsonPrimitive() || !result.getAsBoolean()) throw new IOException("YouTube volume unavailable");
            return;
        }
        if (type.equals("seek") && website == MusicWebsite.SOUNDCLOUD) {
            JsonElement value = evaluate(SoundCloudSeekBridge.set(ratio));
            if (!value.isJsonObject() || !value.getAsJsonObject().has("sent")) throw new IOException("SoundCloud timeline unavailable");
            return;
        }
        if (type.equals("volume") && website == MusicWebsite.SOUNDCLOUD) {
            JsonElement direct = evaluate(SoundCloudVolumeBridge.set(ratio));
            if (direct.isJsonObject() && (direct.getAsJsonObject().has("done") || direct.getAsJsonObject().has("sent"))) return;
            JsonElement hover = evaluate(SoundCloudVolumeBridge.HOVER);
            if (hover.isJsonObject()) {
                JsonObject move = new JsonObject(); move.addProperty("type", "mouseMoved");
                move.addProperty("x", hover.getAsJsonObject().get("x").getAsDouble());
                move.addProperty("y", hover.getAsJsonObject().get("y").getAsDouble());
                cdp.call("Input.dispatchMouseEvent", move, session);
                Thread.sleep(150);
            }
            JsonElement value = evaluate(SoundCloudVolumeBridge.set(ratio));
            if (!value.isJsonObject()) throw new IOException("SoundCloud volume widget unavailable");
            JsonObject point = value.getAsJsonObject();
            if (point.has("open")) {
                JsonObject move = new JsonObject(); move.addProperty("type", "mouseMoved");
                move.addProperty("x", point.get("x").getAsDouble()); move.addProperty("y", point.get("y").getAsDouble());
                cdp.call("Input.dispatchMouseEvent", move, session);
                Thread.sleep(150);
                value = evaluate(SoundCloudVolumeBridge.set(ratio));
                if (!value.isJsonObject()) throw new IOException("SoundCloud volume slider unavailable");
                point = value.getAsJsonObject();
            }
            if (point.has("done") || point.has("sent")) return;
            if (point.has("open") || !point.has("x")) throw new IOException("SoundCloud volume slider not visible");
            dragWebsiteSlider(point);
            return;
        }
        if (type.equals("volume") && website != MusicWebsite.SPOTIFY && website != MusicWebsite.YANDEX) {
            JsonElement result = evaluate(WebMediaBridge.volume(ratio, website));
            if (!result.isJsonPrimitive() || !result.getAsBoolean()) throw new IOException("Media volume unavailable");
            return;
        }
        if (type.equals("volume")) {
            JsonElement result = evaluate(WebVolumeBridge.set(ratio, yandexMode));
            if (!result.isJsonObject()) throw new IOException("Website volume input unavailable");
            JsonObject point = result.getAsJsonObject();
            if (!point.has("x")) return;
            // React/custom sliders often listen to pointer drag rather than a bare click.
            int eventIndex = 0;
            for (String event : List.of("mouseMoved", "mousePressed", "mouseMoved", "mouseReleased")) {
                JsonObject p = new JsonObject(); p.addProperty("type", event);
                p.addProperty("button", event.equals("mouseMoved") ? "none" : "left");
                p.addProperty("buttons", eventIndex == 1 || eventIndex == 2 ? 1 : 0);
                p.addProperty("clickCount", event.equals("mouseMoved") ? 0 : 1);
                p.addProperty("x", point.get("x").getAsDouble()); p.addProperty("y", point.get("y").getAsDouble());
                cdp.call("Input.dispatchMouseEvent", p, session);
                eventIndex++;
            }
            return;
        }
        if (yandexMode) {
            if (type.equals("seek")) {
                JsonElement result = evaluate(WebMediaBridge.seek(ratio, website));
                if (!result.isJsonPrimitive() || !result.getAsBoolean()) throw new IOException("Media seek unavailable");
                return;
            }
            if (!type.equals("volume")) throw new IOException("Seek unavailable");
            JsonElement result = evaluate(WebMediaBridge.volume(ratio));
            if (!result.isJsonPrimitive() || !result.getAsBoolean()) throw new IOException("Media volume unavailable");
            return;
        }
        String selector = type.equals("volume") ? "[data-testid=volume-bar] [data-testid=progress-bar], [data-testid=volume-bar] [role=slider], [data-testid=volume-bar] input[type=range]" : "[data-testid=playback-progressbar]";
        JsonElement result = evaluate("(() => { if(location.origin!=='https://open.spotify.com') return null; const e=document.querySelector('" + selector
            + "'); if(!e) return null; const r=e.getBoundingClientRect(); if(r.width<=0 || r.height<=0) return null; return {x:r.left+r.width*"
            + ratio + ",y:r.top+r.height/2}; })()");
        if (!result.isJsonObject()) throw new IOException("Unavailable slider");
        JsonObject point = result.getAsJsonObject();
        for (String event : List.of("mousePressed", "mouseReleased")) {
            JsonObject p = new JsonObject(); p.addProperty("type", event); p.addProperty("button", "left"); p.addProperty("clickCount", 1);
            p.addProperty("x", point.get("x").getAsDouble()); p.addProperty("y", point.get("y").getAsDouble());
            cdp.call("Input.dispatchMouseEvent", p, session);
        }
    }
    private static void dragWebsiteSlider(JsonObject point) throws Exception {
        int index = 0;
        for (String event : List.of("mouseMoved", "mousePressed", "mouseMoved", "mouseReleased")) {
            JsonObject mouse = new JsonObject(); mouse.addProperty("type", event);
            mouse.addProperty("button", index == 1 || index == 3 ? "left" : "none");
            mouse.addProperty("buttons", index == 1 || index == 2 ? 1 : 0);
            mouse.addProperty("clickCount", index == 1 || index == 3 ? 1 : 0);
            boolean start = index < 2 && point.has("startX") && point.has("startY");
            mouse.addProperty("x", point.get(start ? "startX" : "x").getAsDouble());
            mouse.addProperty("y", point.get(start ? "startY" : "y").getAsDouble());
            cdp.call("Input.dispatchMouseEvent", mouse, session);
            index++;
        }
    }
    private static JsonElement evaluate(String expression) throws Exception {
        JsonObject p = new JsonObject(); p.addProperty("expression", expression); p.addProperty("returnByValue", true);
        p.addProperty("userGesture", true); p.addProperty("timeout", 2000);
        JsonObject result = cdp.call("Runtime.evaluate", p, session);
        if (result.has("exceptionDetails")) throw new IOException();
        JsonObject remote = result.getAsJsonObject("result"); return remote.has("value") ? remote.get("value") : JsonNull.INSTANCE;
    }
    private static void poll(int ticket) {
        WORK.schedule(() -> {
            if (ticket != generation || cdp == null) return;
            try {
                pollAuthPopup();
                measureBrowserFrame();
                JsonElement value = evaluate(yandexMode ? WebMediaBridge.read(website) : """
                    (() => {
                      if(location.origin !== 'https://open.spotify.com') return {login:true};
                      const text=s=>(document.querySelector(s)?.innerText||'').trim();
                      const button=document.querySelector('[data-testid="control-button-playpause"]');
                      const label=(button?.getAttribute('aria-label')||'').toLowerCase();
                      const vol=document.querySelector('[data-testid="volume-bar"] input[type="range"], [data-testid="volume-bar"] [role="slider"]');
                      return {title:text('[data-testid="context-item-info-title"]'),artist:text('[data-testid="context-item-info-artist"]'),
                        playing:/pause|пауза|приостанов|призупин/.test(label),
                        volume:vol?Number(vol.value ?? vol.getAttribute('aria-valuenow')):null,
                        volumeMin:vol?Number(vol.min || vol.getAttribute('aria-valuemin') || 0):0,
                        volumeMax:vol?Number(vol.max || vol.getAttribute('aria-valuemax') || 1):1,
                        position:text('[data-testid="playback-position"]'),duration:text('[data-testid="playback-duration"]')};
                    })()
                    """);
                if (value.isJsonObject()) {
                    synchronized (SpotifyWebPlayer.class) {
                    if (ticket != generation) return;
                    JsonObject s = value.getAsJsonObject(); View v = view;
                    String title = MusicLibrary.str(s, "title");
                    view = new View(true, false, s.has("playing") && s.get("playing").getAsBoolean(), v.hidden(), title,
                        MusicLibrary.str(s, "artist"), title.isEmpty() ? "Войдите / выберите трек на сайте" : website.label,
                        volumeQueued.get() || wantedVolume.get() >= 0 || System.nanoTime() < volumeHoldUntil ? v.volume() : !s.has("volume") || s.get("volume").isJsonNull() ? (yandexMode ? -1 : v.volume()) :
                            normalizeVolume(s.get("volume").getAsDouble(), s.get("volumeMin").getAsDouble(), s.get("volumeMax").getAsDouble(), v.volume()),
                        System.nanoTime() < seekHoldUntil && title.equals(v.title()) ? v.position()
                            : yandexMode ? Math.max(0, s.has("positionMs") ? s.get("positionMs").getAsLong() : 0) : clock(MusicLibrary.str(s,"position")),
                        yandexMode ? Math.max(0, s.has("durationMs") ? s.get("durationMs").getAsLong() : 0) : clock(MusicLibrary.str(s,"duration")));
                    }
                }
                pollFailures = 0;
                if (serverActive && view.playing()) {
                    try { click("playpause"); resumeAfterServer = true; }
                    catch (Exception unavailable) { if (yandexMode) { stop(); status("Веб-плеер остановлен: играет серверная музыка"); return; } throw unavailable; }
                }
            } catch (Exception ignored) {
                if (ticket != generation) return;
                status("Не удалось прочитать плеер. Покажите окно / обновите Spotify.");
                if (++pollFailures >= 3) {
                    closeOwned();
                    view = new View(false, false, false, false, "", "", "Связь с браузером потеряна. Запустите плеер снова.", 100, 0, 0);
                    return;
                }
            }
            poll(ticket);
        }, 1500, TimeUnit.MILLISECONDS);
    }
    private static void measureBrowserFrame() {
        try {
            JsonElement size = evaluate("window.innerHeight * window.devicePixelRatio");
            if (size.isJsonPrimitive() && size.getAsJsonPrimitive().isNumber()) SpotifyBrowserViewport.measure(size.getAsDouble());
        } catch (Exception ignored) { /* Decoration measurement must not stop music or login. */ }
    }
    static boolean ownedPopup(JsonObject info, String opener) {
        return "page".equals(MusicLibrary.str(info, "type")) && !opener.isBlank()
            && opener.equals(MusicLibrary.str(info, "openerId"));
    }
    private static void pollAuthPopup() {
        try {
            JsonArray targets = cdp.call("Target.getTargets", new JsonObject(), "").getAsJsonArray("targetInfos");
            boolean present = false;
            for (JsonElement element : targets) {
                JsonObject info = element.getAsJsonObject();
                String id = MusicLibrary.str(info, "targetId");
                if (!popupTarget.isEmpty() && popupTarget.equals(id)) {
                    present = true; authHost = hostOnly(MusicLibrary.str(info, "url"));
                }
            }
            if (!popupTarget.isEmpty() && !present) {
                popupTarget = ""; authHost = ""; SpotifyBrowserViewport.returnToMusic();
            }
            if (popupTarget.isEmpty()) for (JsonElement element : targets) {
                JsonObject info = element.getAsJsonObject();
                if (!ownedPopup(info, target)) continue;
                JsonObject mainParams = new JsonObject(); mainParams.addProperty("targetId", target);
                JsonObject popupParams = new JsonObject(); popupParams.addProperty("targetId", MusicLibrary.str(info, "targetId"));
                boolean sameWindow = cdp.call("Browser.getWindowForTarget", mainParams, "").get("windowId").getAsInt()
                    == cdp.call("Browser.getWindowForTarget", popupParams, "").get("windowId").getAsInt();
                // Only page targets opened by our music tab, never arbitrary personal browser windows.
                if (sameWindow || SpotifyBrowserViewport.popup(process, MusicLibrary.str(info, "title"))) {
                    popupTarget = MusicLibrary.str(info, "targetId");
                    authHost = hostOnly(MusicLibrary.str(info, "url")); break;
                }
            }
        } catch (Exception ignored) { /* Temporary popup discovery failures must not stop music. */ }
    }
    static String hostOnly(String value) {
        try { String host = URI.create(value).getHost(); return host == null ? "Авторизация" : host; }
        catch (RuntimeException ignored) { return "Авторизация"; }
    }
    static long clock(String text) {
        try {
            String[] parts = text.trim().split(":"); if (parts.length < 2 || parts.length > 3) return 0;
            long seconds = 0; for (String part : parts) { long n = Long.parseLong(part); if (n < 0 || n > 999) return 0; seconds = seconds * 60 + n; }
            return seconds * 1000;
        } catch (RuntimeException ignored) { return 0; }
    }
    public static void serverPlaying(boolean active) {
        if (serverActive == active) return;
        serverActive = active;
        if (!view.running()) return;
        command(() -> {
            if (active && view.playing()) { click("playpause"); resumeAfterServer = true; }
            else if (!active && resumeAfterServer) { resumeAfterServer = false; if (!view.playing()) click("playpause"); }
        });
    }
    public static synchronized void stop() {
        seekRevision++; seekHoldUntil = 0;
        wantedVolume.set(-1);
        generation++; view = new View(false, false, false, false, "", "", "Плеер остановлен; вход сохранён в отдельном профиле", 100, 0, 0);
        WORK.execute(SpotifyWebPlayer::closeOwned);
    }
    /** Cancel playback, pending startup/login and resume-on-server-stop on world disconnect. */
    public static synchronized void disconnect() {
        stop();
        serverActive = false; resumeAfterServer = false;
        loginLaunching = loginWaiting = false;
        Process login = loginBrowser; loginBrowser = null;
        if (login != null && login.isAlive()) login.destroy();
        // Stop owned audio immediately, without waiting behind a pending CDP request.
        Process browser = process;
        if (browser != null && browser.isAlive()) browser.destroy();
    }
    private static void closeOwned() {
        SpotifyBrowserViewport.clear();
        Cdp old = cdp; cdp = null;
        if (old != null) { try { old.call("Browser.close", new JsonObject(), ""); } catch (Exception ignored) {} old.close(); }
        Process child = process; process = null; if (child != null && child.isAlive()) child.destroy();
        session = target = popupTarget = ""; authHost = ""; resumeAfterServer = false;
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void command(Action action) { command(action, () -> status("Действие недоступно. Откройте окно Spotify; Free может ограничивать управление.")); }
    private static void command(Action action, Runnable failure) {
        if (!view.running()) { failure.run(); return; }
        if (queued.incrementAndGet() > 4) { queued.decrementAndGet(); failure.run(); return; }
        final int ticket = generation;
        WORK.execute(() -> {
            try { if (ticket != generation || cdp == null) { failure.run(); return; } action.run(); }
            catch (Exception ignored) { failure.run(); }
            finally { queued.decrementAndGet(); }
        });
    }
    private static final class Cdp implements WebSocket.Listener {
        private final AtomicInteger ids = new AtomicInteger();
        private final ConcurrentHashMap<Integer, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
        private final StringBuilder message = new StringBuilder();
        private final WebSocket socket;
        Cdp(URI uri) throws Exception { socket = HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(3)).buildAsync(uri, this).get(5, TimeUnit.SECONDS); }
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence text, boolean last) {
            if (message.length() + text.length() > 262144) { ws.abort(); fail(); return CompletableFuture.completedFuture(null); }
            message.append(text);
            if (last) {
                try {
                    JsonObject reply = JsonParser.parseString(message.toString()).getAsJsonObject();
                    if (reply.has("id")) { CompletableFuture<JsonObject> future = pending.remove(reply.get("id").getAsInt()); if (future != null) future.complete(reply); }
                } catch (RuntimeException ignored) {} finally { message.setLength(0); }
            }
            ws.request(1); return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket ws, Throwable error) { fail(); }
        @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) { fail(); return CompletableFuture.completedFuture(null); }
        private void fail() { pending.forEach((id, f) -> f.completeExceptionally(new IOException())); pending.clear(); }
        JsonObject call(String method, JsonObject params, String session) throws Exception {
            int id = ids.incrementAndGet(); JsonObject request = new JsonObject(); request.addProperty("id", id);
            request.addProperty("method", method); request.add("params", params);
            if (!session.isEmpty()) request.addProperty("sessionId", session);
            CompletableFuture<JsonObject> future = new CompletableFuture<>(); pending.put(id, future);
            try {
                socket.sendText(request.toString(), true).get(3, TimeUnit.SECONDS);
                JsonObject response = future.get(5, TimeUnit.SECONDS);
                if (response.has("error")) {
                    var error = response.getAsJsonObject("error");
                    int code = error.has("code") ? error.get("code").getAsInt() : 0;
                    throw new IOException("CDP command failed method=" + method + " errorCode=" + code);
                }
                return MusicLibrary.object(response, "result");
            } finally { pending.remove(id); }
        }
        void close() { socket.abort(); fail(); }
    }
}
