import com.wildfire.client.music.*;
import com.google.gson.*;
import java.util.*;
public final class CampaignClientCheck {
    private static final Gson JSON=new Gson();
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Map<String,Object> poll(String id,long expires,boolean multiple){
        var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("title","Question");row.put("text","Description");
        row.put("options",List.of("A","B"));row.put("multiple",multiple);row.put("closesAt",expires);
        row.put("closed",false);row.put("submitted",false);row.put("choices",List.of());return row;
    }
    private static void accept(boolean admin,List<?> active,List<?> history,long now){
        ApiNoticeInbox.receive(JSON.toJson(Map.of("type","state","admin",admin,"polls",active,"history",history,"serverTime",now,"feedback","","failed",false)));
    }
    public static void main(String[] args)throws Exception{
        SpringPlusSession.configure(()->true);CampaignClient.reset();
        var sent=new ArrayList<String>();var notices=new ArrayList<String>();var acks=new ArrayList<String>();
        CampaignClient.configure(sent::add,()->true);ApiNoticeInbox.configure(acks::add,(a,b)->notices.add(a));
        long now=System.currentTimeMillis();String id=UUID.randomUUID().toString();var poll=poll(id,now+60000,true);
        accept(true,List.of(poll),List.of(poll),now);
        check(CampaignClient.admin()&&CampaignClient.active().size()==1,"server permission and temporary tab");
        check(notices.size()==1&&acks.isEmpty(),"poll popup once, no legacy ACK");
        accept(true,List.of(poll),List.of(poll),now);
        check(notices.size()==1,"heartbeat does not duplicate notification");
        CampaignClient.vote(CampaignClient.active().getFirst(),List.of(0,1));
        var request=JsonParser.parseString(sent.getLast()).getAsJsonObject();
        check(request.get("action").getAsString().equals("vote")&&request.getAsJsonArray("choices").size()==2,"multiple ballot packet");
        check(!request.has("player")&&!request.has("token"),"no client asserted voter or API secret");
        accept(false,List.of(poll),List.of(),now);
        check(!CampaignClient.admin()&&CampaignClient.history().isEmpty(),"permission revocation hides admin results");
        poll.put("closesAt",now-1);accept(false,List.of(poll),List.of(),now);
        check(CampaignClient.active().isEmpty(),"expired poll removes tab locally");
        var invalid=Map.of("type","state","admin",true,"polls",List.of(Map.of("id","invalid")),"history",List.of(),"serverTime",now);
        ApiNoticeInbox.receive(JSON.toJson(invalid));check(!CampaignClient.admin(),"invalid snapshot cannot grant admin");
        poll.put("closesAt",now+60000);accept(true,List.of(poll),List.of(poll),now);
        var field=CampaignClient.class.getDeclaredField("pendingAt");field.setAccessible(true);field.setLong(null,0);
        var create=Map.<String,Object>of("type","text","title","Title","text","Body","nick","Alice","keep",true);
        CampaignClient.create(create);String first=JsonParser.parseString(sent.getLast()).getAsJsonObject().get("requestId").getAsString();
        field.setLong(null,0);CampaignClient.create(create);
        check(first.equals(JsonParser.parseString(sent.getLast()).getAsJsonObject().get("requestId").getAsString()),"retry keeps idempotency key");
        SpringPlusSession.configure(()->false);CampaignClient.tick();
        check(!CampaignClient.admin()&&CampaignClient.active().isEmpty(),"disconnect clears authority");
        System.out.println("Campaign client checks passed: permission, expiry, deduplication, ballots, privacy, malformed state, idempotent retry");
    }
}
