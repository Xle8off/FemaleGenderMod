package com.wildfire.client.music;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public final class ApiNoticePackets {
    private ApiNoticePackets() {}
    public record Notice(String json) implements CustomPacketPayload {
        public static final Type<Notice> TYPE=new Type<>(Identifier.fromNamespaceAndPath("appearance_sync","api_notification"));
        public static final StreamCodec<ByteBuf,Notice> CODEC=new StreamCodec<>() {
            public Notice decode(ByteBuf b){return new Notice(new FriendlyByteBuf(b).readUtf(32768));}
            public void encode(ByteBuf b,Notice p){new FriendlyByteBuf(b).writeUtf(p.json,32768);}
        };
        public Type<Notice> type(){return TYPE;}
    }
    public record Ack(String value) implements CustomPacketPayload {
        public static final Type<Ack> TYPE=new Type<>(Identifier.fromNamespaceAndPath("appearance_sync","api_notice_ack"));
        public static final StreamCodec<ByteBuf,Ack> CODEC=new StreamCodec<>() {
            public Ack decode(ByteBuf b){return new Ack(new FriendlyByteBuf(b).readUtf(64));}
            public void encode(ByteBuf b,Ack p){new FriendlyByteBuf(b).writeUtf(p.value,64);}
        };
        public Type<Ack> type(){return TYPE;}
    }
}
