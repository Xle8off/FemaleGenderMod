# Overhead media and camera setup (Fabric, local beta.204)

The mod has an independent world-space billboard renderer, native camera capture,
local preview, and a Cloudflare SFU signaling/WebRTC adapter. This is not yet tested
with real camera hardware or a real CF app. No production deployment is part of this
change. Never claim working RF connectivity or hardware-accelerated encode/decode
without actual device/network tests.

Bell footer camera button opens a centred 620x340 modal like the voting window.
Enumerating devices does not start capture. Selecting a camera starts **local only**
preview. Device/resolution/FPS dropdowns only expose eligible device modes up to
854x480 / 30fps. YUY2/MJPEG is negotiated by WebRTC, no nonfunctional format selector.
Capture defensively scales frames to these bounds and drops excess frames before
encoding. Enable is gated by preview readiness and a recent successful coordinator
response. Cancel, Escape or close disposes an unpublished preview and its GPU texture.
Once enabled, closing the settings window keeps the explicitly enabled broadcast;
use the same button/window to stop it. Closing while connection is pending cancels it.

Native dependency: dev.onvoid.webrtc:webrtc-java:0.19.0; bundled pinned native JARs
for Windows/Linux/macOS. No browser, microphone/audio track, media custom payloads,
disk recordings, camera device IDs or SDP logging. Native tests use synthetic buffers
only and must not open hardware. Keep upstream native license notices in nested JARs.

## Settings

Bell → Settings → Appearance:

- `Над головой`: Messages / Camera / Off. A saved Camera mode never starts capture.
- `Сообщения над головой`: whether this client renders mod message bubbles.
- `Показывать чужие камеры`: explicit viewer opt-in, off by default.

Preferences are local. The owner-selected camera mode replaces mod text/sticker
bubbles even if a frame is absent/paused. Off renders neither. Remote camera metadata
expires after 15 seconds without refresh; frames older than 3 seconds are not shown
as live. A stopped camera returns to message mode. Message lifetimes are at most 10s.
Own first-person view, invisible/sneaking players, GUI mannequin render passes and
players beyond 32 blocks are excluded. Cameras do not render through terrain.

## Renderer adapter API

`OverheadMedia.INSTANCE` is synchronized state, with no dependency on a networking
library. All UUIDs and public content must be authenticated/approved by the
server integration; these methods are not a wire protocol or an authorization layer.

```java
long now = System.nanoTime(); // monotonic arrival time, NOT a remote clock
OverheadMedia.INSTANCE.message(playerUuid, messageId, approvedPublicText,
    8_000_000_000L, now);
OverheadMedia.INSTANCE.mode(playerUuid, OverheadMedia.Mode.OFF, now);

// New live session: use a fresh random UUID per start, not a permanent player ID.
OverheadMedia.INSTANCE.beginCamera(playerUuid, sessionUuid, now);
OverheadMedia.INSTANCE.cameraStatus(playerUuid, sessionUuid,
    OverheadMedia.CameraStatus.LIVE, now);
// Feed decoded RGBA8 on the client thread through the consent-aware facade:
OverheadCameraTextures.acceptFrame(playerUuid, sessionUuid, sequence,
    width, height, rgbaBytes);
OverheadMedia.INSTANCE.cameraStatus(playerUuid, sessionUuid,
    OverheadMedia.CameraStatus.PAUSED, System.nanoTime());
OverheadMedia.INSTANCE.endCamera(playerUuid, sessionUuid);
OverheadMedia.INSTANCE.remove(playerUuid); // player left / adapter loses subscription
```

Local mode must also allow Camera before local preview frames are accepted. Camera
capture needs a separate explicit, revocable device action; never start a device from
saved preferences. Paused/waiting and live are distinct; the live label appears only
with a visible, current video texture. `cameraStatus` is a heartbeat and accepts only
the current unexpired session. Old sessions cannot stop/overwrite newer sessions.

Limits: 128 player states, 8 pending raw frames and 8 visible GPU textures, dimensions
16..854 by 16..480, up to 30 accepted frames/sec/player, strictly increasing sequences.
Input is copied, pending data is latest-frame-only, and at most two GPU uploads happen
per tick. Cameras use a 48-segment circular crop, centred for rectangular input.
Texture uploads/releases occur on the client thread; textures expire outside visibility.
Disconnect clears all media state and GPU textures. No disk video cache exists.

Messages support bundled emoji and `[Стикер:pack/id]`; stickers reuse WebStickers'
bounded asynchronous cache and animation atlas. Raw public-chat text is **not** parsed
locally to guess a speaker: that would permit impersonation or leaking staff/private
messages. There is no public-message transport wired yet.

## HeadMessage / TextDisplay integration still required

Existing HeadMessage ArmorStands (or future server TextDisplays) are independent entities.
The mod cannot yet tell the server to hide them when Camera/Off is selected. A future
authenticated mode/presence channel must make the server suppress those displays, and
send approved public-message UUID/body/expiry events to mod clients without duplicates.
No HeadMessage/Paper plugin was modified by this foundation. Non-mod players continue
to see whatever the existing server plugin shows until that integration is implemented.

Choose transport and capture/encoding separately; do not send video in Minecraft
custom payloads. The state/renderer needs only mode/session events and decoded frames.

## Visibility subscriptions

The client-thread `OverheadCameraTextures.subscriptions()` snapshot exposes desired
owner/session subscriptions for the SFU adapter. The adapter MUST unsubscribe
removed entries; merely hiding their textures does not save network traffic.
Distance beyond 32 blocks from the local player stops demand immediately (next client
tick). Offscreen cameras retain demand for less than three seconds since their last
world-render submission. Leaving the level, changing worlds or ending a camera session
removes demand without that grace. Only live decoded textures render a circle: no
waiting/off placeholder. Textures are released when demand ends, and frames without
demand are rejected. CameraClient creates receiving peers only for wanted cameras,
closes removed peers locally and requests forced SFU closure. Core pushes stopped
camera events to current subscribers so they hide the circle without waiting for a
poll. Camera metadata uses a five-second state/heartbeat refresh. Changing worlds
or disconnecting stops capture and local peers. CF failures may delay server cleanup.

## Activation / remaining work

Update SpringPlusCore-Velocity too, configure cameras.cloudflare.app-id/app-secret
and cameras.enabled=true. An unconfigured coordinator leaves Enable disabled while
local preview still works. Viewing remote cameras requires the existing explicit
"Показывать чужие камеры" opt-in. Video is capped at 800,000 bits/s by native sender
parameters, 30fps max. Idle publications stop feeding video when coordinator reports
zero subscribers (up to five seconds delay); local camera remains open/previewing.
No TURN credential support yet: restrictive networks may fail to connect.
Cross-proxy discovery, server-enforced coordinates/world visibility and hiding
HeadMessage entities are still not implemented. An SFU does not transcode: no hard
server guarantee against modified clients sending oversized encoded media. Receiver
checks decoded frame bounds but cannot undo decoder allocation that already occurred.
