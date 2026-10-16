package com.wildfire.main.networking.appearance;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.UUID;

/** Clientbound protocol for a separate skin plugin. PNG data is split into ordered 16 KiB chunks. */
public final class HdSkinPackets {
    public static final int VERSION = 1;
    public static final int CHUNK_BYTES = 16 * 1024;
    public static final int MAX_PNG_BYTES = 8 * 1024 * 1024;

    private HdSkinPackets() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(Chunk.TYPE, Chunk.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Clear.TYPE, Clear.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CapeChunk.TYPE, CapeChunk.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CapeClear.TYPE, CapeClear.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GarmentChunk.TYPE, GarmentChunk.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GarmentClear.TYPE, GarmentClear.CODEC);
    }

    public record Chunk(UUID player, int revision, int x, int y, int width, int height,
                        int totalBytes, int offset, byte[] data) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(Identifier.fromNamespaceAndPath("appearance_sync", "hd_skin_chunk"));
        public static final StreamCodec<ByteBuf, Chunk> CODEC = new StreamCodec<>() {
            public Chunk decode(ByteBuf input) {
                UUID player = UUIDUtil.STREAM_CODEC.decode(input);
                int revision = VarInt.read(input);
                int x = VarInt.read(input), y = VarInt.read(input);
                int width = VarInt.read(input), height = VarInt.read(input);
                int total = VarInt.read(input), offset = VarInt.read(input), length = VarInt.read(input);
                if (revision < 0 || total < 1 || total > MAX_PNG_BYTES || length < 1
                    || length > CHUNK_BYTES || offset < 0 || offset > total - length || input.readableBytes() < length)
                    throw new IllegalArgumentException("Invalid HD skin chunk");
                byte[] data = new byte[length];
                input.readBytes(data);
                return new Chunk(player, revision, x, y, width, height, total, offset, data);
            }

            public void encode(ByteBuf output, Chunk value) {
                UUIDUtil.STREAM_CODEC.encode(output, value.player);
                VarInt.write(output, value.revision);
                VarInt.write(output, value.x);
                VarInt.write(output, value.y);
                VarInt.write(output, value.width);
                VarInt.write(output, value.height);
                VarInt.write(output, value.totalBytes);
                VarInt.write(output, value.offset);
                VarInt.write(output, value.data.length);
                output.writeBytes(value.data);
            }
        };

        @Override public Type<Chunk> type() { return TYPE; }
    }

    public record Clear(UUID player) implements CustomPacketPayload {
        public static final Type<Clear> TYPE = new Type<>(Identifier.fromNamespaceAndPath("appearance_sync", "hd_skin_clear"));
        public static final StreamCodec<ByteBuf, Clear> CODEC = new StreamCodec<>() {
            public Clear decode(ByteBuf input) { return new Clear(UUIDUtil.STREAM_CODEC.decode(input)); }
            public void encode(ByteBuf output, Clear value) { UUIDUtil.STREAM_CODEC.encode(output, value.player); }
        };

        @Override public Type<Clear> type() { return TYPE; }
    }

    public record CapeChunk(UUID player, int revision, int width, int height,
                            int totalBytes, int offset, byte[] data) implements CustomPacketPayload {
        public static final Type<CapeChunk> TYPE = new Type<>(Identifier.fromNamespaceAndPath("appearance_sync", "cape_chunk"));
        public static final StreamCodec<ByteBuf, CapeChunk> CODEC = new StreamCodec<>() {
            public CapeChunk decode(ByteBuf input) {
                Chunk value = Chunk.CODEC.decode(input);
                if (value.x() != -1 || value.y() != -1) throw new IllegalArgumentException("Invalid cape chunk");
                return new CapeChunk(value.player(), value.revision(), value.width(), value.height(),
                    value.totalBytes(), value.offset(), value.data());
            }
            public void encode(ByteBuf output, CapeChunk value) {
                Chunk.CODEC.encode(output, new Chunk(value.player(), value.revision(), -1, -1,
                    value.width(), value.height(), value.totalBytes(), value.offset(), value.data()));
            }
        };
        @Override public Type<CapeChunk> type() { return TYPE; }
    }

    public record CapeClear(UUID player) implements CustomPacketPayload {
        public static final Type<CapeClear> TYPE = new Type<>(Identifier.fromNamespaceAndPath("appearance_sync", "cape_clear"));
        public static final StreamCodec<ByteBuf, CapeClear> CODEC = new StreamCodec<>() {
            public CapeClear decode(ByteBuf input) { return new CapeClear(UUIDUtil.STREAM_CODEC.decode(input)); }
            public void encode(ByteBuf output, CapeClear value) { UUIDUtil.STREAM_CODEC.encode(output, value.player); }
        };
        @Override public Type<CapeClear> type() { return TYPE; }
    }

    /** Slot 0 is shirt, slot 1 is pants. Uses the same ordered, bounded transfer scheme as skins. */
    public record GarmentChunk(UUID player, int slot, int revision, int width, int height,
                               int totalBytes, int offset, byte[] data) implements CustomPacketPayload {
        public static final Type<GarmentChunk> TYPE = new Type<>(Identifier.fromNamespaceAndPath("appearance_sync", "garment_chunk"));
        public static final StreamCodec<ByteBuf, GarmentChunk> CODEC = new StreamCodec<>() {
            public GarmentChunk decode(ByteBuf input) {
                UUID player = UUIDUtil.STREAM_CODEC.decode(input);
                int slot = VarInt.read(input), revision = VarInt.read(input);
                int width = VarInt.read(input), height = VarInt.read(input);
                int total = VarInt.read(input), offset = VarInt.read(input), length = VarInt.read(input);
                if (slot < 0 || slot > 1 || revision < 0 || total < 1 || total > MAX_PNG_BYTES
                    || length < 1 || length > CHUNK_BYTES || offset < 0 || offset > total - length
                    || input.readableBytes() < length) throw new IllegalArgumentException("Invalid garment chunk");
                byte[] data = new byte[length];input.readBytes(data);
                return new GarmentChunk(player, slot, revision, width, height, total, offset, data);
            }
            public void encode(ByteBuf output, GarmentChunk value) {
                UUIDUtil.STREAM_CODEC.encode(output, value.player);
                for(int number : new int[]{value.slot,value.revision,value.width,value.height,value.totalBytes,value.offset,value.data.length})
                    VarInt.write(output, number);
                output.writeBytes(value.data);
            }
        };
        @Override public Type<GarmentChunk> type() { return TYPE; }
    }

    public record GarmentClear(UUID player, int slot) implements CustomPacketPayload {
        public static final Type<GarmentClear> TYPE = new Type<>(Identifier.fromNamespaceAndPath("appearance_sync", "garment_clear"));
        public static final StreamCodec<ByteBuf, GarmentClear> CODEC = new StreamCodec<>() {
            public GarmentClear decode(ByteBuf input) {
                UUID player = UUIDUtil.STREAM_CODEC.decode(input);int slot = VarInt.read(input);
                if(slot < 0 || slot > 1) throw new IllegalArgumentException("Invalid garment slot");
                return new GarmentClear(player,slot);
            }
            public void encode(ByteBuf output, GarmentClear value) {
                UUIDUtil.STREAM_CODEC.encode(output,value.player);VarInt.write(output,value.slot);
            }
        };
        @Override public Type<GarmentClear> type() { return TYPE; }
    }
}
