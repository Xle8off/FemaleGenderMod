package com.wildfire.neoforge.appearance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.nio.charset.StandardCharsets;

/** Small catalogue messages; model bytes are transferred as separately bounded chunks. */
public final class CosmeticCatalogPackets {
    private CosmeticCatalogPackets() {}
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("appearance_sync",path));
    }
    private static String readString(ByteBuf input,int max) {
        int length=VarInt.read(input);
        if(length<0||length>max||length>input.readableBytes())throw new IllegalArgumentException("Invalid cosmetic string");
        byte[] bytes=new byte[length];input.readBytes(bytes);return new String(bytes,StandardCharsets.UTF_8);
    }
    private static void writeString(ByteBuf output,String value) {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        VarInt.write(output,bytes.length);output.writeBytes(bytes);
    }
    public record Reset(int marker) implements CustomPacketPayload {
        public static final Type<Reset> TYPE=CosmeticCatalogPackets.type("catalog_reset");
        public static final StreamCodec<ByteBuf,Reset> CODEC=new StreamCodec<>() {
            public Reset decode(ByteBuf input){return new Reset(input.readUnsignedByte());}
            public void encode(ByteBuf output,Reset value){output.writeByte(value.marker);}
        };
        public Type<Reset> type(){return TYPE;}
    }
    public record Entry(int networkId,String id,String name,String kind,int price,String hash,boolean owned) implements CustomPacketPayload {
        public static final Type<Entry> TYPE=CosmeticCatalogPackets.type("catalog_entry");
        public static final StreamCodec<ByteBuf,Entry> CODEC=new StreamCodec<>() {
            public Entry decode(ByteBuf input){return new Entry(VarInt.read(input),readString(input,64),readString(input,256),
                readString(input,32),VarInt.read(input),readString(input,64),input.readBoolean());}
            public void encode(ByteBuf output,Entry value){VarInt.write(output,value.networkId);writeString(output,value.id);
                writeString(output,value.name);writeString(output,value.kind);VarInt.write(output,value.price);
                writeString(output,value.hash);output.writeBoolean(value.owned);}
        };
        public Type<Entry> type(){return TYPE;}
    }
    public record Request(int networkId,String hash) implements CustomPacketPayload {
        public static final Type<Request> TYPE=CosmeticCatalogPackets.type("catalog_request");
        public static final StreamCodec<ByteBuf,Request> CODEC=new StreamCodec<>() {
            public Request decode(ByteBuf input){return new Request(VarInt.read(input),readString(input,64));}
            public void encode(ByteBuf output,Request value){VarInt.write(output,value.networkId);writeString(output,value.hash);}
        };
        public Type<Request> type(){return TYPE;}
    }
    public record Chunk(int networkId,String hash,int index,int total,byte[] bytes) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE=CosmeticCatalogPackets.type("catalog_chunk");
        public static final StreamCodec<ByteBuf,Chunk> CODEC=new StreamCodec<>() {
            public Chunk decode(ByteBuf input){int id=VarInt.read(input);String hash=readString(input,64);
                int index=VarInt.read(input),total=VarInt.read(input),size=VarInt.read(input);
                if(size<1||size>16384||size>input.readableBytes())throw new IllegalArgumentException("Invalid cosmetic chunk");
                byte[] bytes=new byte[size];input.readBytes(bytes);return new Chunk(id,hash,index,total,bytes);}
            public void encode(ByteBuf output,Chunk value){VarInt.write(output,value.networkId);writeString(output,value.hash);
                VarInt.write(output,value.index);VarInt.write(output,value.total);VarInt.write(output,value.bytes.length);
                output.writeBytes(value.bytes);}
        };
        public Type<Chunk> type(){return TYPE;}
    }
}
