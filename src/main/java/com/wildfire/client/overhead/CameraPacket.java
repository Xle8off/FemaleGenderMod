package com.wildfire.client.overhead;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record CameraPacket(String json) implements CustomPacketPayload {
    public static final Type<CameraPacket> TYPE=new Type<>(Identifier.fromNamespaceAndPath("appearance_sync","camera_control"));
    public static final StreamCodec<ByteBuf,CameraPacket> CODEC=new StreamCodec<>(){
        public CameraPacket decode(ByteBuf input){return new CameraPacket(new FriendlyByteBuf(input).readUtf(30000));}
        public void encode(ByteBuf output,CameraPacket value){new FriendlyByteBuf(output).writeUtf(value.json(),30000);}
    };
    public Type<CameraPacket> type(){return TYPE;}
}
