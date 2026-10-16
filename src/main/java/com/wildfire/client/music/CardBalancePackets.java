package com.wildfire.client.music;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public final class CardBalancePackets {
    private CardBalancePackets() {}
    public record Request(String nonce) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(Identifier.fromNamespaceAndPath("appearance_sync","cards_request"));
        public static final StreamCodec<ByteBuf,Request> CODEC=new StreamCodec<>() {
            public Request decode(ByteBuf b){return new Request(new FriendlyByteBuf(b).readUtf(64));}
            public void encode(ByteBuf b,Request p){new FriendlyByteBuf(b).writeUtf(p.nonce,64);}
        };
        public Type<Request> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(Identifier.fromNamespaceAndPath("appearance_sync","cards_balance"));
        public static final StreamCodec<ByteBuf,Snapshot> CODEC=new StreamCodec<>() {
            public Snapshot decode(ByteBuf b){return new Snapshot(new FriendlyByteBuf(b).readUtf(512));}
            public void encode(ByteBuf b,Snapshot p){new FriendlyByteBuf(b).writeUtf(p.json,512);}
        };
        public Type<Snapshot> type(){return TYPE;}
    }
}
