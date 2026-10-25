package com.wildfire.client.music;

import com.google.gson.*;
import java.util.*;
import java.util.function.*;

/** UI-thread-only bounded group state. Text sent to the proxy is ordinary Unicode. */
public final class GroupInbox {
    public record Group(String id,String title,String owner,int count,int unread,long updatedAt) {}
    public record Member(String uuid,String name) {}
    public record Message(long id,String sender,String name,String body,long time) {}
    private static final Map<String,Map<String,Long>> readMarkers=new HashMap<>();
    public static boolean readByAll(String group,long message){
        var markers=readMarkers.getOrDefault(group,Map.of());
        return markers.size()>1&&markers.entrySet().stream().filter(e->!e.getKey().equals(self.get())).allMatch(e->e.getValue()>=message);
    }
    public record Result(boolean ok,String group,String error) {}
    private static List<Group> groups=List.of();
    private static final Map<String,List<Member>> members=new HashMap<>();
    private static final Map<String,List<String>> faces=new HashMap<>();
    private static final Map<String,Integer> permissions=new HashMap<>();
    private static final Map<String,Map<String,AdminRole>> roles=new HashMap<>();
    private static final Map<String,Set<Long>> deleted=new HashMap<>();
    public record AdminRole(String role,int permissions) {}
    public static AdminRole role(String id,String uuid){return roles.getOrDefault(id,Map.of()).getOrDefault(uuid,new AdminRole("member",0));}
    public static boolean allowed(String id,int permission){var g=group(id);return g!=null&&(g.owner().equals(self.get())||(permissions.getOrDefault(id,0)&permission)==permission);}
    private static void acceptRoles(String id,JsonArray people){
        if(people.size()>20)throw new IllegalArgumentException();var next=new HashMap<String,AdminRole>();
        for(var item:people){var o=item.getAsJsonObject();String role=o.has("role")?o.get("role").getAsString():"member";int rights=o.has("permissions")?o.get("permissions").getAsInt():0;
            if(!Set.of("owner","admin","member").contains(role)||rights<0||rights>15)throw new IllegalArgumentException();
            next.put(uuid(o,"uuid"),new AdminRole(role,rights));}
        roles.put(id,Map.copyOf(next));
    }
    public static List<String> faces(String id){return faces.getOrDefault(id,List.of());}
    private static final Map<String,List<Message>> messages=new HashMap<>();
    private static final Map<String,Result> results=new LinkedHashMap<>();
    private static final Set<String> loaded=new HashSet<>();
    private static final Map<String,Long> requested=new HashMap<>();
    private static final Map<String,Long> lastReadSent=new HashMap<>();
    private static final Set<String> pendingReads=new HashSet<>();
    private static Consumer<String> sender=s->{};
    private static Supplier<String> self=()->"";
    private static BiConsumer<String,String> notifier=(a,b)->{};
    private static String open="",status="Группы недоступны. Нужна поддержка на прокси";
    private static boolean supported; private static long refreshed;
    private GroupInbox() {}
    public static void configure(Consumer<String> send,Supplier<String> uuid,BiConsumer<String,String> notify){sender=send;self=uuid;notifier=notify;}
    public static boolean supported(){return supported;}
    public static String status(){return status;}
    public static boolean isGroup(String key){return key!=null&&key.matches("#[0-9a-f-]{36}");}
    public static String key(String id){return "#"+id;}
    public static String id(String key){return isGroup(key)?key.substring(1):"";}
    public static List<Group> groups(){return groups;}
    public static Group group(String id){return groups.stream().filter(g->g.id.equals(id)).findFirst().orElse(null);}
    public static List<Member> members(String id){return members.getOrDefault(id,List.of());}
    public static List<Message> messages(String id){return messages.getOrDefault(id,List.of());}
    public static int unread(){return groups.stream().mapToInt(g->g.unread).sum();}
    public static boolean outgoing(Message message){return message.sender.equals(self.get());}
    public static Result result(String request){return results.get(request);}
    public static String request(String operation,Map<String,?> fields) {
        if(!SpringPlusSession.enabled())return "";
        String id=UUID.randomUUID().toString();JsonObject packet=new Gson().toJsonTree(fields).getAsJsonObject();packet.addProperty("op",operation);packet.addProperty("request",id);
        String json=packet.toString();if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4096)return "";
        try{sender.accept(json);return id;}catch(RuntimeException unavailable){status="Не удалось отправить запрос группы";return "";}
    }
    public static void refresh(){long now=System.currentTimeMillis();for(String id:List.copyOf(pendingReads))if(now-lastReadSent.getOrDefault(id,0L)>=1000){pendingReads.remove(id);lastReadSent.put(id,now);long through=messages(id).stream().mapToLong(Message::id).max().orElse(0);request("read",Map.of("group",id,"through",through));}if(now-refreshed<5000)return;refreshed=now;request("list",Map.of());}
    public static void open(String id){open=id;readLocal(id);history(id);}
    public static void close(){open="";}
    public static void history(String id){long now=System.currentTimeMillis();if(now-requested.getOrDefault(id,0L)<1500)return;requested.put(id,now);request("history",Map.of("group",id));}
    public static String historyStatus(String id){return loaded.contains(id)?"Пока нет сообщений. Напишите первым.":System.currentTimeMillis()-requested.getOrDefault(id,0L)>8000?"История не загрузилась. Откройте чат повторно.":"Загружаем историю группы…";}
    private static void readLocal(String id){groups=groups.stream().map(g->g.id.equals(id)?new Group(g.id,g.title,g.owner,g.count,0,g.updatedAt):g).toList();}
    public static boolean send(String id,String body){if(body.isBlank()||body.length()>256||group(id)==null)return false;return !request("send",Map.of("group",id,"body",body)).isEmpty();}
    private static String uuid(JsonObject object,String key){return UUID.fromString(object.get(key).getAsString()).toString();}
    private static String text(JsonObject object,String key,int max){String s=object.get(key).getAsString();if(s.isBlank()||s.length()>max||s.codePoints().anyMatch(c->Character.isISOControl(c)||c==0xA7))throw new IllegalArgumentException("Invalid group text");return s;}
    private static Group parse(JsonObject o){int count=o.get("count").getAsInt(),unread=o.get("unread").getAsInt();if(count<1||count>20||unread<0||unread>999)throw new IllegalArgumentException();String id=uuid(o,"id");if(o.has("faces")){var list=o.getAsJsonArray("faces");if(list.size()>4)throw new IllegalArgumentException();var names=new ArrayList<String>();for(var item:list){String name=item.getAsString();if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException();names.add(name);}faces.put(id,List.copyOf(names));}return new Group(id,text(o,"title",32),uuid(o,"owner"),count,unread,o.get("updatedAt").getAsLong());}
    private static Message message(JsonObject o){long id=o.get("id").getAsLong();if(id<1)throw new IllegalArgumentException();String name=text(o,"name",16);if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException();return new Message(id,uuid(o,"sender"),name,text(o,"body",256),o.get("time").getAsLong());}
    public static void accept(String json) {
        if(!SpringPlusSession.enabled()||json==null||json.length()>32767)return;
        try {
            var data=JsonParser.parseString(json).getAsJsonObject();String type=data.get("type").getAsString();
            com.wildfire.gui.ModPresence.accept(data);
            if(data.has("readMarkers")){
                String id=uuid(data,"group");var markers=data.getAsJsonObject("readMarkers");if(markers.size()>20)throw new IllegalArgumentException();
                var parsed=new HashMap<String,Long>();for(var entry:markers.entrySet()){
                    String member=UUID.fromString(entry.getKey()).toString();long value=entry.getValue().getAsLong();if(value<0)throw new IllegalArgumentException();parsed.put(member,Math.max(value,readMarkers.getOrDefault(id,Map.of()).getOrDefault(member,0L)));
                }
                if(group(id)!=null)readMarkers.put(id,Map.copyOf(parsed));
            }
            if(type.equals("activity")) {
                String id=uuid(data,"group");if(group(id)==null)return;
                com.wildfire.gui.ChatActivity.accept("#"+id,text(data,"name",16),text(data,"state",16));return;
            }
            if(!Set.of("list","history","live","result","roles","deleted").contains(type))return;
            if(data.has("groups")) {
                var array=data.getAsJsonArray("groups");if(array.size()>40)throw new IllegalArgumentException();
                List<Group> next=new ArrayList<>();var ids=new HashSet<String>();for(var item:array){var object=item.getAsJsonObject();var group=parse(object);if(!ids.add(group.id))throw new IllegalArgumentException();next.add(group);int rights=object.has("permissions")?object.get("permissions").getAsInt():0;if(rights<0||rights>15)throw new IllegalArgumentException();permissions.put(group.id,rights);}
                groups=List.copyOf(next);members.keySet().retainAll(ids);messages.keySet().retainAll(ids);loaded.retainAll(ids);requested.keySet().retainAll(ids);
                if(!ids.contains(open))open="";
                else readLocal(open);
                pendingReads.retainAll(ids);lastReadSent.keySet().retainAll(ids);faces.keySet().retainAll(ids);
                permissions.keySet().retainAll(ids);roles.keySet().retainAll(ids);deleted.keySet().retainAll(ids);
                readMarkers.keySet().retainAll(ids);
            }
            if(type.equals("history")) {
                String id=uuid(data,"group");if(group(id)==null)return;
                var people=data.getAsJsonArray("members");if(people.size()>20)throw new IllegalArgumentException();List<Member> roster=new ArrayList<>();
                acceptRoles(id,people);
                for(var item:people){var o=item.getAsJsonObject();roster.add(new Member(uuid(o,"uuid"),text(o,"name",16)));}
                var array=data.getAsJsonArray("messages");if(array.size()>100)throw new IllegalArgumentException();var rows=new TreeMap<Long,Message>();
                // A live packet received while history was loading must not disappear.
                for(var m:messages(id))rows.put(m.id,m);for(var item:array){var m=message(item.getAsJsonObject());rows.put(m.id,m);}
                while(rows.size()>100)rows.pollFirstEntry();messages.put(id,rows.values().stream().map(m->deleted.getOrDefault(id,Set.of()).contains(m.id)?new Message(m.id,m.sender,m.name,"[Сообщение удалено]",m.time):m).toList());members.put(id,List.copyOf(roster));loaded.add(id);if(open.equals(id)){readLocal(id);pendingReads.add(id);}
            }
            if(type.equals("live")) {
                String id=uuid(data,"group");var info=parse(data.getAsJsonObject("info"));if(!info.id.equals(id))throw new IllegalArgumentException();
                var next=new ArrayList<>(groups);next.removeIf(g->g.id.equals(id));next.add(info);next.sort(Comparator.comparingLong(Group::updatedAt).reversed());if(next.size()>40)throw new IllegalArgumentException();groups=List.copyOf(next);
                var parsedMessage=message(data.getAsJsonObject("message"));
                var m=deleted.getOrDefault(id,Set.of()).contains(parsedMessage.id)?new Message(parsedMessage.id,parsedMessage.sender,parsedMessage.name,"[Сообщение удалено]",parsedMessage.time):parsedMessage;
                var rows=new ArrayList<>(messages(id));boolean fresh=rows.stream().noneMatch(old->old.id==m.id);
                if(fresh){rows.add(m);rows.sort(Comparator.comparingLong(Message::id));while(rows.size()>100)rows.removeFirst();messages.put(id,List.copyOf(rows));}
                if(open.equals(id)){readLocal(id);pendingReads.add(id);}
                else if(fresh&&!outgoing(m))notifier.accept(info.title,m.name+": "+m.body);
            }
            if(type.equals("roles")) {String id=uuid(data,"group");if(group(id)==null)return;acceptRoles(id,data.getAsJsonArray("members"));}
            if(type.equals("deleted")) {
                String id=uuid(data,"group");if(group(id)==null)return;long message=data.get("message").getAsLong();if(message<1)return;
                var ids=deleted.computeIfAbsent(id,k->new LinkedHashSet<>());ids.add(message);while(ids.size()>1000)ids.remove(ids.iterator().next());
                messages.put(id,messages(id).stream().map(m->m.id==message?new Message(m.id,m.sender,m.name,"[Сообщение удалено]",m.time):m).toList());
            }
            if(type.equals("result")) {
                String rid=uuid(data,"request");boolean ok=data.get("ok").getAsBoolean();String error=ok?"":text(data,"error",256);
                results.put(rid,new Result(ok,ok?uuid(data,"group"):"",error));while(results.size()>32)results.remove(results.keySet().iterator().next());
                if(!ok){status=error;notifier.accept("Групповой чат",error);}
            }
            supported=true;if(!type.equals("result")||data.get("ok").getAsBoolean())status="";
        }catch(RuntimeException malformed){/* Ignore corrupt/unbounded snapshots without logging private messages. */}
    }
    public static void reset(){readMarkers.clear();groups=List.of();members.clear();faces.clear();permissions.clear();roles.clear();deleted.clear();messages.clear();results.clear();loaded.clear();requested.clear();pendingReads.clear();lastReadSent.clear();open="";supported=false;refreshed=0;status="Группы недоступны. Нужна поддержка на прокси";}
}
