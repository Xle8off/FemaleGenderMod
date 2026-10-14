import com.wildfire.client.towns.TownsClient;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class TownsClientCheck {
    private static void city(DataOutputStream out)throws Exception{
        out.writeUTF("00000000-0000-0000-0000-000000000001");out.writeUTF("Весна");out.writeUTF("Город");
        out.writeInt(23);out.writeInt(50);out.writeBoolean(true);
    }
    private static byte[] sample(boolean own)throws Exception{
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeByte(1);out.writeBoolean(true);out.writeBoolean(own);if(own)city(out);
        out.writeUTF(own?"Гражданин":"");out.writeInt(own?1:0);
        if(own){out.writeUTF("Xle8");out.writeUTF("Глава города");out.writeBoolean(true);out.writeLong(100);out.writeLong(200);}
        out.writeInt(1);city(out);out.writeInt(own?0:1);if(!own)city(out);return bytes.toByteArray();
    }
    private static void check(boolean condition){if(!condition)throw new AssertionError();}
    private static byte[] modern()throws Exception{
        return modern(2);
    }
    private static void modernCity(DataOutputStream out,int version)throws Exception{
        city(out);if(version>=3){out.writeInt(5);out.writeInt(16);out.writeInt(2);out.writeInt(6);out.writeUTF("a".repeat(64));out.writeUTF("b".repeat(64));}
    }
    private static byte[] modern(int version)throws Exception{
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeByte(version);out.writeBoolean(true);out.writeBoolean(true);out.writeBoolean(true);modernCity(out,version);
        out.writeUTF("Глава города");out.writeInt(1);out.writeUTF("00000000-0000-0000-0000-000000000002");
        out.writeUTF("Xle8");out.writeUTF("Гражданин");out.writeBoolean(true);out.writeLong(100);out.writeLong(200);
        out.writeInt(1);modernCity(out,version);out.writeInt(0);
        out.writeBoolean(true);out.writeBoolean(true);out.writeBoolean(true);out.writeBoolean(true);
        out.writeInt(1);out.writeUTF("00000000-0000-0000-0000-000000000003");out.writeUTF("NewPlayer");
        out.writeUTF("Приглашение отправлено");
        if(version>=3){out.writeInt(1);out.writeUTF("00000000-0000-0000-0000-000000000004");out.writeUTF("Applicant");out.writeInt(1);out.writeUTF("00000000-0000-0000-0000-000000000001");}
        return bytes.toByteArray();
    }
    private static void invalid(byte[] bytes)throws Exception{try{TownsClient.parse(bytes);throw new AssertionError("Accepted malformed packet");}catch(IOException expected){}}
    private static void maximumCity(DataOutputStream out)throws Exception{
        out.writeUTF("00000000-0000-0000-0000-000000000001");out.writeUTF("€".repeat(24));out.writeUTF("Малый город");
        out.writeInt(100);out.writeInt(100);out.writeBoolean(true);
        out.writeInt(4000);out.writeInt(4000);out.writeInt(256);out.writeInt(256);out.writeUTF("a".repeat(64));out.writeUTF("b".repeat(64));
    }
    private static void maximumPacket()throws Exception{
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeByte(3);out.writeBoolean(false);out.writeBoolean(true);out.writeBoolean(true);maximumCity(out);
        out.writeUTF("Заместитель главы");out.writeInt(100);
        for(int i=0;i<100;i++){out.writeUTF("00000000-0000-0000-0000-000000000002");out.writeUTF("€".repeat(16));out.writeUTF("Заместитель главы");out.writeBoolean(false);out.writeLong(0);out.writeLong(0);}
        out.writeInt(20);for(int i=0;i<20;i++)maximumCity(out);
        out.writeInt(10);for(int i=0;i<10;i++)maximumCity(out);
        for(int i=0;i<4;i++)out.writeBoolean(true);
        out.writeInt(20);for(int i=0;i<20;i++){out.writeUTF("00000000-0000-0000-0000-000000000003");out.writeUTF("€".repeat(16));}
        out.writeUTF("€".repeat(160));out.writeInt(40);
        for(int i=0;i<40;i++){out.writeUTF("00000000-0000-0000-0000-000000000004");out.writeUTF("€".repeat(16));}
        out.writeInt(5);for(int i=0;i<5;i++)out.writeUTF("00000000-0000-0000-0000-000000000001");
        check(bytes.size()<=30000);check(TownsClient.parse(bytes.toByteArray()).residents().size()==100);
    }
    public static void main(String[] args)throws Exception{
        check(com.wildfire.gui.PrivilegeTime.label(-1,1000).isEmpty());
        check(com.wildfire.gui.PrivilegeTime.label(0,1000).equals("Бессрочно"));
        check(com.wildfire.gui.PrivilegeTime.label(1000,1000).equals("Срок истёк"));
        check(com.wildfire.gui.PrivilegeTime.label(1000+86400000,1000).equals("1д 0ч"));
        maximumPacket();
        var fourth=TownsClient.parse(modern(4));
        check(fourth.protocol()==4&&TownsClient.pendingCities(fourth).size()==1);
        check(TownsClient.pendingCities(fourth).getFirst().id().equals(fourth.requestedTowns().getFirst()));
        var requestBytes=new AtomicReference<byte[]>();
        TownsClient.configure(requestBytes::set,()->true);
        TownsClient.accept(modern(3));
        check(!TownsClient.action(16,fourth.requestedTowns().getFirst()));
        TownsClient.accept(modern(4));
        check(TownsClient.action(16,fourth.requestedTowns().getFirst()));
        var cancelInput=new DataInputStream(new ByteArrayInputStream(requestBytes.get()));
        check(cancelInput.readUnsignedByte()==4&&cancelInput.readUnsignedByte()==16);
        check(cancelInput.readUTF().equals(fourth.requestedTowns().getFirst())&&cancelInput.readUTF().isEmpty());
        TownsClient.reset();
        var member=TownsClient.parse(sample(true));check(member.own().capital()&&member.residents().getFirst().online()&&member.invitations().isEmpty());
        var outsider=TownsClient.parse(sample(false));check(outsider.own()==null&&outsider.invitations().size()==1);
        var modern=TownsClient.parse(modern());check(modern.canRole()&&modern.founder()&&modern.candidates().size()==1&&modern.residents().getFirst().id().length()==36&&!modern.feedback().isEmpty());
        var third=TownsClient.parse(modern(3));check(third.own().cityChunks()==5&&third.own().farmLimit()==6&&third.own().avatar().length()==64&&third.applications().size()==1&&third.requestedTowns().size()==1);
        var roster=com.wildfire.gui.TownRosterLayout.rows(java.util.List.of(
            new TownsClient.Resident("","Zulu","Гражданин",false,0,0),
            new TownsClient.Resident("","Mayor","Глава города",true,0,0),
            new TownsClient.Resident("","Alpha","Гражданин",true,0,0)));
        check(roster.size()==5&&roster.get(0).resident()==null&&roster.get(0).role().equals("Глава города"));
        check(roster.get(2).members()==2&&roster.get(3).resident().name().equals("Alpha"));
        byte[] broken=sample(true);broken[0]=2;invalid(broken);
        broken=sample(false);invalid(java.util.Arrays.copyOf(broken,broken.length-1));
        invalid(java.util.Arrays.copyOf(broken,broken.length+1));invalid(new byte[30001]);
        var empty=new ByteArrayOutputStream();var out=new DataOutputStream(empty);
        out.writeByte(1);out.writeBoolean(false);out.writeBoolean(false);out.writeUTF("");out.writeInt(-1);invalid(empty.toByteArray());
        var sent=new AtomicReference<byte[]>();TownsClient.configure(sent::set,()->true);
        check(TownsClient.action(3,UUID.randomUUID().toString()));var request=new DataInputStream(new ByteArrayInputStream(sent.get()));
        check(request.readUnsignedByte()==4&&request.readUnsignedByte()==3&&request.readUTF().length()==36&&request.readUTF().isEmpty()&&request.available()==0);
        TownsClient.accept(sample(true));check(TownsClient.snapshot()!=null);
        var opened=new java.util.concurrent.atomic.AtomicInteger();TownsClient.setOpenHandler(opened::incrementAndGet);
        TownsClient.accept(modern());check(opened.get()==1);
        byte[] noOpen=modern();noOpen[1]=0;TownsClient.accept(noOpen);check(opened.get()==1);
        check(TownsClient.action(8,"00000000-0000-0000-0000-000000000002","deputy"));
        request=new DataInputStream(new ByteArrayInputStream(sent.get()));check(request.readUnsignedByte()==2&&request.readUnsignedByte()==8&&request.readUTF().length()==36&&request.readUTF().equals("deputy")&&request.available()==0);
        TownsClient.accept(modern(3));check(TownsClient.action(14,"","a".repeat(64)));
        request=new DataInputStream(new ByteArrayInputStream(sent.get()));check(request.readUnsignedByte()==3&&request.readUnsignedByte()==14&&request.readUTF().isEmpty()&&request.readUTF().length()==64);
        TownsClient.configure(sent::set,()->false);check(TownsClient.snapshot()==null&&!TownsClient.action(0,""));
        TownsClient.reset();check(TownsClient.snapshot()==null);
        var clockField=TownsClient.class.getDeclaredField("clock");clockField.setAccessible(true);
        var time=new java.util.concurrent.atomic.AtomicLong(100000);
        clockField.set(null,(java.util.function.LongSupplier)time::get);
        var requests=new java.util.concurrent.atomic.AtomicInteger();
        TownsClient.configure(bytes->requests.incrementAndGet(),()->true);
        TownsClient.refresh();check(requests.get()==1);
        time.addAndGet(3000);TownsClient.refresh();check(requests.get()==1);
        time.addAndGet(3001);TownsClient.refresh();check(requests.get()==2);
        TownsClient.accept(sample(true));var cached=TownsClient.snapshot();check(cached!=null);
        time.addAndGet(3000);TownsClient.refresh();
        time.addAndGet(31000);check(TownsClient.snapshot()==cached);
        check(!TownsClient.status().contains("Обновите плагин"));
        TownsClient.accept(new byte[]{0});check(TownsClient.snapshot()==cached);
        TownsClient.reset();
        TownsClient.configure(bytes->{requests.incrementAndGet();try{TownsClient.accept(sample(true));}catch(Exception e){throw new RuntimeException(e);}},()->true);
        TownsClient.refresh();check(TownsClient.snapshot()!=null);
        int before=requests.get();time.addAndGet(3000);TownsClient.refresh();check(requests.get()==before+1);
        clockField.set(null,(java.util.function.LongSupplier)System::currentTimeMillis);TownsClient.reset();
        System.out.println("Towns protocol checks passed: membership, invitations, bounds, malformed packets, requests, unavailable server, reset");
    }
}
