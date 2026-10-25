package com.wildfire.neoforge.appearance;
import com.google.gson.JsonObject;import java.net.URI;import java.util.*;
public final class StreamNoticeClient {
    public record Entry(String id,String streamer,String platform,String title,String url,String preview,int viewers,long createdAt){}
    private static final LinkedHashMap<String,Entry> ACTIVE=new LinkedHashMap<>();private StreamNoticeClient(){}
    public static synchronized List<Entry> active(){var rows=new ArrayList<>(ACTIVE.values());Collections.reverse(rows);return List.copyOf(rows);}
    static synchronized void receive(JsonObject d){try{
        String type=d.has("type")?d.get("type").getAsString():"";
        if("stream_snapshot".equals(type)){var next=new LinkedHashMap<String,Entry>();for(var raw:d.getAsJsonArray("streams")){Entry e=parse(raw.getAsJsonObject());next.put(e.url(),e);}ACTIVE.clear();ACTIVE.putAll(next);return;}
        String state=s(d,"state",8);Entry e=parse(d);if("START".equals(state)){ACTIVE.put(e.url(),e);ModUpdate.streamFeedback(e.streamer(),e.platform(),e.title(),e.url(),e.preview(),e.viewers());}
        else{ACTIVE.remove(e.url());ModUpdate.transientFeedback(e.streamer()+" завершил стрим",-1f,6);}
    }catch(Exception ignored){}}
    private static Entry parse(JsonObject d){String id=s(d,"id",96),streamer=s(d,"streamer",64),platform=s(d,"platform",24),title=s(d,"title",180),url=s(d,"url",1024),preview=s(d,"previewUrl",2048);if(!"https".equalsIgnoreCase(URI.create(url).getScheme()))throw new IllegalArgumentException();int viewers=d.has("viewers")?d.get("viewers").getAsInt():-1;long created=d.has("createdAt")?d.get("createdAt").getAsLong():0;return new Entry(id,streamer,platform,title,url,preview,viewers,created);}
    private static String s(JsonObject d,String key,int max){String v=d.has(key)?d.get(key).getAsString().trim():"";if(v.length()>max||v.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException();return v;}
}
