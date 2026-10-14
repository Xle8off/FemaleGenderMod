package com.wildfire.neoforge.appearance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class SocialPackets {
    private SocialPackets() {}
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("appearance_sync", name));
    }
    public record Request(byte value) implements CustomPacketPayload {
        public static final Type<Request> TYPE = SocialPackets.type("social_request");
        public static final StreamCodec<ByteBuf, Request> CODEC = new StreamCodec<>() {
            public Request decode(ByteBuf input) { return new Request(input.readByte()); }
            public void encode(ByteBuf output, Request value) { output.writeByte(value.value); }
        };
        public Type<Request> type() { return TYPE; }
    }
    public record Action(String action, String target) implements CustomPacketPayload {
        public static final Type<Action> TYPE = SocialPackets.type("social_action");
        public static final StreamCodec<ByteBuf, Action> CODEC = new StreamCodec<>() {
            public Action decode(ByteBuf input) {
                FriendlyByteBuf buffer = new FriendlyByteBuf(input);
                return new Action(buffer.readUtf(8), buffer.readUtf(16));
            }
            public void encode(ByteBuf output, Action value) {
                FriendlyByteBuf buffer = new FriendlyByteBuf(output);
                buffer.writeUtf(value.action, 8);
                buffer.writeUtf(value.target, 16);
            }
        };
        public Type<Action> type() { return TYPE; }
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = SocialPackets.type("social_snapshot");
        public static final StreamCodec<ByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            public Snapshot decode(ByteBuf input) { return new Snapshot(new FriendlyByteBuf(input).readUtf(32767)); }
            public void encode(ByteBuf output, Snapshot value) { new FriendlyByteBuf(output).writeUtf(value.json, 32767); }
        };
        public Type<Snapshot> type() { return TYPE; }
    }
}
