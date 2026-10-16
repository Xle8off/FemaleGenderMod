package com.wildfire.main.networking.appearance;

import com.mojang.blaze3d.platform.NativeImage;
import com.wildfire.main.WildfireGender;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** HD skin display and bounded, ordered PNG transfer. All methods modifying textures run on the client thread. */
public final class HdSkinClient {
    private static final int MAX_PENDING_BYTES = 16 * 1024 * 1024;
    private static final Map<UUID, Skin> SKINS = new HashMap<>();
    private static final Map<UUID, Transfer> TRANSFERS = new HashMap<>();
    private static int pendingBytes;

    private record Skin(int revision, int scale, boolean slim, Identifier id, DynamicTexture texture) {}

    private static final class Transfer {
        final int revision, x, y, width, height;
        final byte[] bytes;
        int received;

        Transfer(HdSkinPackets.Chunk chunk) {
            revision = chunk.revision();
            x = chunk.x(); y = chunk.y(); width = chunk.width(); height = chunk.height();
            bytes = new byte[chunk.totalBytes()];
        }

        boolean matches(HdSkinPackets.Chunk chunk) {
            return revision == chunk.revision() && x == chunk.x() && y == chunk.y()
                && width == chunk.width() && height == chunk.height() && bytes.length == chunk.totalBytes();
        }
    }

    private HdSkinClient() {}

    public static void registerClient() {
        ClientPlayConnectionEvents.INIT.register((listener, client) -> {
            ClientPlayNetworking.registerReceiver(HdSkinPackets.Chunk.TYPE,
                (packet, context) -> context.client().schedule(() -> receive(packet)));
            ClientPlayNetworking.registerReceiver(HdSkinPackets.Clear.TYPE,
                (packet, context) -> context.client().schedule(() -> clear(packet.player())));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> clearAll());
    }

    public static int scale(UUID player) {
        Skin skin = SKINS.get(player);
        return skin == null ? 1 : skin.scale();
    }

    public static Identifier texture(UUID player) {
        Skin skin = SKINS.get(player);
        return skin == null ? null : skin.id();
    }

    /** Server-provided skins are authoritative, including the absence of a server cape.
     * Unmanaged players (and other servers after disconnect) retain their original providers. */
    public static PlayerSkin skin(UUID player, PlayerSkin original) {
        Identifier id = texture(player);
        if (original == null) return null;
        ClientAsset.Texture cape = com.wildfire.client.config.CosmeticPreferences.cosmeticsVisible()
            ? CapeClient.texture(player) : null;
        return id == null && cape == null ? original : new PlayerSkin(
            id == null ? original.body() : new ClientAsset.ResourceTexture(id, id),
            cape == null && id == null ? original.cape() : cape,
            cape == null && id == null ? original.elytra() : cape,
            id == null ? original.model() : (SKINS.get(player).slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE), original.secure());
    }

    public static void receive(HdSkinPackets.Chunk chunk) {
        if (!validRegion(chunk)) {
            if (isSelf(chunk.player())) WildfireGender.LOGGER.warn("[skin-sync] Invalid HD skin chunk for self revision={} offset={} total={}",
                chunk.revision(), chunk.offset(), chunk.totalBytes());
            return;
        }
        Skin current = SKINS.get(chunk.player());
        if (current != null && chunk.revision() <= current.revision()) {
            if (isSelf(chunk.player()) && chunk.offset() == 0) WildfireGender.LOGGER.info(
                "[skin-sync] Ignoring own HD skin revision={} installedRevision={}", chunk.revision(), current.revision());
            return;
        }
        Transfer transfer = TRANSFERS.get(chunk.player());
        if (transfer != null && chunk.revision() < transfer.revision) return;
        if (transfer == null || !transfer.matches(chunk)) {
            // A new transfer always starts at zero. Never combine chunks from different revisions/regions.
            if (chunk.offset() != 0) {
                if (isSelf(chunk.player())) WildfireGender.LOGGER.warn("[skin-sync] Own HD skin transfer starts at offset={}", chunk.offset());
                return;
            }
            if (transfer != null) dropTransfer(chunk.player());
            if (pendingBytes + chunk.totalBytes() > MAX_PENDING_BYTES) {
                if (isSelf(chunk.player())) WildfireGender.LOGGER.warn("[skin-sync] Own HD skin exceeds pending budget: pending={} total={}",
                    pendingBytes, chunk.totalBytes());
                return;
            }
            transfer = new Transfer(chunk);
            TRANSFERS.put(chunk.player(), transfer);
            pendingBytes += transfer.bytes.length;
            if (isSelf(chunk.player())) WildfireGender.LOGGER.info(
                "[skin-sync] Receiving own HD skin revision={} size={}x{} bytes={}",
                chunk.revision(), chunk.width(), chunk.height(), chunk.totalBytes());
        }
        if (chunk.offset() != transfer.received) {
            if (isSelf(chunk.player())) WildfireGender.LOGGER.warn("[skin-sync] Own HD skin out of order: offset={} expected={}",
                chunk.offset(), transfer.received);
            return;
        }
        System.arraycopy(chunk.data(), 0, transfer.bytes, transfer.received, chunk.data().length);
        transfer.received += chunk.data().length;
        if (transfer.received == transfer.bytes.length) {
            dropTransfer(chunk.player());
            install(chunk.player(), transfer);
        }
    }

    private static boolean validRegion(HdSkinPackets.Chunk chunk) {
        if (chunk.revision() < 0 || chunk.totalBytes() < 1 || chunk.totalBytes() > HdSkinPackets.MAX_PNG_BYTES
            || chunk.data().length < 1 || chunk.data().length > HdSkinPackets.CHUNK_BYTES) return false;
        if (chunk.x() == -1 && chunk.y() == -1)
            return chunk.width() >= 64 && chunk.width() <= 2048 && chunk.width() == chunk.height()
                && chunk.width() % 64 == 0 && Integer.bitCount(chunk.width() / 64) == 1;
        return chunk.x() >= 0 && chunk.y() >= 0 && chunk.width() > 0 && chunk.height() > 0
            && chunk.width() <= 2048 && chunk.height() <= 2048
            && (long) chunk.x() + chunk.width() <= 2048 && (long) chunk.y() + chunk.height() <= 2048;
    }

    private static void install(UUID player, Transfer transfer) {
        // Check PNG IHDR before asking the native decoder to allocate memory.
        byte[] bytes = transfer.bytes;
        if (bytes.length < 24 || !Arrays.equals(Arrays.copyOf(bytes, 8),
            new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})
            || bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R'
            || int32(bytes, 16) != transfer.width || int32(bytes, 20) != transfer.height) {
            if (isSelf(player)) WildfireGender.LOGGER.warn("[skin-sync] Own HD skin rejected: PNG header or dimensions mismatch");
            return;
        }
        try (NativeImage image = NativeImage.read(bytes)) {
            if (image.getWidth() != transfer.width || image.getHeight() != transfer.height) {
                if (isSelf(player)) WildfireGender.LOGGER.warn("[skin-sync] Own HD skin rejected: decoded dimensions mismatch");
                return;
            }
            if (transfer.x == -1) installFull(player, transfer.revision, image);
            else installPatch(player, transfer, image);
        } catch (IOException | RuntimeException invalid) {
            WildfireGender.LOGGER.warn("Rejected HD skin PNG for {}: {}", player, invalid.toString());
        }
    }

