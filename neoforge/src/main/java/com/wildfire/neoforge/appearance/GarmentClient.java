package com.wildfire.neoforge.appearance;

import com.mojang.blaze3d.platform.NativeImage;
import com.wildfire.common.WildfireGender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** HD clothing atlas cache. Slot 0 is shirt, slot 1 is pants; PNG uses the vanilla 64×64 UV layout. */
public final class GarmentClient {
    private record Key(UUID player, int slot) {}
    private record Garment(int revision, Identifier id, DynamicTexture texture) {}
    private static final class Transfer {
        final int revision, width, height;
        final byte[] bytes;
        int received;
        Transfer(HdSkinPackets.GarmentChunk chunk) {
            revision=chunk.revision();width=chunk.width();height=chunk.height();bytes=new byte[chunk.totalBytes()];
        }
        boolean matches(HdSkinPackets.GarmentChunk chunk) {
            return revision==chunk.revision() && width==chunk.width() && height==chunk.height()
                && bytes.length==chunk.totalBytes();
        }
    }
    private static final Map<Key,Garment> GARMENTS=new HashMap<>();
    private static final Map<Key,Transfer> TRANSFERS=new HashMap<>();
    private static int pendingBytes;
    private GarmentClient() {}


    public static Identifier texture(UUID player,int slot) {
        Garment value=GARMENTS.get(new Key(player,slot));return value==null?null:value.id();
    }

    public static void receive(HdSkinPackets.GarmentChunk chunk) {
        int width=chunk.width();
        if(chunk.revision()<0 || width<64 || width>2048 || Integer.bitCount(width/64)!=1
            || chunk.height()!=width || chunk.totalBytes()<1 || chunk.totalBytes()>HdSkinPackets.MAX_PNG_BYTES
            || chunk.data().length<1 || chunk.data().length>HdSkinPackets.CHUNK_BYTES)return;
        Key key=new Key(chunk.player(),chunk.slot());
        Garment current=GARMENTS.get(key);
        if(current!=null && chunk.revision()<=current.revision())return;
        Transfer transfer=TRANSFERS.get(key);
        if(transfer!=null && chunk.revision()<transfer.revision)return;
        if(transfer==null || !transfer.matches(chunk)) {
            if(chunk.offset()!=0 || pendingBytes+chunk.totalBytes()>16*1024*1024)return;
            drop(key);transfer=new Transfer(chunk);TRANSFERS.put(key,transfer);pendingBytes+=transfer.bytes.length;
        }
        if(chunk.offset()!=transfer.received)return;
        System.arraycopy(chunk.data(),0,transfer.bytes,transfer.received,chunk.data().length);
        transfer.received+=chunk.data().length;
        if(transfer.received==transfer.bytes.length){drop(key);install(key,transfer);}
    }

    private static void install(Key key,Transfer transfer) {
        byte[] bytes=transfer.bytes;
        if(bytes.length<24 || bytes[0]!=(byte)137 || bytes[1]!=80 || bytes[2]!=78 || bytes[3]!=71
            || bytes[4]!=13 || bytes[5]!=10 || bytes[6]!=26 || bytes[7]!=10
            || bytes[12]!='I' || bytes[13]!='H' || bytes[14]!='D' || bytes[15]!='R'
            || int32(bytes,16)!=transfer.width || int32(bytes,20)!=transfer.height)return;
        try(NativeImage image=NativeImage.read(bytes)) {
            if(image.getWidth()!=transfer.width || image.getHeight()!=transfer.height)return;
            NativeImage copy=new NativeImage(transfer.width,transfer.height,true);copy.copyFrom(image);
            Identifier id=Identifier.fromNamespaceAndPath("appearance_sync",
                "garment/"+key.slot()+"/"+key.player().toString().replace('-','_'));
            clear(key.player(),key.slot());
            DynamicTexture texture=new DynamicTexture(()->"Garment "+key.player()+"/"+key.slot(),copy);
            Minecraft.getInstance().getTextureManager().register(id,texture);texture.upload();
            GARMENTS.put(key,new Garment(transfer.revision,id,texture));
        }catch(IOException | RuntimeException invalid){
            WildfireGender.LOGGER.warn("Rejected garment PNG for {} slot {}: {}",key.player(),key.slot(),invalid.toString());
        }
    }
    private static int int32(byte[] bytes,int offset) {
        return (bytes[offset]&255)<<24 | (bytes[offset+1]&255)<<16 | (bytes[offset+2]&255)<<8 | bytes[offset+3]&255;
    }
    private static void drop(Key key){Transfer value=TRANSFERS.remove(key);if(value!=null)pendingBytes-=value.bytes.length;}
    public static void clear(UUID player,int slot){
        Minecraft.getInstance().execute(() -> clearOnClientThread(player,slot));
    }
    private static void clearOnClientThread(UUID player,int slot){
        Key key=new Key(player,slot);drop(key);Garment value=GARMENTS.remove(key);
        if(value!=null)Minecraft.getInstance().getTextureManager().release(value.id());
    }
    public static void clearAll(){
        Minecraft.getInstance().execute(() -> {
            for(Key key:GARMENTS.keySet().toArray(Key[]::new))clearOnClientThread(key.player(),key.slot());
            TRANSFERS.clear();pendingBytes=0;
        });
    }
}
