package com.wildfire.client.music;
import com.google.gson.*;
import java.util.*;
import java.util.function.*;
/** Server snapshots contain permissions, active polls and only the current player's ballot. */
public final class CampaignClient {
    public record Poll(String id,String title,String text,List<String> options,boolean multiple,long closesAt,
                       boolean closed,boolean submitted,List<Integer> choices,List<Integer> counts,int totalVoters){}
    private static Consumer<String> sender=value->{};
    private static BooleanSupplier supported=()->false;
    private static BiConsumer<String,String> notifier=(title,text)->{};
    private static List<Poll> polls=List.of(),history=List.of();
    private static final Set<String> notified=new LinkedHashSet<>();
    private static final Set<String> resultNotified=new LinkedHashSet<>();
    private static long sent,received,offset,pendingAt;
    private static boolean admin,failed;
    private static String feedback="";
    private static String createPayload="",createKey="",pendingId="",pendingVote="";
    private CampaignClient(){}
    public static void configure(Consumer<String> send,BooleanSupplier available){sender=send;supported=available;}
    public static void notifier(BiConsumer<String,String> receiver){notifier=receiver;}
    public static boolean admin(){return admin&&System.currentTimeMillis()-received<30000&&SpringPlusSession.enabled();}
    public static List<Poll> active(){
        if(!SpringPlusSession.enabled())return List.of();
        long now=serverNow();return polls.stream().filter(p->!p.closed()&&p.closesAt()>now).toList();
    }
    public static List<Poll> history(){return admin()?history:List.of();}
    public static long serverNow(){return System.currentTimeMillis()+offset;}
    public static boolean pending(){return pendingAt!=0&&System.currentTimeMillis()-pendingAt<12000;}
    public static String feedback(){return feedback;}
    public static boolean failed(){return failed;}
    public static void tick(){
        if(!SpringPlusSession.enabled()){reset();return;}
        long now=System.currentTimeMillis();
        if(pendingAt!=0&&now-pendingAt>=12000){pendingAt=0;feedback="Нет ответа. Можно повторить отправку";failed=true;}
        if(now-sent<10000)return;
        if(send(Map.of("action","state")))sent=now;
    }
    public static boolean send(Map<String,Object> value){
        if(!SpringPlusSession.enabled()||!supported.getAsBoolean())return false;
        String encoded=new Gson().toJson(value);if(encoded.length()>4096)return false;
        try{sender.accept(encoded);return true;}catch(RuntimeException unavailable){return false;}
    }
    public static void vote(Poll poll,Collection<Integer> choices){
        if(pending())return;
        pendingId=UUID.randomUUID().toString();pendingVote=poll.id();
        pendingAt=System.currentTimeMillis();feedback="Отправляем голос…";failed=false;
        if(!send(Map.of("action","vote","id",poll.id(),"choices",new ArrayList<>(choices),"requestId",pendingId))){pendingAt=0;feedback="Нет связи с ядром";failed=true;}
    }
    public static void create(Map<String,Object> input){
        if(!admin()||pending())return;
        String payload=new Gson().toJson(input);
        if(!payload.equals(createPayload)||createKey.isEmpty()){createPayload=payload;createKey=UUID.randomUUID().toString();}
        var request=new LinkedHashMap<>(input);request.put("action","create");request.put("requestId",createKey);
        pendingId=createKey;pendingVote="";
        pendingAt=System.currentTimeMillis();feedback="Отправляем…";failed=false;
        if(!send(request)){pendingAt=0;feedback="Нет связи с ядром";failed=true;}
    }
    public static void close(Poll poll){
        if(!admin()||pending())return;
        pendingId=UUID.randomUUID().toString();pendingVote="";
        pendingAt=System.currentTimeMillis();feedback="Завершаем голосование…";failed=false;
        if(!send(Map.of("action","close","id",poll.id(),"requestId",pendingId))){pendingAt=0;feedback="Нет связи с ядром";failed=true;}
    }
    public static void accept(JsonObject state){
        try {
            var next=parse(state.getAsJsonArray("polls"));var previous=parse(state.getAsJsonArray("history"));
            var results=parse(state.has("results")?state.getAsJsonArray("results"):new JsonArray());
            boolean allowed=state.get("admin").getAsBoolean();long serverTime=state.get("serverTime").getAsLong();
            String reply=state.has("feedback")?state.get("feedback").getAsString():"";
            String requestId=state.has("requestId")?state.get("requestId").getAsString():"";
            boolean replyFailed=state.has("failed")&&state.get("failed").getAsBoolean();
            if(!state.get("admin").isJsonPrimitive()||!state.get("admin").getAsJsonPrimitive().isBoolean()
                ||reply.length()>64)throw new IllegalArgumentException();
            polls=next;history=allowed?previous:List.of();admin=allowed;
            received=System.currentTimeMillis();offset=serverTime-received;
            if(!reply.isEmpty()&&(pendingId.isEmpty()||requestId.equals(pendingId))){
                pendingAt=0;failed=replyFailed;feedback=message(reply);
                if(reply.equals("POLL_CREATED")||reply.equals("NOTICE_ACCEPTED")){createPayload="";createKey="";}
            }
            if(!pendingVote.isEmpty()&&next.stream().anyMatch(p->p.id().equals(pendingVote)&&p.submitted())){
                pendingAt=0;pendingVote="";feedback=message("VOTE_SAVED");failed=false;
            }
            for(var poll:active())if(notified.add(poll.id())){
                notifier.accept("Голосование: "+poll.title(),"Откройте вкладку «Голосования» в меню. "+(poll.multiple()?"Можно выбрать несколько вариантов.":"Выберите один вариант."));
            }
            for(var poll:results)if(resultNotified.add(poll.id()))notifier.accept("Итоги: "+poll.title(),resultSummary(poll));
            while(notified.size()>128)notified.remove(notified.iterator().next());
            while(resultNotified.size()>128)resultNotified.remove(resultNotified.iterator().next());
        }catch(RuntimeException invalid){/* A malformed snapshot never grants admin permissions. */}
    }
    private static String resultSummary(Poll poll){
        int best=poll.counts().stream().mapToInt(Integer::intValue).max().orElse(0);var winners=new ArrayList<String>();
        for(int i=0;i<poll.options().size()&&i<poll.counts().size();i++)if(poll.counts().get(i)==best)winners.add(poll.options().get(i));
        return "Победило: "+String.join(", ",winners)+" (голосов: "+best+"). Всего участников: "+poll.totalVoters()+".";
    }
    private static List<Poll> parse(JsonArray array){
        if(array==null||array.size()>30)throw new IllegalArgumentException();
        var result=new ArrayList<Poll>();
        for(var element:array){
            var p=element.getAsJsonObject();String id=UUID.fromString(p.get("id").getAsString()).toString();
            String title=p.get("title").getAsString(),text=p.get("text").getAsString();
            if(title.length()>80||text.length()>512)throw new IllegalArgumentException();
            var options=new ArrayList<String>();
            for(var option:p.getAsJsonArray("options")){String label=option.getAsString();if(label.length()>60)throw new IllegalArgumentException();options.add(label);}
            if(options.size()<2||options.size()>8)throw new IllegalArgumentException();
            var choices=integers(p,"choices",options.size());
            var counts=integers(p,"counts",Integer.MAX_VALUE);
            if(!counts.isEmpty()&&counts.size()!=options.size())throw new IllegalArgumentException();
            result.add(new Poll(id,title,text,List.copyOf(options),p.get("multiple").getAsBoolean(),p.get("closesAt").getAsLong(),
                p.get("closed").getAsBoolean(),p.has("submitted")&&p.get("submitted").getAsBoolean(),choices,counts,
                p.has("totalVoters")?Math.max(0,p.get("totalVoters").getAsInt()):0));
        }return List.copyOf(result);
    }
    private static List<Integer> integers(JsonObject data,String key,int bound){
        if(!data.has(key))return List.of();var values=new ArrayList<Integer>();
        var array=data.getAsJsonArray(key);if(array.size()>8)throw new IllegalArgumentException();
        for(var item:array){int value=item.getAsInt();if(value<0||value>=bound)throw new IllegalArgumentException();values.add(value);}
        return List.copyOf(values);
    }
    private static String message(String code){return switch(code){
        case "VOTE_SAVED"->"Ваш голос сохранён";case "POLL_CREATED"->"Голосование создано";
        case "NOTICE_ACCEPTED"->"Уведомление принято в доставку";case "POLL_CLOSED"->"Голосование завершено";
        case "ALREADY_VOTED"->"Вы уже проголосовали";case "FORBIDDEN"->"Недостаточно прав";
        case "UNKNOWN_PLAYER"->"Игрок не найден";case "INVALID_NOTIFICATION_BODY","INVALID_NOTIFICATION_TEXT"->"Проверьте заголовок и текст";
        case "INVALID_OPTIONS","DUPLICATE_OPTIONS"->"Нужно 2–8 разных вариантов";
        case "INVALID_DURATION"->"Срок: от 1 минуты до 30 дней";case "INVALID_CHOICES"->"Проверьте выбранные варианты";
        case "RATE_LIMITED"->"Слишком часто. Подождите";case "TOO_MANY_ACTIVE_POLLS"->"Одновременно доступны 4 голосования";
        case "PLAYER_OFFLINE_OR_CLIENT_UNAVAILABLE"->"Игрок не в сети. Включите отложенную доставку";
        default->"Не удалось выполнить действие: "+code;
    };}
    public static void reset(){polls=history=List.of();admin=false;sent=received=offset=pendingAt=0;feedback=createPayload=createKey=pendingId=pendingVote="";failed=false;}
}
