package com.wildfire.client.music;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Offline protocol fixture only. Does not start a browser or authenticate any account. */
public final class SpotifyWebCheck {
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void run() throws Exception {
        require(WebMediaBridge.read(MusicWebsite.SOUNDCLOUD).contains("volume__sliderProgress"), "SoundCloud reads website volume without media element");
        require(SoundCloudVolumeBridge.set(.35).contains("r.height*0.35"), "SoundCloud vertical slider coordinates");
        require(SoundCloudVolumeBridge.set(.35).contains("startY"), "SoundCloud volume drag begins at handle");
        require(SoundCloudSeekBridge.set(.75).contains("r.width*0.75"), "SoundCloud timeline seeks via website");
        require(SoundCloudSeekBridge.set(.75).contains("playbackTimeline__progressWrapper"), "Timeline uses interactive wrapper");
        try { SoundCloudVolumeBridge.set(Double.NaN); throw new AssertionError("Invalid SoundCloud volume accepted"); }
        catch (IllegalArgumentException expected) {}
        String diagnostic = SpotifyWebPlayer.diagnosticMessage("failed https://example.com/?token=private token=private password=private");
        require(!diagnostic.contains("private") && !diagnostic.contains("example.com"), "Startup diagnostics redact URLs and credentials");
        var loginArgs = SpotifyWebPlayer.externalLoginArguments(java.nio.file.Path.of("browser.exe"),
            java.nio.file.Path.of("music-profile"), MusicWebsite.YOUTUBE);
        require(loginArgs.contains("--new-window") && loginArgs.contains(MusicWebsite.YOUTUBE.url), "Visible external music login");
        require(loginArgs.stream().noneMatch(arg -> arg.startsWith("--remote-debugging") || arg.startsWith("--app=")),
            "External login must not enable automation or embedded app mode");
        for (MusicWebsite site : MusicWebsite.values()) {
            require(site.accepts(site.url), "Known provider URL");
            require(!site.accepts(site.url.replace("https:", "http:")), "Reject HTTP provider");
            require(!site.accepts(site.url.replace("https://", "https://user@")), "Reject userinfo");
            require(!site.accepts(site.url.replace("https://", "https://evil.example/?")), "Reject other host");
            if (site != MusicWebsite.SPOTIFY) {
                String origin = site.url.substring(0, site.url.length()-1);
                require(WebMediaBridge.read(site).contains(origin), "Provider metadata origin guard");
                require(WebMediaBridge.seek(.5, site).contains(origin), "Provider seek origin guard");
                require(WebMediaBridge.volume(.5, site).contains(origin), "Provider volume origin guard");
            }
        }
        for (int width : new int[]{128,150,176}) for (int accent : new int[]{0xFF1DB954,0xFFFFCC33,0xFFB681D4}) {
            var mesh = ListeningCardMesh.build(width, accent);
            require(mesh.size() < 120, "Merged card geometry remains bounded");
            int[][] coverage = new int[ListeningCardMesh.HEIGHT][width]; int[][] colors = new int[ListeningCardMesh.HEIGHT][width];
            for (var rect : mesh) {
                require(rect.left() >= 0 && rect.right() <= width && rect.top() >= 0 && rect.bottom() <= ListeningCardMesh.HEIGHT, "Card geometry bounds");
                for (int y=rect.top(); y<rect.bottom(); y++) for (int x=rect.left(); x<rect.right(); x++) {
                    coverage[y][x]++; colors[y][x]=rect.color();
                }
            }
            for (int y=0;y<ListeningCardMesh.HEIGHT;y++) for(int x=0;x<width;x++) {
                int expected=ListeningCardMesh.color(x,y,width,accent);
                require(coverage[y][x] == (expected == 0 ? 0 : 1), "No overlapping card planes or gaps");
                require(colors[y][x] == expected, "Card mesh color preserved");
            }
            if (accent == 0xFF1DB954 || accent == 0xFFFFCC33) require(colors[16][18] == 0xF01D222B, "Provider texture has clean background");
            else require(colors[11][18] == accent && colors[23][15] == accent, "Project note visible");
        }
        require(ListeningLabelRenderer.cardWidth(10,20) == 128, "Music card minimum width");
        require(ListeningLabelRenderer.cardWidth(999,20) == 176, "Music card maximum width");
        try (InputStream input = SpotifyWebCheck.class.getClassLoader().getResourceAsStream("net/minecraft/client/renderer/entity/EntityRenderer.class")) {
            require(input != null, "Renderer bytecode available");
            var node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(input).accept(node, org.objectweb.asm.ClassReader.SKIP_CODE);
            require(node.methods.stream().anyMatch(m -> m.name.equals("submit") && m.desc.equals("(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V")), "Independent entity submission hook exists");
        }
        require(ListeningState.clean("  hello\nworld§  ", 80).equals("helloworld"), "Listening title sanitized");
        require(ListeningState.clean("12345", 3).equals("123"), "Listening title bounded");
        ListeningState.accept("not-json"); // malformed presence must not crash the render client
        ListeningState.reset();
        var prefFields = new java.util.HashMap<java.lang.reflect.Field, Object>();
        for (String key : List.of("loaded", "musicLabels", "ownMusicLabel", "shareListening")) {
            var field = com.wildfire.client.config.CosmeticPreferences.class.getDeclaredField(key); field.setAccessible(true);
            prefFields.put(field, field.get(null)); field.set(null, !key.equals("ownMusicLabel"));
        }
        try {
            var id = java.util.UUID.randomUUID(); var other = java.util.UUID.randomUUID();
            var sentPresence = new java.util.ArrayList<String>();
            ListeningState.tick(id, "Track", "Artist", 1, true, sentPresence::add);
            require(sentPresence.size() == 1 && !sentPresence.getFirst().contains("uuid"), "Client cannot choose presence identity");
            require(ListeningState.label(id, id) == null, "Own label disabled by default");
            var label = ListeningState.label(id, other);
            var card = ListeningState.card(id, other);
            require(card != null && card.platform().equals("Spotify") && card.accent() == 0xFF1DB954, "Structured card platform style");
            require(label != null && label == ListeningState.label(id, other), "Cached presence component reused");
            var checkTime = ListeningState.class.getDeclaredField("checked"); checkTime.setAccessible(true); checkTime.setLong(null, 0);
            ListeningState.tick(id, "Track", "Artist", 1, true, sentPresence::add);
            require(sentPresence.size() == 1, "Unchanged track sends no extra packet");
            var lastTime = ListeningState.class.getDeclaredField("sent"); lastTime.setAccessible(true); lastTime.setLong(null, System.nanoTime()-31_000_000_000L);
            checkTime.setLong(null, 0);
            ListeningState.tick(id, "Track", "Artist", 1, true, sentPresence::add);
            require(sentPresence.size() == 2, "Rare presence heartbeat");
            require(card == ListeningState.card(id, other), "Heartbeat reuses prepared card identity");
            var share = com.wildfire.client.config.CosmeticPreferences.class.getDeclaredField("shareListening"); share.setAccessible(true); share.setBoolean(null,false);
            checkTime.setLong(null, 0);
            ListeningState.tick(id, "Track", "Artist", 1, true, sentPresence::add);
            require(JsonParser.parseString(sentPresence.getLast()).getAsJsonObject().get("title").getAsString().isEmpty(), "Publishing opt-out clears public title");
        } finally {
            prefFields.forEach((field,value) -> { try {field.set(null,value);} catch (IllegalAccessException e) {throw new AssertionError(e);} });
            ListeningState.reset();
        }
        JsonObject popupInfo = new JsonObject(); popupInfo.addProperty("type", "page"); popupInfo.addProperty("openerId", "music-tab");
        require(SpotifyWebPlayer.ownedPopup(popupInfo, "music-tab"), "Popup opener ownership");
        require(!SpotifyWebPlayer.ownedPopup(popupInfo, "personal-tab"), "Foreign popup rejected");
        require(!SpotifyWebPlayer.ownedPopup(popupInfo, ""), "Missing opener rejected");
        popupInfo.addProperty("type", "service_worker");
        require(!SpotifyWebPlayer.ownedPopup(popupInfo, "music-tab"), "Non-page target rejected");
        require(SpotifyWebPlayer.hostOnly("https://accounts.google.com/signin?code=secret").equals("accounts.google.com"), "Only auth hostname displayed");
        require(MusicUiButton.hit(100, 119, 100) == 1, "Central play button hitbox");
        require(MusicUiButton.hit(50, 119, 100) == 0 && MusicUiButton.hit(140, 119, 100) == 2, "Skip hitboxes");
        require(MusicUiButton.hit(77, 119, 100) == -1, "Control gap is not clickable");
        require(MusicUiButton.hit(100, 103, 100) == -1, "Volume row does not trigger playback");
        require(SpotifyBrowserViewport.inside(5, 31, 0, 30, 800, 600), "Browser click receives focus");
        require(!SpotifyBrowserViewport.inside(5, 20, 0, 30, 800, 600), "Header does not steal focus");
        require(!SpotifyBrowserViewport.inside(800, 50, 0, 30, 800, 600), "Viewport right edge excluded");
        require((SpotifyBrowserViewport.childStyle(0x80CF0000) & 0x80CF0000) == 0, "Window decorations removed");
        require((SpotifyBrowserViewport.childStyle(0x80CF0000) & 0x40000000) != 0, "Child style retained");
        require(SpotifyBrowserViewport.measuredInset(720, 690, 0) == 30, "Custom browser title measured");
        require(SpotifyBrowserViewport.measuredInset(750, 720, 30) == 30, "Clipping remains stable after resize");
        require(SpotifyBrowserViewport.measuredInset(720, 720, 30) == 0, "No title does not crop page");
        require(SpotifyBrowserViewport.measuredInset(720, Double.NaN, 30) == 30, "Invalid frame measurement ignored");
        require(SpotifyBrowserViewport.measuredInset(720, 400, 30) == 30, "Transient resize mismatch ignored");
        require(WebMediaBridge.INSTALL.contains("setActionHandler") && WebMediaBridge.READ.contains("media.volume"), "Standard media bridge");
        require(WebMediaBridge.action("next", false).contains("nexttrack"), "Next action");
        require(WebMediaBridge.action("playpause", true).contains("pause"), "Pause action");
        require(WebMediaBridge.volume(.35).contains("m.volume=0.35"), "Volume bridge");
        require(WebMediaBridge.seek(.35).contains("duration*0.35") && WebMediaBridge.seek(.35).contains("window.__spPosition?.duration") && WebMediaBridge.seek(.35).contains("seekTime:time"),
            "Yandex seek uses known duration and media-session handler");
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -.1, 1.1}) {
            try { WebMediaBridge.seek(invalid); throw new AssertionError("Invalid seek accepted"); }
            catch (IllegalArgumentException expected) {}
        }
        try { WebMediaBridge.volume(Double.NaN); throw new AssertionError("Invalid volume accepted"); } catch (IllegalArgumentException expected) {}
        require(SpotifyWebPlayer.providerUrl("https://music.yandex.ru/", true), "Yandex viewport origin");
        require(!SpotifyWebPlayer.providerUrl("https://music.yandex.ru.evil.invalid/", true), "Yandex lookalike rejected");
        require(!SpotifyWebPlayer.providerUrl("https://user@music.yandex.ru/", true), "Yandex userinfo rejected");
        require(!SpotifyWebPlayer.providerUrl("http://music.yandex.ru/", true), "Yandex HTTPS required");
        require(!SpotifyWebPlayer.providerUrl("https://open.spotify.com/", true), "Provider isolation");
        require(SpotifyWebPlayer.normalizeVolume(0.95, 0, 1, 50) == 95, "Fractional volume");
        require(SpotifyWebPlayer.normalizeVolume(0, 0, 1, 50) == 0, "Mute preserved");
        require(SpotifyWebPlayer.normalizeVolume(35, 0, 100, 50) == 35, "Percent volume");
        require(SpotifyWebPlayer.normalizeVolume(Double.NaN, 0, 1, 35) == 35, "Unknown volume preserved");
        require(SpotifyWebPlayer.endpoint(List.of("12345", "/devtools/browser/abc-def")).toString().equals("ws://127.0.0.1:12345/devtools/browser/abc-def"), "Loopback debugger");
        for (List<String> bad : List.of(List.of("0","/devtools/browser/abc"), List.of("65536","/devtools/browser/abc"),
            List.of("123","https://evil.invalid"), List.of("123","/devtools/browser/../../bad"), List.of("oops","/devtools/browser/abc"))) {
            try { SpotifyWebPlayer.endpoint(bad); throw new AssertionError("Invalid endpoint accepted"); } catch (IOException expected) {}
        }
        require(SpotifyWebPlayer.spotifyUrl("https://open.spotify.com/track/0123456789012345678901"), "Provider URL");
        for (String bad : List.of("http://open.spotify.com", "https://open.spotify.com.evil.invalid", "https://user@open.spotify.com", "https://open.spotify.com:9999"))
            require(!SpotifyWebPlayer.spotifyUrl(bad), "Foreign URL rejected");
        require(SpotifyWebPlayer.clock("3:25") == 205000 && SpotifyWebPlayer.clock("1:02:03") == 3723000, "Playback clock");
        require(SpotifyWebPlayer.clock("x:y") == 0 && SpotifyWebPlayer.clock("-1:02") == 0, "Malformed clock");
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(5000);
            AtomicReference<Throwable> fault = new AtomicReference<>();
            Thread fixture = new Thread(() -> {
                try (Socket peer = server.accept()) {
                    peer.setSoTimeout(5000); InputStream in = peer.getInputStream(); OutputStream out = peer.getOutputStream();
                    ByteArrayOutputStream header = new ByteArrayOutputStream(); int next;
                    while ((next = in.read()) >= 0 && header.size() < 8192) {
                        header.write(next); byte[] b = header.toByteArray(); int n = b.length;
                        if (n >= 4 && b[n-4] == 13 && b[n-3] == 10 && b[n-2] == 13 && b[n-1] == 10) break;
                    }
                    String request = header.toString(StandardCharsets.US_ASCII);
                    require(!request.toLowerCase(Locale.ROOT).contains("authorization:"), "No provider credentials on socket");
                    String key = request.lines().filter(l -> l.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:")).findFirst().orElseThrow().split(":",2)[1].trim();
                    String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
                    out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); out.flush();
                    for (int call = 0; call < 2; call++) {
                        in.read(); int length = in.read(); require((length & 128) != 0, "Client frame masked"); length &= 127;
                        if (length == 126) length = in.read() * 256 + in.read();
                        require(length < 4096, "Bounded fixture request");
                        byte[] mask = in.readNBytes(4), payload = in.readNBytes(length);
                        for (int i=0;i<payload.length;i++) payload[i] ^= mask[i%4];
                        JsonObject command = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
                        require(command.get("method").getAsString().equals("Runtime.evaluate"), "Expected protocol command");
                        int id = command.get("id").getAsInt();
                        String reply = call == 0 ? "{\"id\":"+id+",\"result\":{\"result\":{\"value\":true}}}" : "{\"id\":"+id+",\"error\":{\"code\":-1,\"message\":\"fixture\"}}";
                        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8); int half = bytes.length/2;
                        out.write(1); out.write(half); out.write(bytes,0,half); out.flush();
                        out.write(128); out.write(bytes.length-half); out.write(bytes,half,bytes.length-half); out.flush();
                    }
                } catch (Throwable error) { fault.set(error); }
            }, "Spotify-Web-Protocol-Fixture");
            fixture.setDaemon(true); fixture.start();
            Class<?> type = Class.forName("com.wildfire.client.music.SpotifyWebPlayer$Cdp");
            var constructor = type.getDeclaredConstructor(URI.class); constructor.setAccessible(true);
            var call = type.getDeclaredMethod("call",String.class,JsonObject.class,String.class); call.setAccessible(true);
            var close = type.getDeclaredMethod("close"); close.setAccessible(true);
            Object socket = constructor.newInstance(URI.create("ws://127.0.0.1:"+server.getLocalPort()+"/devtools/browser/test"));
            try {
                JsonObject response = (JsonObject) call.invoke(socket,"Runtime.evaluate",new JsonObject(),"fixture-session");
                require(response.getAsJsonObject("result").get("value").getAsBoolean(), "Fragmented reply assembled");
                try { call.invoke(socket,"Runtime.evaluate",new JsonObject(),"fixture-session"); throw new AssertionError("Protocol error ignored"); }
                catch (java.lang.reflect.InvocationTargetException error) { require(error.getCause() instanceof IOException,"Protocol error surfaced"); }
                var pending = type.getDeclaredField("pending"); pending.setAccessible(true);
                require(((Map<?,?>)pending.get(socket)).isEmpty(), "No pending requests leaked");
            } finally { close.invoke(socket); }
            fixture.join(1000); require(fault.get() == null, "Protocol fixture failure: " + fault.get());
        }
        System.out.println("Spotify web checks passed: isolated endpoint, URL guard, clock, fragmented CDP, error cleanup");
    }
}
