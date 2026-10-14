# Spotify Web — beta.110, experimental local build

Entry: Music → “Web Free/Prem”. This mode is independent of Spotify developer OAuth and Web Playback SDK.

Implemented:
- Launches an INSTALLED Edge/Chrome browser as a separate process, NOT an embedded/bundled browser.
  Edge is preferred on Windows. Chrome/Edge/Chromium paths are also probed on Linux/macOS,
  but non-Windows support has not been exercised.
- Browser profile: <Minecraft game directory>/config/springplus/spotify-web-profile.
  Normal browser profiles are never attached, read or modified.
- Remote-debugging socket bound to loopback, dynamically assigned port and browser UUID.
  The port file is validated; no wildcard origins, sandbox disabling or certificate exceptions.
  Local processes with access to the debugger can access this isolated profile: do not use it
  for unrelated browsing or accounts. The port is not exposed to the game server or the network.
- Login is performed by the user on the normal Spotify site. Mod code does not read passwords,
  cookies or account tokens. The browser itself persists login in the separate profile.
- “Reset login” clears cookies in this dedicated browser and site storage for Spotify origins.
  It does not delete files or modify the user's normal browser profile.
- Show window, move window offscreen, close own browser, shutdown cleanup.
  Offscreen is NOT a completely invisible embedded view: a taskbar/dock icon can remain.
- Experimental site-DOM controls: play/pause, previous/next, volume and seek.
- Search, favorites, playlists and recommendations open the matching Spotify pages.
  Selection is currently performed in the normal browser window, not an entirely native mod UI.
- Optional metadata parsing: title, artist, playback clock and state, displayed in the mod/overlay.
- Server music priority attempts to pause/resume the web player.
- Starting Spotify stops native Yandex playback; starting Yandex closes the Spotify browser.
- All music remains in the normal Spotify player. No audio extraction, DRM bypass,
  ad suppression, premium spoofing or download/offline feature.
- Free/Premium behavior is determined by Spotify itself, not the mod.
  Availability of a Free web session may vary with service/region/account policy.
- Commands are bounded to four queued operations; metadata polls every 1.5 seconds
  over LOCAL WebSocket, with timeouts and failure cleanup. No polling through the core bridge.

Verified:
- Both loader builds and offline checks pass.
- Loopback endpoint validation, canonical Spotify URLs, playback-time parsing.
- Mock WebSocket/CDP handshake, fragmented replies, protocol-error handling and pending-request cleanup.
- Existing account security/library/native-player tests and NeoForge early UI-load checks.

NOT verified:
- A real Spotify Free/Premium login, DRM playback, offscreen playback, DOM selector compatibility,
  actual browser-process cleanup and end-to-end in-game runtime.
- The implementation is intentionally experimental. A browser/Spotify website update may break controls;
  show the normal player window to operate it manually.
- Technical feasibility does not establish permission under Spotify developer/service terms.
- This build has NOT been published.

Usage test:
1. Open Music → Web Free/Prem → “Login / show Spotify”.
2. Sign in in the dedicated browser window and start a track.
3. Try pause/next/volume in the mod, then “Background”.
4. Report the displayed status if playback or controls fail.
5. “Stop” closes this player; “Reset login” logs it out while keeping the normal browser untouched.

Previous library/native Yandex details are recorded in MUSIC-ACCOUNTS.md.

