package com.wildfire.main.networking.appearance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wildfire.main.WildfireGender;
import com.wildfire.render.CosmeticLayer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

/** Client-side hash cache. A new catalogue only requests missing/changed compressed assets. */
public final class CosmeticCatalogClient {
    private static final Map<Integer,CosmeticCatalogPackets.Entry> ENTRIES=new HashMap<>();
    private static final Map<Integer,CosmeticCatalogPackets.Entry> OLD_ENTRIES=new HashMap<>();
    private static final Map<Integer,JsonObject> PACKAGES=new HashMap<>();
    private static final Map<Integer,Transfer> TRANSFERS=new HashMap<>();
    private static final Map<Integer,Long> REQUESTED=new HashMap<>();
    private static int staticPetMask;
    private static int generation, tickCounter, prefetchId, prefetchCursor;
    private static long prefetchStarted;
    private static final Path CACHE=Minecraft.getInstance().gameDirectory.toPath().resolve("springpluscosmetics").resolve("cache");
    private static final class Transfer {
        final String hash;final byte[][] chunks;int count;
        Transfer(String hash,int total){this.hash=hash;this.chunks=new byte[total][];}
    }
    private CosmeticCatalogClient(){}
    public static void reset(){reset(0);}
    public static void reset(int mask){generation++;CosmeticLayer.clearDynamicCache();ENTRIES.clear();OLD_ENTRIES.clear();PACKAGES.clear();TRANSFERS.clear();REQUESTED.clear();staticPetMask=mask;prefetchId=0;prefetchCursor=0;}
    public static void beginCatalog(int mask){generation++;OLD_ENTRIES.clear();OLD_ENTRIES.putAll(ENTRIES);ENTRIES.clear();TRANSFERS.clear();REQUESTED.clear();staticPetMask=mask;prefetchId=0;prefetchCursor=0;}
    public static boolean staticPetOwned(int id){return id>=1&&id<=4&&(staticPetMask&(1<<(id-1)))!=0;}
    public static CosmeticCatalogPackets.Entry entry(int id){return ENTRIES.get(id);}
    public static JsonObject packageFor(int id){
        var pack=PACKAGES.get(id);if(pack!=null)return pack;
        var entry=ENTRIES.get(id);if(entry==null)return null;
        long now=System.nanoTime();
        Long previous=REQUESTED.get(id);
        if(previous!=null&&now-previous<22_000_000_000L)return null;
        REQUESTED.put(id,now);
        Path file=CACHE.resolve(entry.hash()+".gz");
        int requestGeneration=generation;
        CompletableFuture.supplyAsync(()->{
            try{return Files.isRegularFile(file)?unpack(Files.readAllBytes(file),entry.hash()):null;}
            catch(Exception error){WildfireGender.LOGGER.warn("Invalid cached cosmetic {}: {}",entry.id(),error.getMessage());return null;}
        }).thenAccept(loaded->Minecraft.getInstance().schedule(()->{
            if(requestGeneration!=generation||ENTRIES.get(id)!=entry)return;
            if(loaded!=null){PACKAGES.put(id,loaded);if(prefetchId==id)prefetchId=0;}
            else { ModUpdate.cosmeticFeedback("Загружаем косметику: " + entry.name(), 0f, 32);
                ClientPlayNetworking.send(new CosmeticCatalogPackets.Request(id,entry.hash())); }
        }));
        return null;
    }
    public static void tick(){
        if(Minecraft.getInstance().player==null||++tickCounter%20!=0||ENTRIES.isEmpty())return;
        long now=System.nanoTime();
        if(prefetchId!=0&&now-prefetchStarted<30_000_000_000L)return;
        if(prefetchId!=0) ModUpdate.cosmeticFeedback("Косметика не загрузилась — повторяем", -1f, 8);
        prefetchId=0;
        var items=ENTRIES.values().stream().sorted(java.util.Comparator.comparingInt(CosmeticCatalogPackets.Entry::networkId)).toList();
        for(int offset=0;offset<items.size();offset++){
            int index=(prefetchCursor+offset)%items.size();
            var entry=items.get(index);
            if(PACKAGES.containsKey(entry.networkId()))continue;
            Long previous=REQUESTED.get(entry.networkId());
            if(previous!=null&&now-previous<22_000_000_000L)continue;
            packageFor(entry.networkId());
            prefetchId=entry.networkId();prefetchStarted=now;prefetchCursor=(index+1)%items.size();
            break;
        }
    }
    public static java.util.Collection<CosmeticCatalogPackets.Entry> entries(){return java.util.List.copyOf(ENTRIES.values());}
    public static void accept(CosmeticCatalogPackets.Entry entry){
        if(entry.networkId()<1||entry.networkId()>100000||!entry.id().matches("[a-z][a-z0-9_-]{2,47}")
            ||!entry.hash().matches("[a-f0-9]{64}"))return;
        var previous=OLD_ENTRIES.remove(entry.networkId());
        ENTRIES.put(entry.networkId(),entry);
        if(previous!=null&&!previous.hash().equals(entry.hash())){
            PACKAGES.remove(entry.networkId());REQUESTED.remove(entry.networkId());
            if(prefetchId==entry.networkId())prefetchId=0;
        }
    }
    public static void accept(CosmeticCatalogPackets.Chunk chunk){
        var entry=ENTRIES.get(chunk.networkId());
        if(entry==null||!entry.hash().equals(chunk.hash())||chunk.total()<1||chunk.total()>512
            ||chunk.index()<0||chunk.index()>=chunk.total())return;
        Transfer transfer=TRANSFERS.get(chunk.networkId());
        if(transfer==null||!transfer.hash.equals(chunk.hash())||transfer.chunks.length!=chunk.total()){
            transfer=new Transfer(chunk.hash(),chunk.total());TRANSFERS.put(chunk.networkId(),transfer);
        }
        if(transfer.chunks[chunk.index()]==null){transfer.chunks[chunk.index()]=chunk.bytes();transfer.count++;}
        ModUpdate.cosmeticFeedback("Загружаем косметику: " + entry.name(), (float)transfer.count/transfer.chunks.length, 32);
        if(transfer.count!=transfer.chunks.length)return;
        TRANSFERS.remove(chunk.networkId());
        Transfer completed=transfer;
        int requestGeneration=generation;
        CompletableFuture.runAsync(()->{try{
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            for(byte[] bytes:completed.chunks)output.write(bytes);
            byte[] compressed=output.toByteArray();
            JsonObject pack=unpack(compressed,entry.hash());
            Files.createDirectories(CACHE);Files.write(CACHE.resolve(entry.hash()+".gz"),compressed);
            Minecraft.getInstance().schedule(()->{
                if(requestGeneration!=generation||ENTRIES.get(entry.networkId())!=entry)return;
                PACKAGES.put(entry.networkId(),pack);if(prefetchId==entry.networkId())prefetchId=0;
                ModUpdate.cosmeticFeedback("Косметика загружена: " + entry.name(), -1f, 4);
                WildfireGender.LOGGER.info("Loaded cosmetic {} from server ({} bytes)",entry.id(),compressed.length);
            });
        }catch(Exception error){WildfireGender.LOGGER.warn("Rejected cosmetic {}: {}",entry.id(),error.getMessage());
            ModUpdate.cosmeticFeedback("Ошибка косметики: " + error.getMessage(), -1f, 12);}});
    }
    private static JsonObject unpack(byte[] compressed,String hash)throws Exception{
        if(compressed.length>8_388_608)throw new IllegalArgumentException("Compressed asset too large");
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(GZIPInputStream gzip=new GZIPInputStream(new ByteArrayInputStream(compressed))){
            byte[] buffer=new byte[16384];int count;
            while((count=gzip.read(buffer))!=-1){output.write(buffer,0,count);
                if(output.size()>12_000_000)throw new IllegalArgumentException("Asset too large");}
        }
        byte[] bytes=output.toByteArray();
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder actual=new StringBuilder(64);for(byte value:digest)actual.append(String.format("%02x",value&255));
        if(!actual.toString().equals(hash))throw new IllegalArgumentException("Hash mismatch");
        JsonObject pack=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        if(!pack.has("model")||!pack.has("texture"))throw new IllegalArgumentException("Incomplete cosmetic");
        return pack;
    }
}
