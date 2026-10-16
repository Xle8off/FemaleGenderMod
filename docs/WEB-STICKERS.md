# Web-hosted chat stickers

Beta.166 loads a trusted catalog from `https://skin.springplus.net/api/stickers/catalog`.
The Samson pack contains 36 original static stickers from the user-provided archive.
Only the basic emoji font is bundled in the mod; Samson PNGs are not in the JAR.

Messages store `[Стикер:samson_vk/3710957]`-style stable IDs through the existing private
message path. Only catalog-listed IDs resolve to images. Older clients see plain tokens.
Removed or unknown IDs remain readable text; private message history is not modified.

PNG requests use `/api/stickers/image/<sha256>.png` on the same fixed HTTPS host.
The client rejects redirects, oversized HTTP bodies, unsupported catalog versions,
duplicate IDs, paths, excessive dimensions and mismatched SHA-256/PNG headers.
Limits: 8 packs, 128 entries, 512x512, 1 MiB per image, 128 KiB catalog.
Downloads/decode/cache writes run on two background workers; texture registration
runs on the Minecraft thread. Concurrent requests are deduplicated and failed requests
back off for 30 seconds. At most 64 GPU textures and 128 disk PNGs are retained.
Cache: `<game directory>/cache/springplus-stickers`. Catalog refresh is every 5 minutes
while the chat UI is open. Downloaded IDs can be displayed from cache when the site
is temporarily unavailable. Nothing is activated without the SpringPlus session gate.

Picker: compact send/reaction buttons, Emoji/Stickers tabs, pack preview buttons,
paged previews and a larger sticker card. Sticker sends preserve the text draft.
All loading placeholders use the current theme; no third-party URL can be sent as media.

Website administration lives in `profileskinsys/scripts/import-sticker-pack.mjs`:

```
node scripts/import-sticker-pack.mjs DATA_DIR PACK_ID PACK_LABEL PNG_DIRECTORY
```

The admin importer validates PNGs, writes immutable hash filenames and publishes a
catalog atomically. Adding or updating packs needs no mod rebuild or website restart.
Keep IDs stable to preserve old messages. Catalog removal does not delete image files.

Verification: `checkWebStickers`, `checkChatReactions`, existing music and toast checks.
In-game layout, cursor/focus and sender/recipient delivery still require a live test.
