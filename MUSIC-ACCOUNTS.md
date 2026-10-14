# Music — beta.110, local experimental build

New separate Spotify Web Free/Premium prototype: see SPOTIFY-WEB.md.
The restrictions below on Spotify audio refer to the developer-API library mode,
not the new normal-site browser mode. Neither mode has been provider-tested.

Music-disc tab in the drawer opens a dedicated music screen on Fabric and NeoForge.

Implemented:
- Spotify PKCE S256, loopback callback at http://127.0.0.1:43821/spotify/callback.
  The public app ID is bundled; Spotify Client Secret is never requested or stored.
- Experimental Yandex Android-client device authorization, explicitly disclosed before login.
- Track search, favorites, add/remove favorites, personal playlists and playlist contents.
- Spotify uses the 2026 /playlists/{id}/items and /me/library APIs, with legacy item parsing.
  Spotify only exposes playlist contents owned by the user or collaborative playlists.
- Yandex personal daily playlist. This is NOT My Wave.
- Explicit “My Wave” external link opens Yandex Music in the browser.
- Yandex MP3 streaming: selected track, pause/resume, stop, previous/next, queue,
  shuffle, repeat off/all/one, seek via progress bar, volume.
  Only full non-preview MP3 variants supplied to the authorized account are accepted.
  No encrypted/DRM format decryption, offline files or disk audio cache.
- Personal Yandex playback replaces idle server music in the existing overlay.
  Server pv-discs playback pauses personal playback; it resumes afterward only if not manually paused.
  Overlay volume controls the active source.
- Compact layout below 320 GUI pixels, rounded action buttons and scrollable lists.
- Direct provider requests: nothing is routed through Paper, proxy, site or the core bridge.
- One in-flight operation per provider, at most two deferred requests, 15-second deferred deadline,
  60-second metadata cache capped at 32 pages, provider Retry-After respected.
  The browse list is capped at 1000 entries and the audio queue at 200.
- Tokens remain in memory only; restart requires reconnecting.
  Disconnect clears provider metadata and credentials, and stops Yandex audio.
- Audio URLs restricted to HTTPS Yandex hosts; no redirects or auth headers to audio hosts.
  XML external entities disabled; metadata body size and read time limited.

Not complete / not verified:
- Real Spotify and Yandex login/library/playback have NOT been tested on an authorized user account.
- Actual in-game visual appearance and JavaSound output device need runtime verification.
- Full My Wave radio session and listening feedback are NOT implemented.
- Spotify audio/control is NOT implemented; selected tracks open in the Spotify service.
  Do not publish the Spotify integration before resolving policy compatibility.
- No album artwork, public listening status above players, persistent login or offline downloads.
- pv-discs playlist skip/pause commands are not added by this client-only change.
- Seek currently reopens the MP3 stream and decodes/discards the prefix; it is not instant byte-range seeking.

Verification:
Both loaders build; PKCE/localhost callback tests, library-schema parsing, URL/XXE protection,
queue/volume/server-priority tests, cosmetic UV checks, and NeoForge early UI-load checks pass.
The JLayer MP3 decoder is bundled as a nested dependency (javazoom:jlayer:1.0.1, LGPL).
Audio-engine tests validate state/safety, not actual provider streaming or audible output.
These artifacts are local and have NOT been published.

Provider references:
https://developer.spotify.com/policy
https://developer.spotify.com/documentation/web-api/reference/get-playlists-items
https://developer.spotify.com/documentation/web-api/reference/save-library-items
https://ym.marshal.dev/token/
https://github.com/MarshalX/yandex-music-api/blob/main/yandex_music/_client/likes.py
https://github.com/MarshalX/yandex-music-api/blob/main/yandex_music/_client/playlists.py
https://github.com/MarshalX/yandex-music-api/blob/main/yandex_music/download_info.py
