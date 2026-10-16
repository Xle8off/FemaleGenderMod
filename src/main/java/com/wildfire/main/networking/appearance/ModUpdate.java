package com.wildfire.main.networking.appearance;

import com.google.gson.JsonParser;
import com.wildfire.gui.RoundedPanels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Checks the trusted release endpoint at login and while the client remains online. */
public final class ModUpdate {
    private static final String BASE = "https://skin.springplus.net/api/mod-release/fabric/26.2";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile boolean pending;
    private static volatile boolean declined;
    private static volatile boolean accepted;
    private static volatile int generation;
    private static volatile boolean backgroundChecking;
    private static volatile String stagedHash;
    private static volatile String stagedVersion;
    private static volatile boolean installerStarted;
    private static volatile Process installerProcess;
    private static volatile int updateHudState;
    private static volatile String manualDownloadUrl;
    // Never hold the HUD/toast class monitor across network or filesystem I/O.
    private static final Object stagingLock = new Object();

    /** 0 hidden, 1 downloading, 2 verified and installer waiting, 3 manual download required. */
    public static int updateHudState() { return updateHudState; }
    public static void activateUpdateHud() {
        if (updateHudState == 2 && installerStarted && stagedHash != null
            && installerProcess != null && installerProcess.isAlive()) {
            Minecraft.getInstance().stop();
        } else if (updateHudState == 2 || updateHudState == 3) {
            updateHudState = 3;
            String url = manualDownloadUrl;
            if (url != null && url.matches(java.util.regex.Pattern.quote(BASE) + "/[a-f0-9]{64}\\.jar"))
                net.minecraft.util.Util.getPlatform().openUri(url);
        }
    }
    private static volatile boolean legacyBackupCleanupStarted;
    private static volatile long lastBackgroundCheck;
    private static volatile String lastRecordedFeedback = "";
    private static volatile long lastRecordedAt;
    private static volatile float downloadProgress = -1f;
    private static volatile long feedbackStarted;
    private static volatile long musicPopupUntil;
    public record PopupAction(String kind, String target) {}
    private record Toast(long id, String message, String sender, float progress, PopupAction action, long started, long until) {}
    private static final List<Toast> toasts = new ArrayList<>();
    private static long nextToastId;
    private static final com.wildfire.gui.ToastControlsReveal controlsReveal=new com.wildfire.gui.ToastControlsReveal();
    public static double popupControlsReveal(){
        return controlsReveal.update(Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen,System.nanoTime());
    }
    public static int popupTop(int slot){
        var list=visibleToasts();
        return slot>=0&&slot<list.size()?toastY(list,slot,System.nanoTime()):-100;
    }
    private static synchronized List<Toast> visibleToasts() {
        long now = System.nanoTime();
        toasts.removeIf(toast -> now >= toast.until());
        if(!com.wildfire.client.config.CosmeticPreferences.notificationPopups())toasts.removeIf(toast->toast.action()!=null);
        return List.copyOf(toasts);
    }
    private static synchronized void pushToast(String message, String sender, float progress, PopupAction action, long seconds) {
        if(action!=null&&!com.wildfire.client.config.CosmeticPreferences.notificationPopups())return;
        visibleToasts();
        long now = System.nanoTime();
        long until = now + Duration.ofSeconds(Math.max(1, seconds)).toNanos();
        if (!toasts.isEmpty()) {
            var previous = toasts.getLast();
            if (action == null && sender.isEmpty() && previous.action() == null && previous.sender().isEmpty()
                && (message.equals(previous.message()) || progress >= 0 && previous.progress() >= 0)) {
                toasts.set(toasts.size() - 1, new Toast(previous.id(), message, sender, progress, action, previous.started(), until));
                return;
            }
        }
        if (toasts.size() == 3) toasts.removeFirst();
        toasts.add(new Toast(++nextToastId, message, sender, progress, action, now, until));
    }
    public static PopupAction popupAction(int slot) {
        var list = visibleToasts();
        return slot >= 0 && slot < list.size() ? list.get(slot).action() : null;
    }
    public static long popupId(int slot) {
        var list = visibleToasts();
        return slot >= 0 && slot < list.size() ? list.get(slot).id() : -1;
    }
    public static synchronized void dismissPopup(long id) { toasts.removeIf(toast -> toast.id() == id); }
    private static int toastHeight(Toast toast) { return com.wildfire.gui.ToastControlsReveal.height(toast.action()!=null,popupControlsReveal()); }
    private static int toastY(List<Toast> list, int slot, long now) {
        int y = topInset();
        for (int index = 0; index < slot; index++) y += toastHeight(list.get(index)) + 5;
        var toast = list.get(slot);
        double entering = Math.clamp((double)(now - toast.started()) / SLIDE_NANOS, 0d, 1d);
        double leaving = Math.clamp((double)(toast.until() - now) / SLIDE_NANOS, 0d, 1d);
        return y + (int)Math.round(18 * Math.pow(1 - entering, 3) - toastHeight(toast) * Math.pow(1 - leaving, 3));
    }
    public static int popupActionY(int slot) {
        var list = visibleToasts();
        return slot >= 0 && slot < list.size() && list.get(slot).action() != null
            ? toastY(list, slot, System.nanoTime()) + 40 : -100;
    }
    private static volatile long lastOverlayCheck;
    private static volatile int overlayTopInset;
    private static final long SLIDE_NANOS = Duration.ofMillis(280).toNanos();
    private static volatile boolean awaitingAppearanceSave;

