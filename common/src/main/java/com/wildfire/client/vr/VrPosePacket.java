package com.wildfire.client.vr;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record VrPosePacket(byte[] data) implements CustomPacketPayload {
 public static final Type<VrPosePacket> TYPE=new Type<>(Identifier.fromNamespaceAndPath("vivecraft","data"));
 public static final StreamCodec<ByteBuf,VrPosePacket> CODEC=new StreamCodec<>(){
  public VrPosePacket decode(ByteBuf b){if(b.readableBytes()<1||b.readableBytes()>2048)throw new IllegalArgumentException("Vivecraft payload length");byte[] bytes=new byte[b.readableBytes()];b.readBytes(bytes);return new VrPosePacket(bytes);}
  public void encode(ByteBuf b,VrPosePacket p){if(p.data.length<1||p.data.length>2048)throw new IllegalArgumentException();b.writeBytes(p.data);}
 };
 public Type<VrPosePacket> type(){return TYPE;}
}
