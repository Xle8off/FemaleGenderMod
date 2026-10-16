package com.wildfire.main.networking.appearance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class MessagePackets {
    private MessagePackets() {}

    public record Request(byte kind, String partner) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("appearance_sync", "message_request"));
        public static final StreamCodec<ByteBuf, Request> CODEC = new StreamCodec<>() {
            public Request decode(ByteBuf input) {
                byte kind = input.readByte();
                return new Request(kind, kind >= 2 && kind <= 6 ? new FriendlyByteBuf(input).readUtf(16) : "");
            }
            public void encode(ByteBuf output, Request value) {
                output.writeByte(value.kind);
                if (value.kind >= 2 && value.kind <= 6) new FriendlyByteBuf(output).writeUtf(value.partner, 16);
            }
        };
        public Type<Request> type() { return TYPE; }
    }

    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath("appearance_sync", "message_snapshot"));
        public static final StreamCodec<ByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            public Snapshot decode(ByteBuf input) { return new Snapshot(new FriendlyByteBuf(input).readUtf(32767)); }
            public void encode(ByteBuf output, Snapshot value) { new FriendlyByteBuf(output).writeUtf(value.json, 32767); }
        };
        public Type<Snapshot> type() { return TYPE; }
    }
}