    private static int int32(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16
            | (bytes[offset + 2] & 255) << 8 | bytes[offset + 3] & 255;
    }

    private static void installFull(UUID player, int revision, NativeImage image) {
        boolean slim = isSlim(image);
        NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), true);
        copy.copyFrom(image);
        Identifier id = Identifier.fromNamespaceAndPath("appearance_sync", "hd_skin/" + player.toString().replace('-', '_'));
        clearOnClientThread(player);
        DynamicTexture texture = new DynamicTexture(() -> "HD skin " + player, copy);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.upload();
        SKINS.put(player, new Skin(revision, image.getWidth() / 64, slim, id, texture));
        if (isSelf(player)) WildfireGender.LOGGER.info("[skin-sync] Installed own HD skin revision={} size={}x{} slim={}",
            revision, image.getWidth(), image.getHeight(), slim);
    }

    private static void installPatch(UUID player, Transfer transfer, NativeImage patch) {
        Skin skin = SKINS.get(player);
        if (skin == null || transfer.revision <= skin.revision()) return;
        NativeImage target = skin.texture().getPixels();
        if (target == null || transfer.x + patch.getWidth() > target.getWidth()
            || transfer.y + patch.getHeight() > target.getHeight()) return;
        int[] pixels = patch.getPixelsABGR();
        for (int y = 0; y < patch.getHeight(); y++)
            for (int x = 0; x < patch.getWidth(); x++)
                target.setPixelABGR(transfer.x + x, transfer.y + y, pixels[y * patch.getWidth() + x]);
        boolean slim = isSlim(target);
        skin.texture().upload();
        SKINS.put(player, new Skin(transfer.revision, skin.scale(), slim, skin.id(), skin.texture()));
    }

    private static boolean isSlim(NativeImage image) {
        int scale = image.getWidth() / 64;
        if (scale < 1) return false;
        int[] pixels = image.getPixelsABGR();
        // Slim arms use three-pixel front/back faces; columns 54 and 46 are outside their UVs.
        return transparentStrip(pixels, image.getWidth(), 54, 20, 12, scale)
            && transparentStrip(pixels, image.getWidth(), 46, 52, 12, scale)
            && opaqueStrip(pixels, image.getWidth(), 53, 20, 12, scale)
            && opaqueStrip(pixels, image.getWidth(), 45, 52, 12, scale);
    }

    private static boolean transparentStrip(int[] pixels, int width, int x, int y, int height, int scale) {
        for (int row = y * scale; row < (y + height) * scale; row++)
            for (int col = x * scale; col < (x + 1) * scale; col++)
                if ((pixels[row * width + col] >>> 24) != 0) return false;
        return true;
    }

    private static boolean opaqueStrip(int[] pixels, int width, int x, int y, int height, int scale) {
        for (int row = y * scale; row < (y + height) * scale; row++)
            for (int col = x * scale; col < (x + 1) * scale; col++)
                if ((pixels[row * width + col] >>> 24) != 0) return true;
        return false;
    }

    private static void dropTransfer(UUID player) {
        Transfer removed = TRANSFERS.remove(player);
        if (removed != null) pendingBytes -= removed.bytes.length;
    }

    private static boolean isSelf(UUID player) {
        var local = Minecraft.getInstance().player;
        return local != null && player.equals(local.getUUID());
    }

    public static void clear(UUID player) {
        Minecraft.getInstance().execute(() -> clearOnClientThread(player));
    }
    private static void clearOnClientThread(UUID player) {
        dropTransfer(player);
        Skin removed = SKINS.remove(player);
        if (removed != null) Minecraft.getInstance().getTextureManager().release(removed.id());
    }

    public static void clearAll() {
        Minecraft.getInstance().execute(() -> {
            for (UUID player : SKINS.keySet().toArray(UUID[]::new)) clearOnClientThread(player);
            TRANSFERS.clear();
            pendingBytes = 0;
        });
    }
}
