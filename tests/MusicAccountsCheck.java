package com.wildfire.client.music;
import java.util.Map;
import java.lang.reflect.Method;
public final class MusicAccountsCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void rejected(String raw) {
        try { MusicAccounts.query(raw); throw new AssertionError("Accepted malformed callback"); }
        catch (IllegalArgumentException expected) {}
    }
    public static void main(String[] args) throws Exception {
        var overlay = new ChatOverlayButton(10, 0, 100, 20, net.minecraft.network.chat.Component.empty(), button -> {});
        overlay.visible = false;
        require(!overlay.isMouseOver(20, 10), "Hidden overlay cannot steal clicks on Minecraft 26.2");
        overlay.visible = true;
        require(overlay.isMouseOver(20, 10), "Visible overlay keeps mouse interaction");
        require(overlay.nextFocusPath(null) == null, "Overlay cannot be selected with chat arrow keys");
        require(!overlay.shouldTakeFocusAfterInteraction(), "Overlay click preserves chat focus");
        var updateState = new java.util.concurrent.atomic.AtomicInteger();
        var update = new UpdateHudButton(updateState::get, () -> {});
        update.sync(38,true);
        require(!update.visible && !update.isMouseOver(15,43), "No update leaves no invisible hitbox");
        updateState.set(1);update.sync(58,true);
        require(update.visible && !update.active, "Downloading cannot exit the game");
        updateState.set(2);update.sync(58,true);
        require(update.active && update.isMouseOver(15,63), "Ready update is clickable below moved bell");
        updateState.set(3);update.sync(58,true);
        require(update.active && update.visible, "Failed update exposes manual-download action");
        update.sync(58,false);
        require(!update.isMouseOver(15,63), "Hidden update cannot steal chat clicks");
        require(update.nextFocusPath(null)==null && !update.shouldTakeFocusAfterInteraction(),
            "Update button preserves chat keyboard focus");
        for (int screenWidth : new int[] {120, 240, 320, 640, 960, 1920}) {
            var layout = HudPlayerLayout.of(screenWidth);
            require(layout.x() >= 8, "HUD never starts on the left edge");
            require(layout.x() + layout.width() == screenWidth - 8, "HUD stays right-aligned at every GUI scale");
            require(layout.width() <= 250, "HUD maximum width matches rendered panel");
            require(!layout.fits(100), "Small window has no invisible player hitbox");
        }
        require(HudPlayerLayout.of(640).x() == 382, "Right position exists before first render");
        require(HudPlayerLayout.of(320).x() == 62, "Resize recalculates right position without render");
        require(!HudPlayerLayout.of(120).fits(480), "Insufficient width disables HUD");
        require(!SpringPlusSession.enabled(), "Session fails closed before SpringPlus handshake");
        for (var site : MusicWebsite.values()) {
            SpotifyWebPlayer.start(null, site);
            SpotifyWebPlayer.loginInBrowser(null, site);
        }
        PersonalMusicPlayer.play(null, null);
        require(!SpotifyWebPlayer.view().running() && !SpotifyWebPlayer.view().busy()
            && !SpotifyWebPlayer.externalLoginOpen(), "Foreign server cannot start browser or login");
        require(PersonalMusicPlayer.view().track() == null, "Foreign server cannot start native audio");
        SpringPlusSession.configure(() -> true);
        MenuPresence.reset();
        var menuOwner=java.util.UUID.randomUUID();
        var menuSent=new java.util.ArrayList<String>();
        MenuPresence.tick(menuOwner,1,menuSent::add);
        require(menuSent.size()==1 && menuSent.getFirst().equals("{\"menu\":1,\"receive\":true}"),
            "Menu presence sends category only, never UI contents");
        MenuPresence.tick(menuOwner,1,menuSent::add);
        require(menuSent.size()==1,"Menu presence is not sent every frame");
        var mapField=MenuPresence.class.getDeclaredField("entries");mapField.setAccessible(true);
        var menuEntries=(java.util.Map<?,?>)mapField.get(null);
        Object entry=menuEntries.get(menuOwner);
        var changed=entry.getClass().getDeclaredField("changed");changed.setAccessible(true);
        changed.setLong(entry,System.nanoTime()-400_000_000L);
        require(MenuPresence.view(menuOwner).amount()>.99f,"Menu appearance finishes smoothly");
        MenuPresence.put(menuOwner,-1);
        require(MenuPresence.view(menuOwner)!=null,"Menu closing has an animation rather than instant disappearance");
        changed.setLong(entry,System.nanoTime()-400_000_000L);
        require(MenuPresence.view(menuOwner)==null,"Closed menu has no world panel");
        for(int i=0;i<300;i++)MenuPresence.put(new java.util.UUID(0,i),1);
        require(menuEntries.size()<=256,"Remote menu presence cache stays bounded");
        MenuPresence.reset();
        require(SpringPlusSession.enabled(), "Confirmed server enables session");
        SpringPlusSession.tick();
        SpringPlusSession.configure(() -> false);
        SpringPlusSession.tick();
        require(!SpringPlusSession.enabled() && !SpotifyWebPlayer.view().running(), "Revoked session stays stopped");
        require(MusicAccounts.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
            .equals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"), "RFC 7636 PKCE vector");
        String state = MusicAccounts.randomToken();
        require(state.length() == 43, "256-bit state / verifier");
        require(MusicAccounts.stateMatches(state, state), "Valid state");
        require(!MusicAccounts.stateMatches(state, null), "Missing state");
        require(!MusicAccounts.stateMatches(state, state + "x"), "Wrong state");
        require(MusicAccounts.query("code=a%2Bb&state=hello").equals(Map.of("code", "a+b", "state", "hello")), "Decode");
        rejected("state=a&state=b"); rejected("code=%XX"); rejected("x".repeat(8193)); rejected(null);
        for (var provider : MusicAccounts.Provider.values()) {
            MusicAccounts.disconnect(provider);
            var view = MusicAccounts.view(provider);
            require(!view.connected() && !view.pending() && view.account().isEmpty(), "Clean disconnected state");
            Method session = MusicAccounts.class.getDeclaredMethod("session", MusicAccounts.Provider.class);
            session.setAccessible(true); Object value = session.invoke(null, provider);
            var generation = value.getClass().getDeclaredField("generation"); generation.setAccessible(true);
            int old = generation.getInt(value);
            Method fail = MusicAccounts.class.getDeclaredMethod("fail", MusicAccounts.Provider.class, int.class, String.class);
            fail.setAccessible(true); fail.invoke(null, provider, old, "Timed out");
            require(generation.getInt(value) != old, "Timeout invalidates late callbacks");
            fail.invoke(null, provider, old, "Late response");
            require(MusicAccounts.view(provider).status().equals("Timed out"), "Late response ignored");
            MusicAccounts.disconnect(provider);
        }
        require(java.util.Arrays.stream(MusicAccounts.View.class.getRecordComponents())
            .noneMatch(c -> c.getName().matches("access|refresh|token")), "UI exposes no credentials");
        require(MusicAccounts.spotifyId().equals("d55d95a82a30414188259fb988d2022f"), "Bundled public client ID");
        var sessionMethod = MusicAccounts.class.getDeclaredMethod("session", MusicAccounts.Provider.class);
        sessionMethod.setAccessible(true);
        Object spotify = sessionMethod.invoke(null, MusicAccounts.Provider.SPOTIFY);
        var pending = spotify.getClass().getDeclaredField("pending"); pending.setAccessible(true); pending.setBoolean(spotify, true);
        var generationField = spotify.getClass().getDeclaredField("generation"); generationField.setAccessible(true);
        int generation = generationField.getInt(spotify);
        var start = MusicAccounts.class.getDeclaredMethod("startSpotify", int.class, java.util.function.Consumer.class);
        start.setAccessible(true);
        var auth = new java.util.concurrent.atomic.AtomicReference<java.net.URI>();
        try {
            start.invoke(null, generation, (java.util.function.Consumer<java.net.URI>) auth::set);
            var params = MusicAccounts.query(auth.get().getRawQuery());
            require(params.get("client_id").equals(MusicAccounts.spotifyId()), "One-click login uses bundled app");
            require(params.get("redirect_uri").equals(MusicAccounts.REDIRECT), "Registered redirect");
            require(params.get("code_challenge_method").equals("S256"), "PKCE enforced");
            var http = java.net.http.HttpClient.newHttpClient();
            var wrong = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(MusicAccounts.REDIRECT + "?code=fake&state=wrong")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            require(wrong.statusCode() == 400 && MusicAccounts.view(MusicAccounts.Provider.SPOTIFY).pending(), "Bad state does not consume session");
            var denied = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(MusicAccounts.REDIRECT + "?error=access_denied&state=" + params.get("state"))).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            require(denied.statusCode() == 200, "Cancellation browser response");
            for (int i = 0; i < 100 && MusicAccounts.view(MusicAccounts.Provider.SPOTIFY).pending(); i++) Thread.sleep(10);
            require(!MusicAccounts.view(MusicAccounts.Provider.SPOTIFY).pending(), "Denial clears pending session");
        } finally { MusicAccounts.disconnect(MusicAccounts.Provider.SPOTIFY); }
        var spotifyResponse = com.google.gson.JsonParser.parseString("{\"tracks\":{\"items\":[{\"id\":\"0123456789012345678901\",\"name\":\"Song\",\"artists\":[{\"name\":\"Artist\"}],\"duration_ms\":123000,\"external_urls\":{\"spotify\":\"https://evil.invalid\"}},null,{\"id\":\"../bad\"}]}}").getAsJsonObject();
        var spotifyTracks = MusicAccounts.parseTracks(MusicAccounts.Provider.SPOTIFY, spotifyResponse);
        require(spotifyTracks.size() == 1 && spotifyTracks.getFirst().artist().equals("Artist"), "Spotify search parsing");
        require(spotifyTracks.getFirst().durationMillis() == 123000, "Track duration");
        require(spotifyTracks.getFirst().url().equals("https://open.spotify.com/track/0123456789012345678901"), "Canonical link, no arbitrary URL");
        var yandexResponse = com.google.gson.JsonParser.parseString("{\"result\":{\"tracks\":{\"results\":[{\"id\":12345,\"title\":\"Song\",\"artists\":[{\"name\":\"Artist\"}],\"durationMs\":80000},{\"id\":\"fake\"}]}}}").getAsJsonObject();
        var yandexTracks = MusicAccounts.parseTracks(MusicAccounts.Provider.YANDEX, yandexResponse);
        require(yandexTracks.size() == 1 && yandexTracks.getFirst().url().equals("https://music.yandex.ru/track/12345"), "Yandex parsing / canonical link");
        require(MusicAccounts.parseTracks(MusicAccounts.Provider.SPOTIFY, new com.google.gson.JsonObject()).isEmpty(), "Empty search response");
        var noAccount = new java.util.concurrent.atomic.AtomicReference<MusicAccounts.SearchResult>();
        MusicAccounts.search(MusicAccounts.Provider.SPOTIFY, "query", noAccount::set);
        require(noAccount.get() != null && noAccount.get().tracks().isEmpty(), "Disconnected search does not call API");
        var items = com.google.gson.JsonParser.parseString("""
            [{"item":{"id":"0123456789012345678901","name":"New schema","duration_ms":90000,"artists":[{"name":"A"}]}},
             {"track":{"id":"1123456789012345678901","name":"Old schema","artists":[]}},
             {"item":null},{"track":{"id":"../../bad"}},{"track":{"id":"2123456789012345678901","is_local":true}},
             {"track":{"id":"3123456789012345678901","type":"episode"}},{"track":{"id":"4123456789012345678901","is_local":{}}}]
            """).getAsJsonArray();
        var parsed = MusicLibrary.tracks(MusicAccounts.Provider.SPOTIFY, items, true);
        require(parsed.entries().size() == 2 && parsed.more(), "Playlist item + legacy track, malformed/local/episodes skipped");
        require(parsed.entries().getFirst().track().durationMillis() == 90000, "Library duration");
        require(parsed.entries().getFirst().url().startsWith("https://open.spotify.com/track/"), "Library canonical link");
        var ymItems = com.google.gson.JsonParser.parseString("""
            [{"track":{"id":12345,"title":"YM","durationMs":20000,"artists":[{"name":"B"}]}},null,{"id":"bad"}]
            """).getAsJsonArray();
        require(MusicLibrary.tracks(MusicAccounts.Provider.YANDEX, ymItems, false).entries().size() == 1, "Yandex playlist parsing");
        var page = new java.util.concurrent.atomic.AtomicReference<MusicLibrary.Page>();
        MusicLibrary.load(MusicAccounts.Provider.SPOTIFY, MusicLibrary.Section.FAVORITES, "", null, 0, page::set);
        require(page.get() != null && page.get().entries().isEmpty(), "Disconnected library does not request API");
        require(PersonalMusicPlayer.safeUri("https://storage.mds.yandex.net/file").getHost().equals("storage.mds.yandex.net"), "Provider audio host allowed");
        for (String bad : java.util.List.of("http://storage.mds.yandex.net/file", "https://evil.invalid/file",
            "https://yandex.net.evil.invalid/file", "https://user@storage.mds.yandex.net/file", "https://storage.mds.yandex.net:8888/file")) {
            try { PersonalMusicPlayer.safeUri(bad); throw new AssertionError("Unsafe audio URL accepted"); } catch (java.io.IOException expected) {}
        }
        byte[] xml = "<download-info><host>storage.mds.yandex.net</host><path>/test.mp3</path><ts>123</ts><s>salt</s></download-info>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var link = PersonalMusicPlayer.directLink(xml);
        String expectedSign = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(
            "XGRlBW9FXlekgbPrRHuSiAtest.mp3salt".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        require(link.toString().equals("https://storage.mds.yandex.net/get-mp3/" + expectedSign + "/123/test.mp3"), "Provider-signed MP3 URL");
        try {
            PersonalMusicPlayer.directLink("<!DOCTYPE x [<!ENTITY y SYSTEM 'file:///does-not-exist'>]><x>&y;</x>".getBytes());
            throw new AssertionError("External XML entity accepted");
        } catch (org.xml.sax.SAXException expected) {}
        PersonalMusicPlayer.stop();
        PersonalMusicPlayer.volume(-10); require(PersonalMusicPlayer.view().volume() == 0, "Volume lower bound");
        PersonalMusicPlayer.volume(110); require(PersonalMusicPlayer.view().volume() == 100, "Volume upper bound");
        for (int i = 1; i <= 250; i++) PersonalMusicPlayer.enqueue(new MusicAccounts.Track(Integer.toString(i), "T", "A", 1, ""));
        require(PersonalMusicPlayer.queue().size() == 200, "Queue bounded");
        try { PersonalMusicPlayer.queue().clear(); throw new AssertionError("Mutable queue exposed"); } catch (UnsupportedOperationException expected) {}
        PersonalMusicPlayer.stop(); require(PersonalMusicPlayer.queue().isEmpty(), "Stop releases queue");
        var audioClass = PersonalMusicPlayer.class;
        var audioTrack = audioClass.getDeclaredField("track"); audioTrack.setAccessible(true);
        var pausedField = audioClass.getDeclaredField("paused"); pausedField.setAccessible(true);
        audioTrack.set(null, new MusicAccounts.Track("12345", "T", "A", 20000, ""));
        pausedField.setBoolean(null, false);
        PersonalMusicPlayer.serverPlaying(true); require(pausedField.getBoolean(null), "Server audio pauses personal audio");
        PersonalMusicPlayer.serverPlaying(false); require(!pausedField.getBoolean(null), "Personal audio resumes after server");
        pausedField.setBoolean(null, true);
        PersonalMusicPlayer.serverPlaying(true); PersonalMusicPlayer.serverPlaying(false);
        require(pausedField.getBoolean(null), "Manually paused track is not auto-resumed");
        PersonalMusicPlayer.stop();
        System.out.println("Library/audio checks passed: schemas, safe links, XML/SSRF protection, bounded queue, volume, server priority");
        SpotifyWebCheck.run();
        System.out.println("Music checks passed: PKCE, local callback, cancellation, privacy, search parsing and safe links");
    }
}
