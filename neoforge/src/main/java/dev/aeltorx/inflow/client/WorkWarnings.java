package dev.aeltorx.inflow.client;
import com.google.gson.*;
import java.util.*;
public final class WorkWarnings {
    public static List<String> others(JsonArray work,String viewerId,String viewerName){
        var result=new LinkedHashSet<String>();
        for(var entry:work){if(!entry.isJsonObject())continue;var item=entry.getAsJsonObject();String id=value(item,"uuid"),name=value(item,"moderator");if(name.isBlank()||!id.isBlank()&&id.equals(viewerId)||name.equalsIgnoreCase(viewerName))continue;String type=switch(value(item,"type")){case "check"->"проверка";case "report"->"жалоба";case "case"->"дело";default->"разбор";};result.add(name+" · "+type);}
        return List.copyOf(result);
    }
    private static String value(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
}
