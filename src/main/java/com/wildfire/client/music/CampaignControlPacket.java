package com.wildfire.client.music;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record CampaignControlPacket(String json) implements CustomPacketPayload {
    public static final Type<CampaignControlPacket> TYPE=new Type<>(Identifier.fromNamespaceAndPath("appearance_sync","notice_control"));
    public static final StreamCodec<ByteBuf,CampaignControlPacket> CODEC=new StreamCodec<>(){
        public CampaignControlPacket decode(ByteBuf input){return new CampaignControlPacket(new FriendlyByteBuf(input).readUtf(4096));}
        public void encode(ByteBuf output,CampaignControlPacket value){new FriendlyByteBuf(output).writeUtf(value.json(),4096);}
    };
    public Type<CampaignControlPacket> type(){return TYPE;}
}
