package com.wildfire.client.music;

import com.google.gson.*;
import javazoom.jl.decoder.*;
import javax.sound.sampled.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Authorized Yandex MP3 streaming only. No disk cache, encrypted formats or token forwarding. */
public final class PersonalMusicPlayer {
    public record View(MusicAccounts.Track track, boolean playing, boolean loading, long position,
                       int volume, boolean shuffle, String repeat, int queued, String status) {}
    private static final ExecutorService AUDIO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SpringPlus-Personal-Music"); t.setDaemon(true); return t;
    });
    private static final Object LOCK = new Object();
    private static final List<MusicAccounts.Track> QUEUE = new ArrayList<>();
    private static int generation, index = -1, volume = 50;
    private static boolean paused, loading, shuffle, serverActive, resumeAfterServer;
    private static String repeat = "off", status = "Выберите трек";
    private static long position;
    private static MusicAccounts.Track track;
    private static InputStream input;
    private static SourceDataLine line;
    private PersonalMusicPlayer() {}
    public static View view() {
        synchronized (LOCK) { return new View(track, track != null && !paused && !loading && line != null, loading,
            position, volume, shuffle, repeat, QUEUE.size(), status); }
    }
    public static List<MusicAccounts.Track> queue() { synchronized (LOCK) { return List.copyOf(QUEUE); } }
    public static void serverPlaying(boolean active) {
        synchronized (LOCK) {
            if (serverActive == active) return;
            serverActive = active;
            if (active && track != null && !paused) {
                resumeAfterServer = true; paused = true; status = "Пауза: играет серверная музыка";
                if (line != null) line.stop();
            } else if (!active && resumeAfterServer) {
                resumeAfterServer = false; paused = false; status = loading ? "Загружаем…" : "Воспроизведение";
                if (line != null) line.start(); LOCK.notifyAll();
            }
        }
    }
    public static void play(List<MusicAccounts.Track> tracks, MusicAccounts.Track selected) {
        if (!SpringPlusSession.enabled()) return;
        if (!MusicAccounts.view(MusicAccounts.Provider.YANDEX).connected()) return;
        if (!MusicLibrary.validId(MusicAccounts.Provider.YANDEX, selected.id())) return;
        if (SpotifyWebPlayer.view().running()) SpotifyWebPlayer.stop();
        synchronized (LOCK) {
            QUEUE.clear();
            for (var t : tracks) if (QUEUE.size() < 200 && MusicLibrary.validId(MusicAccounts.Provider.YANDEX, t.id())) QUEUE.add(t);
            index = QUEUE.indexOf(selected);
            if (index < 0) { QUEUE.addFirst(selected); index = 0; }
        }
        start(selected, 0);
    }
    public static void enqueue(MusicAccounts.Track song) {
        if (!MusicLibrary.validId(MusicAccounts.Provider.YANDEX, song.id())) return;
        synchronized (LOCK) { if (QUEUE.size() < 200) QUEUE.add(song); }
    }
    public static void stop() {
        InputStream oldInput; SourceDataLine oldLine;
        synchronized (LOCK) {
            generation++; oldInput = input; oldLine = line; input = null; line = null;
            track = null; loading = paused = false; position = 0; status = "Остановлено";
            QUEUE.clear(); index = -1; LOCK.notifyAll();
            resumeAfterServer = false;
            serverActive = false;
        }
        close(oldInput, oldLine);
    }
    public static void pause() {
        MusicAccounts.Track restart = null;
        synchronized (LOCK) {
            if (track == null || loading) return;
            if (serverActive) return;
            if (line == null) restart = track;
            else { paused = !paused; status = paused ? "Пауза" : "Воспроизведение"; }
            if (line != null) { if (paused) line.stop(); else line.start(); }
            LOCK.notifyAll();
        }
        if (restart != null) start(restart, 0);
    }
    public static void volume(int value) { synchronized (LOCK) { volume = Math.clamp(value, 0, 100); } }
    public static void shuffle() { synchronized (LOCK) { shuffle = !shuffle; } }
    public static void repeat() { synchronized (LOCK) { repeat = switch (repeat) { case "off" -> "all"; case "all" -> "one"; default -> "off"; }; } }
    public static void seek(long millis) {
        MusicAccounts.Track song;
        synchronized (LOCK) { song = track; }
        if (song != null) start(song, Math.clamp(millis, 0L, Math.max(0L, song.durationMillis() - 1000)));
    }
    public static void previous() {
        MusicAccounts.Track song;
        synchronized (LOCK) {
            if (QUEUE.isEmpty()) return;
            if (position < 3000) index = Math.max(0, index - 1);
            song = QUEUE.get(Math.clamp(index, 0, QUEUE.size() - 1));
        }
        start(song, 0);
    }
    public static void next() { advance(false); }
    private static void advance(boolean automatic) {
        MusicAccounts.Track song = null;
        synchronized (LOCK) {
            if (QUEUE.isEmpty()) return;
            int next = automatic && repeat.equals("one") ? index
                : shuffle && QUEUE.size() > 1 ? (index + 1 + ThreadLocalRandom.current().nextInt(QUEUE.size() - 1)) % QUEUE.size() : index + 1;
            if (next >= QUEUE.size() && repeat.equals("all")) next = 0;
            if (next < QUEUE.size()) { index = next; song = QUEUE.get(index); }
            else { paused = true; status = "Очередь закончилась"; }
        }
        if (song != null) start(song, 0);
    }
    private static void start(MusicAccounts.Track song, long seek) {
        final int ticket;
        InputStream oldInput; SourceDataLine oldLine;
        synchronized (LOCK) {
            ticket = ++generation; oldInput = input; oldLine = line; input = null; line = null;
            track = song; loading = true; paused = serverActive; resumeAfterServer = serverActive;
            position = seek; status = "Загружаем…"; LOCK.notifyAll();
        }
        close(oldInput, oldLine);
        MusicAccounts.library(MusicAccounts.Provider.YANDEX, "", lease -> {
            JsonArray variants = MusicLibrary.resultArray(lease.get("/tracks/" + song.id() + "/download-info"));
            JsonObject best = null;
            for (JsonElement value : variants) if (value.isJsonObject()) {
                JsonObject v = value.getAsJsonObject();
                if (!"mp3".equals(MusicLibrary.str(v, "codec")) || !v.has("preview") || v.get("preview").getAsBoolean()) continue;
                if (best == null || MusicLibrary.number(v, "bitrateInKbps", 0) > MusicLibrary.number(best, "bitrateInKbps", 0)) best = v;
            }
            if (best == null) throw new IOException("No authorized full MP3");
            URI xmlUrl = safeUri(MusicLibrary.str(best, "downloadInfoUrl"));
            byte[] xml;
            try (InputStream stream = connect(xmlUrl)) { xml = stream.readNBytes(32769); }
            if (xml.length > 32768) throw new IOException();
            URI audioUrl = directLink(xml);
            AUDIO.execute(() -> decode(ticket, audioUrl, seek));
            return new MusicLibrary.Page(List.of(), false, "");
        }, result -> {
            if (!result.message().isEmpty()) synchronized (LOCK) {
                if (ticket == generation) { loading = false; paused = true; status = result.message(); }
            }
        });
    }
    static URI safeUri(String url) throws IOException {
        URI uri;
        try { uri = URI.create(url); } catch (RuntimeException ex) { throw new IOException(); }
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null || uri.getRawUserInfo() != null || uri.getFragment() != null
            || (uri.getPort() != -1 && uri.getPort() != 443)
            || !(host.endsWith(".yandex.net") || host.endsWith(".yandex.ru") || host.equals("yandex.net") || host.equals("yandex.ru")))
            throw new IOException();
        return uri;
    }
    static URI directLink(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
        });
        var doc = builder.parse(new ByteArrayInputStream(xml));
        String host = doc.getElementsByTagName("host").item(0).getTextContent();
        String path = doc.getElementsByTagName("path").item(0).getTextContent();
        String ts = doc.getElementsByTagName("ts").item(0).getTextContent();
        String salt = doc.getElementsByTagName("s").item(0).getTextContent();
        if (!host.matches("[a-zA-Z0-9.-]+") || !path.startsWith("/") || path.contains("?") || path.contains("#")
            || !ts.matches("[0-9]+")) throw new IOException();
        String sign = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
            .digest(("XGRlBW9FXlekgbPrRHuSiA" + path.substring(1) + salt).getBytes(StandardCharsets.UTF_8)));
        return safeUri("https://" + host + "/get-mp3/" + sign + "/" + ts + path);
    }
    private static InputStream connect(URI uri) throws IOException {
        var connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "SpringPlusMusic/1");
        if (connection.getResponseCode() != 200) { connection.disconnect(); throw new IOException(); }
        InputStream stream = connection.getInputStream();
        return new FilterInputStream(stream) { @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } } };
    }
    private static void decode(int ticket, URI uri, long seek) {
        InputStream stream = null; SourceDataLine output = null;
        boolean finished = false;
        try {
            if (!current(ticket)) return;
            stream = new BufferedInputStream(connect(uri), 32768);
            synchronized (LOCK) { if (ticket != generation) return; input = stream; }
            Bitstream bits = new Bitstream(stream); Decoder decoder = new Decoder();
            double millis = 0;
            Header header;
            while (current(ticket) && (header = bits.readFrame()) != null) {
                SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(header, bits);
                int length = samples.getBufferLength(), channels = samples.getChannelCount(), rate = samples.getSampleFrequency();
                double frameMillis = 1000.0 * length / channels / rate;
                if (millis + frameMillis <= seek) { millis += frameMillis; bits.closeFrame(); continue; }
                if (output == null) {
                    AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
                    output = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
                    output.open(format, Math.max(8192, rate * channels / 5)); output.start();
                    synchronized (LOCK) {
                        if (ticket != generation) return;
                        line = output; loading = false;
                        status = serverActive ? "Пауза: играет серверная музыка" : "Воспроизведение";
                        if (paused) output.stop();
                    }
                }
                int gain;
                synchronized (LOCK) {
                    while (ticket == generation && paused) LOCK.wait();
                    if (ticket != generation) return;
                    gain = volume;
                }
                byte[] pcm = new byte[length * 2];
                short[] source = samples.getBuffer();
                for (int i = 0; i < length; i++) {
                    int sample = source[i] * gain / 100;
                    pcm[i * 2] = (byte) sample; pcm[i * 2 + 1] = (byte) (sample >> 8);
                }
                int written = 0;
                while (written < pcm.length && current(ticket)) {
                    int n = output.write(pcm, written, pcm.length - written);
                    if (n <= 0) throw new IOException();
                    written += n;
                }
                millis += frameMillis;
                synchronized (LOCK) { if (ticket == generation) position = (long) millis; }
                bits.closeFrame();
            }
            if (current(ticket)) { if (output != null) output.drain(); finished = true; }
        } catch (Exception ignored) {
            synchronized (LOCK) { if (ticket == generation) { loading = false; paused = true; status = "Не удалось воспроизвести: сеть / формат / устройство звука"; } }
        } finally {
            close(stream, output);
            synchronized (LOCK) { if (ticket == generation) { input = null; line = null; loading = false; } }
        }
        if (finished && current(ticket)) advance(true);
    }
    private static boolean current(int ticket) { synchronized (LOCK) { return generation == ticket; } }
    private static void close(InputStream stream, SourceDataLine output) {
        if (output != null) { output.stop(); output.flush(); output.close(); }
        if (stream != null) try { stream.close(); } catch (IOException ignored) {}
    }
}
