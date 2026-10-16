package com.wildfire.client.music;
import com.google.gson.JsonParser;
import java.util.*;
import java.util.function.*;
/** Main-thread delivery, bounded deduplication, ACK only after insertion into notification history. */
public final class ApiNoticeInbox {
    private static final Set<String> seen=new LinkedHashSet<>();
    private static Consumer<String> ack=value->{};
    private static BiConsumer<String,String> deliver=(title,text)->{};
    private static long heartbeat;
    private ApiNoticeInbox() {}
    public static void configure(Consumer<String> send,BiConsumer<String,String> receiver){ack=send;deliver=receiver;CampaignClient.notifier(receiver);}
    public static void tick(){CampaignClient.tick();if(!SpringPlusSession.enabled())return;long now=System.currentTimeMillis();if(now-heartbeat<10000)return;try{ack.accept("ready");heartbeat=now;}catch(RuntimeException unavailable){}}
    public static void receive(String json){
        if(!SpringPlusSession.enabled()||json==null||json.length()>32768)return;
        try {
            var data=JsonParser.parseString(json).getAsJsonObject();
            if(data.has("type")&&data.get("type").getAsString().equals("state")){CampaignClient.accept(data);return;}
            if(json.length()>4096)return;
            String id=UUID.fromString(data.get("id").getAsString()).toString();
            String title=data.get("title").getAsString(),text=data.get("text").getAsString();
            if(!valid(title,80)||!valid(text,512))return;
            if(!seen.contains(id)){deliver.accept(title,text);seen.add(id);while(seen.size()>2048)seen.remove(seen.iterator().next());}
            ack.accept(id);
        }catch(RuntimeException invalid){}
    }
    private static boolean valid(String value,int limit){return !value.isBlank()&&value.length()<=limit&&value.codePoints().noneMatch(c->Character.isISOControl(c)||c==0xA7);}
    public static void reset(){heartbeat=0;CampaignClient.reset();}
}
