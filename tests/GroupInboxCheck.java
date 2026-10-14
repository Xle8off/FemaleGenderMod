import com.wildfire.client.music.*;
import com.google.gson.*;
import java.util.*;

public final class GroupInboxCheck {
    static final Gson JSON=new Gson();static final String SELF=UUID.randomUUID().toString(),OTHER=UUID.randomUUID().toString(),GID=UUID.randomUUID().toString();
    static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    static Map<String,Object> group(int unread){return Map.of("id",GID,"title","Друзья 😀","owner",SELF,"count",2,"unread",unread,"updatedAt",123L);}
    static Map<String,Object> message(long id,String sender,String body){return Map.of("id",id,"sender",sender,"name",sender.equals(SELF)?"Owner":"Alice","body",body,"time",1000L+id);}
    static void accept(Map<String,?> packet){GroupInbox.accept(JSON.toJson(packet));}
    public static void main(String[]args) {
        var sent=new ArrayList<String>();var notices=new ArrayList<String>();SpringPlusSession.configure(()->true);
        var apiAcks=new ArrayList<String>();var apiNotices=new ArrayList<String>();
        ApiNoticeInbox.configure(apiAcks::add,(title,text)->apiNotices.add(title+": "+text));
        String noticeId=UUID.randomUUID().toString(),notice=JSON.toJson(Map.of("id",noticeId,"title","System","text","Привет 😀"));
        ApiNoticeInbox.receive(notice);ApiNoticeInbox.receive(notice);
        check(apiNotices.size()==1&&apiAcks.equals(List.of(noticeId,noticeId)),"API retries acknowledged without duplicate notification");
        ApiNoticeInbox.receive(JSON.toJson(Map.of("id",UUID.randomUUID().toString(),"title","System","text","x".repeat(513))));
        check(apiNotices.size()==1&&apiAcks.size()==2,"invalid API payload never inserted or acknowledged");
        GroupInbox.configure(sent::add,()->SELF,(a,b)->notices.add(a+": "+b));GroupInbox.reset();
        accept(Map.of("type","list","groups",List.of(group(2))));check(GroupInbox.supported()&&GroupInbox.unread()==2,"catalog and unread");
        GroupInbox.open(GID);check(GroupInbox.unread()==0,"opening clears local unread");
        accept(Map.of("type","live","group",GID,"info",group(1),"message",message(3,OTHER,"👨‍👩‍👧‍👦")));
        check(GroupInbox.messages(GID).size()==1 && notices.isEmpty(),"open group has no popup");
        int before=sent.size();for(int i=0;i<20;i++){accept(Map.of("type","live","group",GID,"info",group(1),"message",message(3,OTHER,"👨‍👩‍👧‍👦")));GroupInbox.refresh();}
        check(sent.size()<=before+2,"read receipts coalesced; duplicate live packet not repeated");
        accept(Map.of("type","history","group",GID,"groups",List.of(group(0)),"members",List.of(Map.of("uuid",SELF,"name","Owner"),Map.of("uuid",OTHER,"name","Alice")),"messages",List.of(message(1,SELF,"Hello"),message(2,OTHER,"[Стикер:samson_vk/3710957]"))));
        check(GroupInbox.messages(GID).size()==3 && GroupInbox.messages(GID).getLast().id()==3,"live message survives delayed history");
        check(GroupInbox.members(GID).size()==2 && GroupInbox.historyStatus(GID).startsWith("Пока"),"roster and loaded status");
        GroupInbox.close();accept(Map.of("type","live","group",GID,"info",group(1),"message",message(4,OTHER,"😀")));check(notices.size()==1,"closed group notifies once");
        accept(Map.of("type","live","group",GID,"info",group(1),"message",message(4,OTHER,"😀")));check(notices.size()==1,"duplicate popup suppressed");
        accept(Map.of("type","live","group",GID,"info",group(0),"message",message(5,SELF,"Own")));check(notices.size()==1,"own message is not incoming popup");
        String request=GroupInbox.request("create",Map.of("title","New","members",List.of("Alice")));
        accept(Map.of("type","result","request",request,"ok",true,"group",GID,"groups",List.of(group(0))));check(GroupInbox.result(request).ok(),"request correlated");
        var oversize=new ArrayList<Map<String,Object>>();for(int i=0;i<41;i++){var g=new HashMap<>(group(0));g.put("id",UUID.randomUUID().toString());oversize.add(g);}
        accept(Map.of("type","list","groups",oversize));check(GroupInbox.groups().size()==1,"oversize rejected");
        accept(Map.of("type","list","groups",List.of(group(0),group(0))));check(GroupInbox.groups().size()==1,"duplicate catalog ids rejected");
        accept(Map.of("type","deleted","group",GID,"message",3));
        check(GroupInbox.messages(GID).stream().filter(m->m.id()==3).findFirst().orElseThrow().body().equals("[Сообщение удалено]"),"deletion updates cached bubble");
        accept(Map.of("type","history","group",GID,"members",List.of(Map.of("uuid",SELF,"name","Owner","role","owner","permissions",15),Map.of("uuid",OTHER,"name","Alice","role","admin","permissions",5)),"messages",List.of(message(3,OTHER,"Old body"))));
        check(GroupInbox.messages(GID).stream().filter(m->m.id()==3).findFirst().orElseThrow().body().equals("[Сообщение удалено]"),"stale history cannot resurrect deleted text");
        check(GroupInbox.role(GID,OTHER).permissions()==5&&GroupInbox.role(GID,OTHER).role().equals("admin"),"per-member admin roles received");
        var delegated=new HashMap<String,Object>(group(0));delegated.put("owner",OTHER);delegated.put("permissions",5);
        accept(Map.of("type","list","groups",List.of(delegated)));
        check(GroupInbox.allowed(GID,1)&&GroupInbox.allowed(GID,4)&&!GroupInbox.allowed(GID,2)&&!GroupInbox.allowed(GID,8),"UI permissions are individually gated");
        accept(Map.of("type","roles","group",GID,"members",List.of(Map.of("uuid",SELF,"name","Owner","role","member","permissions",0),Map.of("uuid",OTHER,"name","Alice","role","owner","permissions",15))));
        check(GroupInbox.role(GID,SELF).role().equals("member"),"role pushes update roster");
        accept(Map.of("type","list","groups",List.of()));check(GroupInbox.messages(GID).isEmpty()&&GroupInbox.members(GID).isEmpty(),"removal purges inaccessible history");
        SpringPlusSession.configure(()->false);check(GroupInbox.request("list",Map.of()).isEmpty(),"unsupported sessions never send");accept(Map.of("type","list","groups",List.of(group(0))));check(GroupInbox.groups().isEmpty(),"unsupported sessions reject incoming state");
        GroupInbox.reset();check(!GroupInbox.supported()&&GroupInbox.unread()==0,"disconnect clears state");
        System.out.println("Group inbox checks passed: membership changes, Unicode/stickers, unread, delayed history, duplicate delivery, coalesced reads, correlated results, bounds, session gate");
    }
}