    public static void appearanceSavePending() { awaitingAppearanceSave = true; cosmeticFeedback("Сохраняем образ на сервере…", -1f, 12); }
    public static void appearanceUpdated(java.util.UUID player) {
        var self = Minecraft.getInstance().player;
        if (awaitingAppearanceSave && self != null && self.getUUID().equals(player)) {
            awaitingAppearanceSave = false;
            cosmeticFeedback("Образ сохранён", -1f, 4);
        }
    }

    private static void feedback(String message, float progress, long seconds) {
        recordFeedback(message, progress);
        showFeedback(message, progress, seconds);
    }

    private static void recordFeedback(String message, float progress) {
        long now = System.nanoTime();
        if ((progress < 0f || progress >= 1f) && (!message.equals(lastRecordedFeedback) || now - lastRecordedAt > 2_000_000_000L) && !message.contains("%")) {
            lastRecordedFeedback = message;
            lastRecordedAt = now;
            NotificationInbox.add("Система", "SpringPlusCosmetics", message);
        }
    }

    private static void showFeedback(String message, float progress, long seconds) {
        pushToast(message, "", progress, null, seconds);
        downloadProgress = progress;
    }

    public static void cosmeticFeedback(String message, float progress, long seconds) {
        recordFeedback(message, progress);
        transientFeedback(message, progress, seconds);
    }

    public static void transientFeedback(String message, float progress, long seconds) {
        showFeedback(message.length() > 36 ? message.substring(0, 35) + "…" : message, progress, seconds);
    }

    public static void notificationFeedback(String category, String title, String message) {
        String target = null;
        String kind = null;
        if ("ЛС".equals(category) && title.matches("[A-Za-z0-9_]{1,16}")) {
            target = title;
            kind = "message";
        } else if ("Друзья".equals(category)) {
            String candidate = NotificationInbox.sender(category, title, message);
            if (candidate.matches("[A-Za-z0-9_]{1,16}")) {
                target = candidate;
                kind = "friend_message";
            }
        }
        if (kind == null) { kind = "notification"; target = category + "\u0000" + title + "\u0000" + message; }
        String text = title + ": " + message;
        pushToast(text.length() > 36 ? text.substring(0, 35) + "…" : text,
            NotificationInbox.sender(category, title, message), -1f,
            kind == null ? null : new PopupAction(kind, target), kind == null ? 7 : 12);
    }

    public static PopupAction popupAction() {
        return popupAction(0);
    }

    public static void dismissPopupAction() {
        dismissPopup(popupId(0));
    }

