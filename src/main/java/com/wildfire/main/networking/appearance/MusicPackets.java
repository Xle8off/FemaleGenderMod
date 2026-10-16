package com.wildfire.main.networking.appearance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class MusicPackets {
    private MusicPackets() {}
    public record NowPlaying(String id, String title, String position, String remaining) implements CustomPacketPayload {
        public static final Type<NowPlaying> TYPE = new Type<>(Identifier.fromNamespaceAndPath("appearance_sync", "music"));
        public static final StreamCodec<ByteBuf, NowPlaying> CODEC = new StreamCodec<>() {
            public NowPlaying decode(ByteBuf input) {
                FriendlyByteBuf buffer = new FriendlyByteBuf(input);
                return new NowPlaying(buffer.readUtf(96), buffer.readUtf(160), buffer.readUtf(16), buffer.readUtf(20));
            }
            public void encode(ByteBuf output, NowPlaying value) {
                FriendlyByteBuf buffer = new FriendlyByteBuf(output);
                buffer.writeUtf(value.id, 96);
                buffer.writeUtf(value.title, 160);
                buffer.writeUtf(value.position, 16);
                buffer.writeUtf(value.remaining, 20);
            }
        };
        public Type<NowPlaying> type() { return TYPE; }
    }
}
