package com.wildfire.main.networking.appearance;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record ListeningPacket(String json) implements CustomPacketPayload {
    public static final Type<ListeningPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("appearance_sync", "listening"));
    public static final StreamCodec<ByteBuf, ListeningPacket> CODEC = new StreamCodec<>() {
        public ListeningPacket decode(ByteBuf input) { return new ListeningPacket(new FriendlyByteBuf(input).readUtf(1024)); }
        public void encode(ByteBuf output, ListeningPacket value) { new FriendlyByteBuf(output).writeUtf(value.json,1024); }
    };
    public Type<ListeningPacket> type() { return TYPE; }
}