    public static int topInset() {
        long now = System.currentTimeMillis();
        if (now - lastOverlayCheck < 2_000L) return overlayTopInset;
        lastOverlayCheck = now;
        try {
            Path config = Minecraft.getInstance().gameDirectory.toPath().resolve("config/sodium-extra-options.json");
            var extra = JsonParser.parseString(Files.readString(config)).getAsJsonObject().getAsJsonObject("extra_settings");
            if (extra != null && "TOP_LEFT".equals(extra.get("overlay_corner").getAsString())) {
                boolean coords = extra.has("show_coords") && extra.get("show_coords").getAsBoolean();
                overlayTopInset = coords ? 12 : 0;
            } else overlayTopInset = 0;
        } catch (Exception ignored) { overlayTopInset = 0; }
        return overlayTopInset;
    }

    public static void musicFeedback() {
        musicPopupUntil = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        feedbackStarted = System.nanoTime();
    }

    public static boolean popupVisible() {
        long now = System.nanoTime();
        return (now < musicPopupUntil && MusicClient.active()) || !visibleToasts().isEmpty();
    }

    public static void renderHud(GuiGraphicsExtractor graphics) {
        com.wildfire.gui.HudReveal.observe();
        var list = visibleToasts();
        long timestamp = System.nanoTime();
        for (int slot = 0; slot < list.size(); slot++)
            renderToast(graphics, list.get(slot), toastY(list, slot, timestamp));
        if (!list.isEmpty()) return;
        renderLegacyHud(graphics);
    }
    private static void renderToast(GuiGraphicsExtractor graphics, Toast toast, int y) {
        int width = Math.min(232, graphics.guiWidth());
        if (width < 100) return;
        renderToastBody(graphics, toast.message(), toast.sender(), toast.progress(), width, y, toastHeight(toast));
    }
    private static void renderLegacyHud(GuiGraphicsExtractor graphics) {
        long now = System.nanoTime();
        if (now < musicPopupUntil && MusicClient.active()) {
            renderMusicPopup(graphics, now);
        }
    }
    private static void renderToastBody(GuiGraphicsExtractor graphics, String message, String sender, float downloadProgress, int width, int y, int height) {
        RoundedPanels.fill(graphics, 0, y, width, y + height, com.wildfire.gui.UiTheme.BACKGROUND, 6);
        RoundedPanels.fillLeftBar(graphics, 0, y, 3, y + height, com.wildfire.gui.UiTheme.ACCENT);
        int inset = sender.isEmpty() ? 10 : 36;
        if (!sender.isEmpty()) com.wildfire.gui.screen.NotificationCenterScreen.renderNotificationAvatar(graphics, sender, 10, y + 9, 20);
        var font = Minecraft.getInstance().font;
        graphics.text(font, font.plainSubstrByWidth(sender.isEmpty() ? "SpringPlusCosmetics" : NotificationInbox.playerIdentity(sender), width - inset - 8-(int)Math.round(18*popupControlsReveal())), inset, y + 6, com.wildfire.gui.UiTheme.TEXT, false);
        graphics.text(font, font.plainSubstrByWidth(message, width - inset - 8), inset, y + 19, com.wildfire.gui.UiTheme.MUTED, false);
        if (downloadProgress >= 0f) {
            graphics.fill(10, y + 34, width - 10, y + 37, 0xFF343A43);
            graphics.fill(10, y + 34, 10 + (int)((width - 20) * downloadProgress), y + 37, com.wildfire.gui.UiTheme.ACCENT);
        }
    }

    public static int popupActionY() {
        return popupActionY(0);
    }

