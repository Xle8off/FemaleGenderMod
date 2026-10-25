package com.wildfire.client.screens;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.springplus.screens.ScreenProtocol;
public record ScreenPacket(byte[] data) implements CustomPacketPayload {
 public static final Type<ScreenPacket> TYPE=new Type<>(Identifier.fromNamespaceAndPath("springplus","screens"));
 public static final StreamCodec<ByteBuf,ScreenPacket> CODEC=new StreamCodec<>(){
  public ScreenPacket decode(ByteBuf b){if(b.readableBytes()>ScreenProtocol.MAX_BYTES)throw new IllegalArgumentException("Screen packet too large");byte[] bytes=new byte[b.readableBytes()];b.readBytes(bytes);return new ScreenPacket(bytes);}
  public void encode(ByteBuf b,ScreenPacket value){if(value.data.length>ScreenProtocol.MAX_BYTES)throw new IllegalArgumentException("Screen packet too large");b.writeBytes(value.data);}
 };
 public Type<ScreenPacket> type(){return TYPE;}
}
