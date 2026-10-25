package com.wildfire.client.music;
import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;

/** Only public menu category; no messages, searches, settings or account data are transmitted. */
public final class MenuPresence {
    public record View(float amount,int category) {}
    private static final Map<UUID, Entry> entries=new LinkedHashMap<>();
    private static int lastCategory=-2;
    private static long sent;
    private MenuPresence() {}
    private static final class Entry {
        float from,target;int category;long changed,received;
        float value(long now) {
            float t=Math.clamp((now-changed)/300_000_000f,0f,1f);
            return from+(target-from)*t*t*(3f-2f*t);
        }
    }
    public static void put(UUID owner,int category) {
        if(owner==null||category < -1||category>3)return;
        long now=System.nanoTime();
        if(!entries.containsKey(owner)&&entries.size()>=256)entries.remove(entries.keySet().iterator().next());
        Entry e=entries.computeIfAbsent(owner,id->new Entry());
        float target=category<0?0f:1f;
        if(target!=e.target) { e.from=e.value(now);e.target=target;e.changed=now; }
        if(category>=0)e.category=category;
        e.received=now;
    }
    public static View view(UUID owner) {
        if(!SpringPlusSession.enabled())return null;
        Entry e=entries.get(owner);if(e==null)return null;
        long now=System.nanoTime();
        if(now-e.received>25_000_000_000L) { entries.remove(owner);return null; }
        float value=e.value(now);return value<=.001f?null:new View(value,e.category);
    }
    public static void tick(UUID self,int category,Consumer<String> send) {
        put(self,category);
        long now=System.nanoTime();
        if((category!=lastCategory||now-sent>15_000_000_000L)&&now-sent>=600_000_000L) {
            JsonObject data=new JsonObject();data.addProperty("menu",category);data.addProperty("receive",true);
            send.accept(data.toString());lastCategory=category;sent=now;
        }
    }
    public static void accept(String json) {
        if(!SpringPlusSession.enabled()||json==null||json.length()>256)return;
        try {
            var data=JsonParser.parseString(json).getAsJsonObject();
            put(UUID.fromString(data.get("uuid").getAsString()),data.get("menu").getAsInt());
        } catch(RuntimeException ignored) {}
    }
    public static void reset() { entries.clear();lastCategory=-2;sent=0; }
}