    private static void renderMusicPopup(GuiGraphicsExtractor graphics, long now) {
        int width = Math.min(248, graphics.guiWidth());
        if (width < 110) return;
        double entering = Math.clamp((double) (now - feedbackStarted) / SLIDE_NANOS, 0d, 1d);
        double leaving = Math.clamp((double) (musicPopupUntil - now) / SLIDE_NANOS, 0d, 1d);
        int y = topInset() - 55 + (int) Math.round(55 * Math.min(entering, leaving));
        RoundedPanels.fillLeft(graphics, 0, y, width, y + 55, 0xEE171B22, 6);
        graphics.fill(width - 3, y, width, y + 55, com.wildfire.gui.UiTheme.ACCENT);
        graphics.item(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_13), 9, y + 11);
        var font = Minecraft.getInstance().font;
        graphics.text(font, "СЕЙЧАС ИГРАЕТ · " + MusicClient.timeLabel(), 33, y + 8, 0xFFB9A5D8, false);
        drawScrollingTitle(graphics, MusicClient.title(), 33, y + 22, width - 43);
        float progress = MusicClient.progress();
        graphics.fill(10, y + 45, width - 10, y + 48, 0xFF343A43);
        if (progress >= 0) graphics.fill(10, y + 45, 10 + (int) ((width - 20) * progress), y + 48, 0xFFCE8AFF);
    }

    public static void drawScrollingTitle(GuiGraphicsExtractor graphics, String title, int x, int y, int maxWidth) {
        var font = Minecraft.getInstance().font;
        if (font.width(title) <= maxWidth) {
            graphics.text(font, title, x, y, com.wildfire.gui.UiTheme.TEXT, false);
            return;
        }
        String ticker = title + "   ·   ";
        int offset = (int) ((System.currentTimeMillis() / 180L) % ticker.length());
        String loop = ticker.substring(offset) + ticker.substring(0, offset) + ticker;
        graphics.text(font, font.plainSubstrByWidth(loop, maxWidth), x, y, com.wildfire.gui.UiTheme.TEXT, false);
    }

    private ModUpdate() {}

    public static void reset() { generation++; pending = false; declined = false; accepted = false; }
    public static boolean isDeclined() { return declined; }

    private static boolean compatible(int protocol) { return protocol == AppearancePackets.VERSION; }

    public static void check(int protocol, Runnable accept, Runnable decline) {
        if (declined) return;
        if (compatible(protocol)) {
            accepted = true;
            accept.run();
            backgroundPoll();
            return;
        }
        accepted = false;
        if (pending) return;
        pending = true;
        feedback("Проверка обновления…", -1f, 15);
        int requestGeneration = generation;
        CompletableFuture.supplyAsync(() -> {
            try {
                var request = HttpRequest.newBuilder(URI.create(BASE)).timeout(Duration.ofSeconds(12)).GET().build();
                var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) return null;
                if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                var json = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("release");
                String version = json.get("version").getAsString();
                String sha = json.get("sha256").getAsString();
                String url = json.get("url").getAsString();
                String changelog = json.get("changelog").getAsString();
                if (!sha.matches("[a-f0-9]{64}") || !url.equals(BASE + "/" + sha + ".jar")) throw new IOException("Недоверенная ссылка обновления");
                if (compareVersions(version, currentVersion()) < 0) return null;
                if (installedHash().equals(sha)) return null;
                return new Release(version, sha, url, changelog);
            } catch (Exception error) { throw new RuntimeException(error); }
        }).whenComplete((release, error) -> Minecraft.getInstance().schedule(() -> {
            if (!pending || requestGeneration != generation) return;
            if (error != null) {
                feedback("Не удалось проверить обновление", -1f, 8);
                System.err.println("SpringPlusCosmetics update check failed: endpoint=" + BASE
                    + " client=" + currentVersion() + " serverProtocol=" + protocol + " cause=" + error);
                // An existing compatible protocol may continue when the site is temporarily unavailable.
                if (compatible(protocol)) { pending = false; accepted = true; accept.run(); }
                else Minecraft.getInstance().gui.setScreen(new Prompt(null, "Не удалось проверить обновление", error.getMessage(), decline,
                    () -> { pending = false; check(protocol, accept, decline); }));
            } else if (release == null && compatible(protocol)) {
                feedback("Версия мода актуальна", -1f, 5);
                pending = false;
                accepted = true;
                accept.run();
            } else {
                feedback(release == null ? "Обновление недоступно" : "Обновление " + release.version + " найдено", -1f, 8);
                if (release == null) System.err.println("SpringPlusCosmetics: no applicable update at " + BASE
                    + " (client=" + currentVersion() + ", serverProtocol=" + protocol + ")");
                Minecraft.getInstance().gui.setScreen(new Prompt(release, "Доступно обновление мода", release == null ? "Версия протокола несовместима" : release.changelog, decline,
                    () -> { pending = false; check(protocol, accept, decline); }));
            }
        }));
    }

    /** Compatible clients keep playing while a verified JAR is staged for the next restart. */
    public static void tick() {
        if (accepted && Minecraft.getInstance().player != null) backgroundPoll();
    }

    private static void backgroundPoll() {
        cleanupLegacyBackups();
        long now = System.nanoTime();
        if (backgroundChecking || now - lastBackgroundCheck < 300_000_000_000L) return;
        lastBackgroundCheck = now;
        backgroundChecking = true;
        CompletableFuture.supplyAsync(() -> {
            try {
                var request = HttpRequest.newBuilder(URI.create(BASE)).timeout(Duration.ofSeconds(6)).GET().build();
                var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) return null;
                if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                var json = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("release");
                String version = json.get("version").getAsString();
                String sha = json.get("sha256").getAsString();
                String url = json.get("url").getAsString();
                if (!sha.matches("[a-f0-9]{64}") || !url.equals(BASE + "/" + sha + ".jar")) throw new IOException("Untrusted update URL");
                if (compareVersions(version, currentVersion()) <= 0 || installedHash().equals(sha)
                    || sha.equals(stagedHash) || stagedVersion != null && compareVersions(version, stagedVersion) <= 0) return null;
                return new Release(version, sha, url, json.get("changelog").getAsString());
            } catch (Exception error) { throw new RuntimeException(error); }
        }).thenAcceptAsync(release -> {
            if (release == null) return;
            try { stage(release); feedback("Обновление готово. Перезапустите игру", 1f, 12); }
            catch (Exception error) { throw new RuntimeException(error); }
            System.out.println("SpringPlusCosmetics " + release.version
                + " downloaded in background; update will install after game exit.");
        }).whenComplete((ignored,error) -> {
            backgroundChecking = false;
            if (error != null) { feedback("Ошибка загрузки обновления", -1f, 8); System.err.println("SpringPlusCosmetics background update failed: " + error); }
        });
    }

    private static void cleanupLegacyBackups() {
        if (legacyBackupCleanupStarted) return;
        legacyBackupCleanupStarted = true;
        CompletableFuture.runAsync(() -> {
            try {
                Path current = Path.of(ModUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                if (!Files.isRegularFile(current) || !current.getFileName().toString().startsWith("SpringPlusCosmetics-fabric-")) return;
                try (var files = Files.list(current.getParent())) {
                    files.filter(file -> file.getFileName().toString().matches(
                        "SpringPlusCosmetics-fabric-[0-9]+\\.[0-9]+\\.[0-9]+-beta\\.[0-9]+\\+26\\.2\\.jar\\.previous"))
                        .forEach(file -> {
                            try { Files.deleteIfExists(file); }
                            catch (IOException error) { System.err.println("SpringPlusCosmetics could not remove old backup " + file + ": " + error); }
                        });
                }
            } catch (Exception error) { System.err.println("SpringPlusCosmetics backup cleanup failed: " + error); }
        });
    }

    private record Release(String version, String sha, String url, String changelog) {}

    private static String currentVersion() {
        try { return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("wildfire_gender").orElseThrow().getMetadata().getVersion().getFriendlyString().split("\\+")[0]; }
        catch (Exception ignored) { return "0.0.0"; }
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("-", 2), b = right.split("-", 2);
        String[] an = a[0].split("\\."), bn = b[0].split("\\.");
        for (int i = 0; i < 3; i++) {
            int result = Integer.compare(Integer.parseInt(an[i]), Integer.parseInt(bn[i]));
            if (result != 0) return result;
        }
        if (a.length == 1) return b.length == 1 ? 0 : 1;
        if (b.length == 1) return -1;
        String[] ap = a[1].split("\\."), bp = b[1].split("\\.");
        for (int i = 0; i < Math.min(ap.length, bp.length); i++) {
            boolean ai = ap[i].matches("[0-9]+"), bi = bp[i].matches("[0-9]+");
            int result = ai && bi ? Integer.compare(Integer.parseInt(ap[i]), Integer.parseInt(bp[i]))
                : ai != bi ? (ai ? -1 : 1) : ap[i].compareTo(bp[i]);
            if (result != 0) return result;
        }
        return Integer.compare(ap.length, bp.length);
    }

    private static String installedHash() {
        try {
            Path jar = Path.of(ModUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(jar)) return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        } catch (Exception ignored) {}
        return "";
    }

    private static final class Prompt extends Screen {
        private final Release release;
        private final String heading, detail;
        private final Runnable decline, retry;
        private final List<String> lines = new ArrayList<>();
        private int scroll;
        private String status = "";

        Prompt(Release release, String heading, String detail, Runnable decline, Runnable retry) {
            super(Component.literal("SpringPlusCosmetics"));
            this.release = release; this.heading = heading; this.detail = detail; this.decline = decline; this.retry = retry;
            for (String paragraph : detail.split("\\R")) {
                String remaining = paragraph;
                while (remaining.length() > 80) {
                    int end = remaining.lastIndexOf(' ', 80);
                    if (end < 20) end = 80;
                    lines.add(remaining.substring(0, end));
                    remaining = remaining.substring(end).stripLeading();
                }
                lines.add(remaining);
            }
        }

        @Override protected void init() {
            int x = width / 2, y = height / 2;
            if (release != null) addRenderableWidget(com.wildfire.gui.FlatButtons.builder(Component.literal("Да, обновить"), button -> {
                button.active = false;
                status = "Загрузка и проверка файла…";
                CompletableFuture.runAsync(() -> {
                    try { stage(release); Minecraft.getInstance().schedule(() -> { status = "Обновление готово. Перезапустите игру для установки."; feedback("Обновление готово. Перезапустите игру", 1f, 12); }); }
                    catch (Exception error) { Minecraft.getInstance().schedule(() -> { status = "Ошибка: " + error.getMessage(); button.active = true; feedback("Ошибка загрузки обновления", -1f, 8); }); }
                });
            }).bounds(x - 155, y + 65, 150, 20).build());
            if (release == null) addRenderableWidget(com.wildfire.gui.FlatButtons.builder(Component.literal("Повторить проверку"), button -> {
                button.active = false;
                status = "Проверяем обновление ещё раз…";
                retry.run();
            }).bounds(x - 155, y + 65, 150, 20).build());
            addRenderableWidget(com.wildfire.gui.FlatButtons.builder(Component.literal("Нет, играть без мода"), button -> onClose())
                .bounds(x + 5, y + 65, 150, 20).build());
            if (lines.size() > 5) {
                addRenderableWidget(com.wildfire.gui.FlatButtons.builder(Component.literal("▲"), button -> scroll = Math.max(0, scroll - 1))
                    .bounds(x + 166, y - 35, 20, 20).build());
                addRenderableWidget(com.wildfire.gui.FlatButtons.builder(Component.literal("▼"), button -> scroll = Math.min(lines.size() - 5, scroll + 1))
                    .bounds(x + 166, y - 10, 20, 20).build());
            }
        }

        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            int x = width / 2, y = height / 2;
            centered(graphics, heading, x, y - 72, 0xFFE8EDF0);
            if (release != null) centered(graphics, "Версия " + release.version, x, y - 53, 0xFFBA9CFF);
            for (int line = 0; line < 5 && scroll + line < lines.size(); line++) {
                centered(graphics, lines.get(scroll + line), x, y - 34 + line * 14, 0xFFB3BDC8);
            }
            centered(graphics, status, x, y + 95, 0xFFBA9CFF);
            if (downloadProgress >= 0f && !status.isEmpty()) {
                graphics.fill(x - 155, y + 110, x + 155, y + 115, 0xFF343A43);
                graphics.fill(x - 155, y + 110, x - 155 + (int)(310 * downloadProgress), y + 115, com.wildfire.gui.UiTheme.ACCENT);
            }
        }

        private void centered(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
            graphics.text(font, text, x - font.width(text) / 2, y, color, false);
        }

        @Override public void onClose() {
            pending = false;
            declined = true;
            decline.run();
            Minecraft.getInstance().gui.setScreen(null);
        }

        @Override public boolean isPauseScreen() { return false; }
    }

    private static void stage(Release release) throws Exception {
        synchronized (stagingLock) {
        if (!release.url.matches(java.util.regex.Pattern.quote(BASE) + "/[a-f0-9]{64}\\.jar"))
            throw new IOException("Untrusted update download");
        manualDownloadUrl = release.url;
        updateHudState = 1;
        try {
            stageFiles(release);
            updateHudState = 2;
        } catch (Exception failure) {
            updateHudState = 3;
            throw failure;
        }
        }
    }

    private static void stageFiles(Release release) throws Exception {
        if (release.sha.equals(stagedHash) || stagedVersion != null
            && compareVersions(release.version, stagedVersion) <= 0) return;
        URI uri = URI.create(release.url);
        if (!"https".equals(uri.getScheme()) || !"skin.springplus.net".equals(uri.getHost())) throw new IOException("Недоверенный адрес");
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
        feedback("Загрузка обновления: 0%", 0f, 45);
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) { response.body().close(); throw new IOException("HTTP " + response.statusCode()); }
        long expected = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (expected > 128 * 1024 * 1024) { response.body().close(); throw new IOException("Файл слишком большой"); }
        byte[] bytes;
        try (var input = response.body(); var output = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[32 * 1024];
            int read, previousPercent = -1;
            while ((read = input.read(chunk)) != -1) {
                output.write(chunk, 0, read);
                if (output.size() > 128 * 1024 * 1024) throw new IOException("Файл слишком большой");
                if (expected > 0) {
                    int percent = (int)Math.min(100, output.size() * 100L / expected);
                    if (percent != previousPercent) { feedback("Загрузка обновления: " + percent + "%", percent / 100f, 45); previousPercent = percent; }
                }
            }
            bytes = output.toByteArray();
        }
        if (bytes.length < 1024) throw new IOException("Неверный ответ сервера");
        feedback("Проверка файла…", 1f, 30);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!digest.equals(release.sha)) throw new IOException("Контрольная сумма не совпала");
        Path current = Path.of(ModUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isRegularFile(current) || !current.getFileName().toString().endsWith(".jar")) throw new IOException("Мод запущен не из JAR");
        if (!release.version.matches("[0-9]+\\.[0-9]+\\.[0-9]+-beta\\.[0-9]+")) throw new IOException("Неверная версия обновления");
        Path target = current.resolveSibling("SpringPlusCosmetics-fabric-" + release.version + "+26.2.jar");
        Path staged = current.resolveSibling(".springplus-update-fabric-pending.bin");
        Path manifest = current.resolveSibling(".springplus-update-fabric-pending.txt");
        Path temporary = Files.createTempFile(current.getParent(), ".springplus-update-", ".part");
        Files.write(temporary, bytes);
        Files.move(temporary, staged, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Path manifestTemp = Files.createTempFile(current.getParent(), ".springplus-update-", ".txt");
        Files.writeString(manifestTemp, target.getFileName() + "\n" + digest + "\n");
        Files.move(manifestTemp, manifest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (!installerStarted) {
            startInstaller(current, staged, manifest);
            installerStarted = true;
        }
        stagedHash = release.sha;
        stagedVersion = release.version;
    }

    private static void startInstaller(Path current, Path staged, Path manifest) throws IOException {
        Path helperRoot = Files.createTempDirectory("springplus-update-helper-");
        Path helperClass = helperRoot.resolve("com/wildfire/main/networking/appearance/ModUpdateHelper.class");
        Files.createDirectories(helperClass.getParent());
        try (var input = ModUpdate.class.getResourceAsStream("ModUpdateHelper.class")) {
            if (input == null) throw new IOException("Класс установщика не найден");
            Files.copy(input, helperClass);
        }
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        installerProcess = new ProcessBuilder(java, "-cp", helperRoot.toString(), ModUpdateHelper.class.getName(),
            Long.toString(ProcessHandle.current().pid()), current.toString(), staged.toString(), manifest.toString()).start();
    }
}
