package com.wildfire.client.towns;

import java.io.*;
import java.util.*;
import java.util.function.*;

public final class TownsClient {
    public record City(String id,String name,String type,int population,int capacity,boolean capital,int cityChunks,int cityLimit,int farmChunks,int farmLimit,String avatar,String cover){}
    public record Resident(String id,String name,String role,boolean online,long joined,long lastSeen){}
    public record Candidate(String id,String name){}
    public record Snapshot(boolean passport,City own,String role,List<Resident> residents,List<City> cities,List<City> invitations,
        boolean canInvite,boolean canKick,boolean canRole,boolean founder,List<Candidate> candidates,String feedback,int protocol,List<Candidate> applications,List<String> requestedTowns){}
    private static Consumer<byte[]> sender=bytes->{};
    private static BooleanSupplier available=()->false;
    private static Snapshot snapshot;
    private static long requestedAt,receivedAt;
    private static long waitingSince;
    private static int requestProtocol=4;
    private static LongSupplier clock=System::currentTimeMillis;
    private static final System.Logger LOG=System.getLogger("SpringPlus-Towns");
    private static String error="";
    private static Runnable openHandler=()->{};
    private static Runnable imageTicket=()->{};
    public static void configureImageTicket(Runnable handler){imageTicket=handler;}
    public static void requestImageTicket(){imageTicket.run();}
    private static long announcedAt;
    public static void setOpenHandler(Runnable handler){openHandler=handler;}
    public static void announce(){if(available.getAsBoolean()&&System.currentTimeMillis()-announcedAt>60000&&action(9,""))announcedAt=System.currentTimeMillis();}
    private TownsClient(){}
    public static void configure(Consumer<byte[]> send,BooleanSupplier supported){sender=send;available=supported;}
    public static Snapshot snapshot(){
        if(!available.getAsBoolean())return null;
        return snapshot;
    }
    public static String status(){
        if(!available.getAsBoolean())return "На этом сервере интеграция Towns недоступна";
        if(!error.isEmpty())return error;
        return waitingSince!=0&&clock.getAsLong()-waitingSince>6000?"Нет ответа от Towns. Повторяем запрос…":"Загружаем города…";
    }
    public static void refresh(){
        long now=clock.getAsLong();
        if(waitingSince!=0&&now-waitingSince<6000)return;
        if(waitingSince!=0&&receivedAt==0)requestProtocol=Math.max(2,requestProtocol-1);
        if(now-requestedAt>=3000)action(0,"");
    }
    public static boolean action(int action,String target){
        return action(action,target,"");
    }
    public static boolean action(int action,String target,String argument){
        if(action<0||action>16||target.length()>36||argument.length()>(action>=14&&action<=15?64:16)||!available.getAsBoolean())return false;
        try{
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            if(action>10&&requestProtocol<3)return false;
            if(action==16&&(snapshot==null||snapshot.protocol()<4))return false;
            out.writeByte(requestProtocol);out.writeByte(action);out.writeUTF(target);out.writeUTF(argument);
            long now=clock.getAsLong();
            // Set pending before sending: a synchronous response must be able to clear it.
            if(action!=9){requestedAt=now;if(waitingSince==0||now-waitingSince>=6000)waitingSince=now;}
            sender.accept(bytes.toByteArray());
            if(action!=9)LOG.log(System.Logger.Level.INFO,"[towns-ui] request action="+action+" bytes="+bytes.size());
            return true;
        }catch(Exception e){error="Не удалось отправить действие Towns";return false;}
    }
    public static void accept(byte[] bytes){
        if(bytes.length>0&&bytes[0]==127){try{TerritoryPresence.accept(bytes);}catch(IOException ignored){}return;}
        try{
            snapshot=parse(bytes);receivedAt=clock.getAsLong();
            requestProtocol=Math.max(2,snapshot.protocol());
            LOG.log(System.Logger.Level.INFO,"[towns-ui] response bytes="+bytes.length+" latencyMs="+(waitingSince==0?0:receivedAt-waitingSince)+" cities="+snapshot.cities().size());
            waitingSince=0;error="";if(bytes[0]>=2&&bytes[1]!=0)openHandler.run();
        }catch(Exception e){error="Не удалось прочитать данные Towns";LOG.log(System.Logger.Level.WARNING,"[towns-ui] invalid response bytes="+bytes.length,e);}
    }
    public static Snapshot parse(byte[] bytes)throws IOException{
        if(bytes.length>TownsPacket.MAX_BYTES)throw new IOException("Size");
        var in=new DataInputStream(new ByteArrayInputStream(bytes));
        int version=in.readUnsignedByte();if(version<1||version>4)throw new IOException("Version");
        if(version>=2)in.readBoolean();
        boolean passport=in.readBoolean();City own=in.readBoolean()?city(in,version):null;
        String role=text(in,40);int count=count(in,100);var residents=new ArrayList<Resident>();
        for(int i=0;i<count;i++)residents.add(new Resident(version>=2?id(in):"",text(in,36),text(in,40),in.readBoolean(),in.readLong(),in.readLong()));
        var cities=cities(in,200,version);var invitations=cities(in,100,version);
        boolean invite=false,kick=false,roles=false,founder=false;var candidates=new ArrayList<Candidate>();String feedback="";
        if(version>=2){
            invite=in.readBoolean();kick=in.readBoolean();roles=in.readBoolean();founder=in.readBoolean();
            int candidateCount=count(in,50);for(int i=0;i<candidateCount;i++)candidates.add(new Candidate(id(in),text(in,36)));
            feedback=text(in,160);
        }
        var applications=new ArrayList<Candidate>();var requested=new ArrayList<String>();
        if(version>=3){
            int n=count(in,100);for(int i=0;i<n;i++)applications.add(new Candidate(id(in),text(in,36)));
            n=count(in,5);for(int i=0;i<n;i++)requested.add(id(in));
        }
        if(in.available()!=0)throw new IOException("Trailing bytes");
        return new Snapshot(passport,own,role,List.copyOf(residents),cities,invitations,invite,kick,roles,founder,List.copyOf(candidates),feedback,version,List.copyOf(applications),List.copyOf(requested));
    }
    private static City city(DataInputStream in,int version)throws IOException{
        String id=id(in);
        String name=text(in,64),type=text(in,40);int population=in.readInt(),capacity=in.readInt();
        if(population<0||capacity<1||population>10000||capacity>10000)throw new IOException("Population");
        boolean capital=in.readBoolean();int city=-1,limit=-1,farm=-1,farmLimit=-1;
        if(version>=3){city=countValue(in);limit=countValue(in);farm=countValue(in);farmLimit=countValue(in);}
        String avatar=version>=3?image(in):"",cover=version>=3?image(in):"";
        return new City(id,name,type,population,capacity,capital,city,limit,farm,farmLimit,avatar,cover);
    }
    private static String image(DataInputStream in)throws IOException{String value=text(in,64);if(!value.isEmpty()&&!value.matches("[a-f0-9]{64}"))throw new IOException("Image ID");return value;}
    private static int countValue(DataInputStream in)throws IOException{int n=in.readInt();if(n<0||n>100000)throw new IOException("Chunk count");return n;}
    private static String id(DataInputStream in)throws IOException{String value=text(in,36);try{UUID.fromString(value);}catch(Exception e){throw new IOException("ID");}return value;}
    private static List<City> cities(DataInputStream in,int max,int version)throws IOException{
        int count=count(in,max);var result=new ArrayList<City>();for(int i=0;i<count;i++)result.add(city(in,version));return List.copyOf(result);
    }
    private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IOException("Count");return n;}
    private static String text(DataInputStream in,int max)throws IOException{
        String value=in.readUTF();if(value.length()>max||value.indexOf('§')>=0||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Text");return value;
    }
    public static void reset(){TerritoryPresence.reset();snapshot=null;requestedAt=receivedAt=announcedAt=waitingSince=0;requestProtocol=4;error="";}
    public static List<City> pendingCities(Snapshot data){
        if(data==null)return List.of();
        var byId=new HashMap<String,City>();data.cities().forEach(c->byId.put(c.id(),c));
        return data.requestedTowns().stream().distinct().map(id->byId.getOrDefault(id,
            new City(id,"Город","Заявка отправлена",0,0,false,-1,-1,-1,-1,"",""))).toList();
    }
}
