# HD skin transport (client protocol v1)

The skin-storage server plugin is separate. It may send these clientbound Fabric custom payloads to clients with FemaleGenderMod. The client only renders received skins; it does not store or request them. Send a full skin when a viewer joins or first sees a player, then send changed rectangular regions as patches.

`appearance_sync:hd_skin_chunk` contains, in order:

| Field | Encoding | Meaning |
| --- | --- | --- |
| player | Minecraft UUID stream codec (two longs) | Skin owner |
| revision | VarInt | Monotonically increasing per player for this viewer |
| x, y | VarInt each | `-1, -1` for a full atlas; otherwise top-left of patch |
| width, height | VarInt each | PNG dimensions |
| totalBytes | VarInt | Entire PNG byte count |
| offset | VarInt | Offset into the PNG byte stream |
| length | VarInt | Bytes in this chunk, at most 16,384 |
| data | raw bytes | PNG bytes |

Chunks must arrive in offset order, starting at zero. Metadata must match for every chunk of a transfer. A complete PNG is decoded only after all bytes arrive. PNGs are already compressed; do not wrap them in additional zlib. Full skins must be square, 64–2048 pixels wide, with a power-of-two `width / 64` (64, 128, 256, 512, 1024, 2048). They use the standard Minecraft skin UV layout at higher pixel density. Patches can be any rectangle within the existing atlas and must have a revision greater than the installed skin. Each patch advances the revision, so send distinct increasing revisions for successive patches. A viewer needs the full atlas before patches. The PNG limit is 8 MiB. Unfinished transfers share a 16 MiB client memory cap.

`appearance_sync:hd_skin_clear` contains only the player UUID and removes their custom texture. Send this when the skin is removed or the player leaves the viewer's scope. All textures are removed automatically when the viewer disconnects.

The future server plugin should check that a client accepts these channels before sending, send full skins on join/tracking, and distribute patches only to relevant viewers. The skin storage and patch-detection implementation are intentionally outside FemaleGenderMod.
