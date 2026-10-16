package com.wildfire.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared batched faces for friends, PMs, groups and notifications. No render-thread I/O. */
public final class ChatAvatars {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ScheduledExecutorService WORK=Executors.newSingleThreadScheduledExecutor(task->{var thread=new Thread(task,"SpringPlus avatar batches");thread.setDaemon(true);return thread;});
    private static final Map<String,Entry> entries=new ConcurrentHashMap<>();
    private static final LinkedHashMap<String,Identifier> textures=new LinkedHashMap<>(128,.75f,true); // render thread
    private static final LinkedHashMap<String,Entry> pending=new LinkedHashMap<>();
    private static final Object lock=new Object();private static boolean scheduled;
    private static volatile long epoch;private static int writes;
    private static final class Entry {
        final String name;final AtomicBoolean fetching=new AtomicBoolean();
        volatile String hash="";volatile byte[] png;volatile long nextCheck,lastUsed;boolean diskRead;
        Entry(String name){this.name=name;}
    }
    private ChatAvatars(){}
    public static Identifier texture(String nickname){
        if(!AvatarBatchProtocol.name(nickname)||!com.wildfire.client.music.SpringPlusSession.enabled())return null;
        String name=nickname.toLowerCase(Locale.ROOT);Entry entry=entries.get(name);
        if(entry==null){
            if(entries.size()>=512){var old=entries.values().stream().filter(e->!e.fetching.get()).min(Comparator.comparingLong(e->e.lastUsed)).orElse(null);if(old==null)return null;entries.remove(old.name,old);var id=textures.remove(old.name);if(id!=null)Minecraft.getInstance().getTextureManager().release(id);}
            entry=new Entry(name);entries.put(name,entry);
        }
        long now=System.currentTimeMillis();entry.lastUsed=now;
        Identifier cached=textures.get(name);
        if(cached==null&&entry.png!=null)cached=install(entry);
        if(now>=entry.nextCheck&&entry.fetching.compareAndSet(false,true)){
            synchronized(lock){pending.put(name,entry);if(!scheduled){scheduled=true;WORK.schedule(ChatAvatars::drain,120,TimeUnit.MILLISECONDS);}}
        }
        return cached!=null?cached:defaultTexture(nickname);
    }
    private static Identifier defaultTexture(String nickname){
        var uuid=UUID.nameUUIDFromBytes(("OfflinePlayer:"+nickname).getBytes(StandardCharsets.UTF_8));
        return Identifier.withDefaultNamespace((uuid.getLeastSignificantBits()&1)==0
            ?"textures/entity/player/wide/steve.png":"textures/entity/player/slim/alex.png");
    }
    public static void drawTexture(GuiGraphicsExtractor g,Identifier texture,int x,int y,int size){
        if(texture.getNamespace().equals("minecraft")){
            g.blit(RenderPipelines.GUI_TEXTURED,texture,x,y,8f,8f,size,size,8,8,64,64);
            g.blit(RenderPipelines.GUI_TEXTURED,texture,x,y,40f,8f,size,size,8,8,64,64);
            return;
        }
        g.blit(RenderPipelines.GUI_TEXTURED,texture,x,y,0f,0f,size,size,64,64,64,64);
    }
    private static AvatarDiskCache disk(){return new AvatarDiskCache(Minecraft.getInstance().gameDirectory.toPath().resolve("cache/springplus/avatar-faces-v1"));}
    private static void publish(Entry entry,long generation){Minecraft.getInstance().execute(()->{if(epoch==generation&&entries.get(entry.name)==entry)install(entry);});}
    private static Identifier install(Entry entry){
        if(entry.png==null)return null;
        try(var image=NativeImage.read(entry.png)){
            if(image.getWidth()!=64||image.getHeight()!=64)throw new IllegalArgumentException("Avatar dimensions");
            var copy=new NativeImage(64,64,true);copy.copyFrom(image);
            var id=Identifier.fromNamespaceAndPath("appearance_sync","chat_avatar/"+entry.name);
            var previous=textures.remove(entry.name);if(previous!=null)Minecraft.getInstance().getTextureManager().release(previous);
            var texture=new DynamicTexture(()->"Chat avatar "+entry.name,copy);
            Minecraft.getInstance().getTextureManager().register(id,texture);texture.upload();textures.put(entry.name,id);
            while(textures.size()>128){var oldest=textures.entrySet().iterator();var evicted=oldest.next();Minecraft.getInstance().getTextureManager().release(evicted.getValue());oldest.remove();}
            return id;
        }catch(Exception e){entry.hash="";entry.png=null;entry.nextCheck=System.currentTimeMillis()+30000;return null;}
    }
    private static void drain(){
        long generation=epoch;var batch=new LinkedHashMap<String,Entry>();
        synchronized(lock){var iterator=pending.entrySet().iterator();while(iterator.hasNext()&&batch.size()<32){var item=iterator.next();batch.put(item.getKey(),item.getValue());iterator.remove();}}
        try{
            var known=new LinkedHashMap<String,String>();var disk=disk();
            for(var entry:batch.values()){
                if(!entry.diskRead){entry.diskRead=true;var cached=disk.load(entry.name);if(cached!=null){entry.hash=cached.hash();entry.png=cached.png();publish(entry,generation);}}
                known.put(entry.name,entry.png==null?"":entry.hash);
            }
            if(batch.isEmpty()||generation!=epoch)return;
            var request=HttpRequest.newBuilder(URI.create("https://skin.springplus.net/api/avatars/batch")).timeout(Duration.ofSeconds(20))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(AvatarBatchProtocol.request(known))).build();
            var response=HTTP.send(request,info->new AvatarBatchProtocol.LimitedBody(2097152));
            if(response.statusCode()!=200)throw new IllegalStateException("HTTP "+response.statusCode());
            byte[] bytes=response.body();
            if(generation!=epoch)return;
            var results=AvatarBatchProtocol.parse(new String(bytes,StandardCharsets.UTF_8),known);
            for(var entry:batch.values())entry.nextCheck=System.currentTimeMillis()+30000;
            for(var face:results){
                Entry entry=batch.get(face.name());
                if(face.status().equals("ok")){
                    if(!face.unchanged()){
                        entry.hash=face.hash();entry.png=face.png();publish(entry,generation);
                        try{disk.save(entry.name,entry.hash,entry.png);if(++writes%32==1)disk.prune(512);}catch(Exception ignored){}
                    }
                    entry.nextCheck=System.currentTimeMillis()+300000;
                }else if(face.status().equals("missing")){
                    disk.remove(entry.name);
                    entry.hash="";entry.png=null;entry.nextCheck=System.currentTimeMillis()+120000;
                    Minecraft.getInstance().execute(()->{if(generation==epoch&&entries.get(entry.name)==entry){var id=textures.remove(entry.name);if(id!=null)Minecraft.getInstance().getTextureManager().release(id);}});
                }else entry.nextCheck=System.currentTimeMillis()+60000;
            }
        }catch(Exception error){
            for(var entry:batch.values())entry.nextCheck=System.currentTimeMillis()+30000;
            if(generation==epoch)System.err.println("SpringPlus avatar batch: "+error.getClass().getSimpleName()+"; retry in 30s");
        }finally{
            batch.values().forEach(e->e.fetching.set(false));
            synchronized(lock){if(pending.isEmpty())scheduled=false;else WORK.schedule(ChatAvatars::drain,120,TimeUnit.MILLISECONDS);}
        }
    }
    public static void clear(){
        epoch++;synchronized(lock){pending.values().forEach(e->e.fetching.set(false));pending.clear();}
        entries.clear();textures.values().forEach(Minecraft.getInstance().getTextureManager()::release);textures.clear();
    }
}
