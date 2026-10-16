package com.wildfire.main.networking.appearance;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record ChatStatePacket(String state) implements CustomPacketPayload {
    public static final Type<ChatStatePacket> TYPE=new Type<>(Identifier.fromNamespaceAndPath("appearance_sync","chat_state"));
    public static final StreamCodec<ByteBuf,ChatStatePacket> CODEC=new StreamCodec<>(){
        public ChatStatePacket decode(ByteBuf input){return new ChatStatePacket(new FriendlyByteBuf(input).readUtf(128));}
        public void encode(ByteBuf output,ChatStatePacket value){new FriendlyByteBuf(output).writeUtf(value.state(),128);}
    };
    public Type<ChatStatePacket> type(){return TYPE;}
}
