# Public chat UI (beta.194)

## Built-in message animations

The chat gear contains local settings for appearance (off, fade, slide from the left,
rise from below), speed (fast/normal/slow), smooth insertion and smooth wheel scrolling.
Settings use the existing `config/springplus-chat-filters.txt` file and survive restarts.
The settings pane itself scrolls on small windows; clipped buttons are not clickable.

Both open history and closed HUD use the same monotonic arrival timestamp. Opening
the chat or changing tabs does not replay old messages. Text, emoji, stickers and
images share the appearance animation. Interactive text/media hitboxes follow their
actual animated coordinates.

Reading older history preserves the viewport anchor when messages arrive.
The scrollbar thumb follows the rendered scroll position; dragging stays direct
rather than lagging behind the pointer. Burst insertion displacement is capped at
120 pixels. No extra textures, render targets or per-frame asynchronous jobs are created.
The separate ChatAnimation mod is not required; this does not add an adapter for it.

Headless verification: `:26.2:checkChatMotion` (Fabric) or
`:neoforge:26.2:checkChatMotion` (NeoForge), with `-I tests/uv-check.gradle`.
Check GUI appearance in-game after installing the matching rebuilt JAR.

Requires SpringPlusChat 1.1.16 for explicit channel metadata and staff/spy capabilities. No new plugin-message channels are registered. The server pushes a strictly validated insertion marker on join and mode changes; the mod consumes it even before appearance session activation, without creating a visible chat line. The mod never polls `/chat ui`, so older plugins cannot spam command usage.

Tabs: All, Global, Local, Discord, Staff, System, SPY. Staff and SPY are hidden until their server permissions are confirmed. System and SPY only filter reading and do not change the sending mode. Server recipient selection, local radius, mutes, message cooldowns and permission checks remain unchanged. Discord reads relayed Discord messages; outgoing messages use global chat and its existing Discord relay. The settings gear controls which categories appear in All, including the closed HUD; excluded rows remain in their dedicated tabs. These preferences are local in config/springplus-chat-filters.txt, with background atomic disk writes.

Vanilla input remains responsible for commands, command suggestions, history and message submission. Typing a slash keeps the enhanced history visible, with native command suggestions rendered above it. The history has no opaque panel; the input uses a low-opacity background. A single 16px emoji/sticker button sits inside the right end of the input row. Image and send buttons are not shown. Unicode emoji rendering, installed sticker packs, favorites and recents reuse existing local preferences and web assets. Right-click a picker item to toggle its favorite status. Public sticker messages carry only a bounded sticker ID; clients without the mod see the textual sticker marker. No durable public history is added.

The enhanced history stores at most 200 messages for the current connection and clears on disconnect/connection change or native chat clear. Unknown/legacy messages appear in All as system messages, not guessed channels. The custom history retains component colors and text click/hover styles; native invisible click targets are disabled while enhanced history is displayed.

Only the bottom input row and its emoji button are horizontally centered. The history, channel tabs and picker retain the left chat anchor. The message composer uses the same multiline widget, rounded plate and vertically centered text-height calculation as private messages, and smoothly grows upward to a bounded height. Commands retain the native single-line input, suggestions and history. Closed-chat HUD uses the same component history with sticker/image previews, without native invisible click targets. Recent messages expire after ten seconds. Build and protocol checks passed for Fabric and NeoForge 26.2. In-game visual and interaction testing is still required. Hold-to-preview sticker animation is not implemented here.
