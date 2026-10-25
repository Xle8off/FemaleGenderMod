package com.wildfire.gui;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Local bounded favorites and recents. Disk writes never run on the render thread. */
public final class ReactionHistory {
    private static ReactionHistory instance;
    private final Path file;
    private final Map<String,List<String>> values=new HashMap<>();
    private long revision;
    private boolean queued;
    private static final ExecutorService DISK=Executors.newSingleThreadExecutor(task->{
        var thread=new Thread(task,"SpringPlus reaction preferences");thread.setDaemon(true);return thread;
    });
    public static synchronized ReactionHistory get(){
        if(instance==null)instance=new ReactionHistory(Minecraft.getInstance().gameDirectory.toPath().resolve("config/springplus-reactions.json"));
        return instance;
    }
    public ReactionHistory(Path file){
        this.file=file;
        try{
            if(Files.isRegularFile(file)&&Files.size(file)<=131072){
                var root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if(root.get("version").getAsInt()==1)for(String kind:List.of("emoji","sticker","pack"))for(boolean favorite:List.of(false,true)){
                    String key=key(kind,favorite);var array=root.getAsJsonArray(key);if(array==null)continue;
                    var ids=new ArrayList<String>();
                    for(var raw:array){String id=raw.getAsString();if(valid(kind,id)&&!ids.contains(id)&&ids.size()<(favorite?256:64))ids.add(id);}
                    values.put(key,ids);
                }
            }
        }catch(Exception ignored){}
    }
    private static String key(String kind,boolean favorite){return kind+(favorite?"Favorites":"Recent");}
    private static boolean valid(String kind,String id){
        if("pack".equals(kind))return id!=null&&id.matches("[a-z0-9_]{1,32}");
        return "emoji".equals(kind)?id!=null&&id.matches("[a-zA-Z0-9_-]{1,120}")
            :"sticker".equals(kind)&&id!=null&&id.matches("[a-z0-9_]{1,32}/[a-z0-9_-]{1,64}");
    }
    public synchronized long revision(){return revision;}
    public synchronized boolean installed(String id){return favorite("pack",id);}
    public synchronized void togglePack(String id){toggle("pack",id);}
    public synchronized List<String> ids(String kind,boolean favorites){return List.copyOf(values.getOrDefault(key(kind,favorites),List.of()));}
    public synchronized boolean favorite(String kind,String id){return values.getOrDefault(key(kind,true),List.of()).contains(id);}
    public synchronized void toggle(String kind,String id){
        if(!valid(kind,id))return;
        var ids=new ArrayList<>(ids(kind,true));
        if(!ids.remove(id)){ids.addFirst(id);if(ids.size()>256)ids.removeLast();}
        values.put(key(kind,true),ids);revision++;queue();
    }
    public synchronized void used(String kind,String id){
        if(!valid(kind,id))return;
        var ids=new ArrayList<>(ids(kind,false));ids.remove(id);ids.addFirst(id);if(ids.size()>64)ids.removeLast();
        values.put(key(kind,false),ids);revision++;queue();
    }
    private void queue(){if(!queued){queued=true;DISK.execute(this::flush);}}
    private void flush(){
        for(;;){
            String data;long saved;
            synchronized(this){saved=revision;var root=new LinkedHashMap<String,Object>();root.put("version",1);values.forEach((key,value)->root.put(key,List.copyOf(value)));data=new Gson().toJson(root);}
            Path temporary=null;
            try{
                Files.createDirectories(file.getParent());temporary=Files.createTempFile(file.getParent(),".reactions-",".tmp");Files.writeString(temporary,data);
                try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            }catch(Exception ignored){}
            finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(Exception ignored){}}
            synchronized(this){if(saved==revision){queued=false;notifyAll();return;}}
        }
    }
    public synchronized boolean awaitSaved(long milliseconds)throws InterruptedException{
        long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(milliseconds);
        while(queued){long left=until-System.nanoTime();if(left<=0)return false;TimeUnit.NANOSECONDS.timedWait(this,left);}
        return true;
    }
}
