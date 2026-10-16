package com.wildfire.client.towns;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One optional bidirectional channel; protocol bytes are shared with Paper. */
public record TownsPacket(byte[] data) implements CustomPacketPayload {
    public static final int MAX_BYTES=30000;
    public static final Type<TownsPacket> TYPE=new Type<>(Identifier.fromNamespaceAndPath("towns","ui"));
    public static final StreamCodec<ByteBuf,TownsPacket> CODEC=new StreamCodec<>() {
        public TownsPacket decode(ByteBuf b){
            if(b.readableBytes()>MAX_BYTES)throw new IllegalArgumentException("Town packet too large");
            byte[] data=new byte[b.readableBytes()];b.readBytes(data);return new TownsPacket(data);
        }
        public void encode(ByteBuf b,TownsPacket p){
            if(p.data.length>MAX_BYTES)throw new IllegalArgumentException("Town packet too large");
            b.writeBytes(p.data);
        }
    };
    public Type<TownsPacket> type(){return TYPE;}
}
