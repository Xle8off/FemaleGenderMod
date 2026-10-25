package com.wildfire.main.networking.appearance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RouteProbePacket(byte[] data) implements CustomPacketPayload {
    public static final Type<RouteProbePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("springplus", "route_probe"));
    public static final StreamCodec<ByteBuf, RouteProbePacket> CODEC = new StreamCodec<>() {
        @Override public RouteProbePacket decode(ByteBuf input) {
            if (input.readableBytes() > 4096) throw new IllegalArgumentException("Route probe packet too large");
            byte[] data = new byte[input.readableBytes()];
            input.readBytes(data);
            return new RouteProbePacket(data);
        }
        @Override public void encode(ByteBuf output, RouteProbePacket value) {
            if (value.data().length > 4096) throw new IllegalArgumentException("Route probe packet too large");
            output.writeBytes(value.data());
        }
    };
    @Override public Type<RouteProbePacket> type() { return TYPE; }
}
