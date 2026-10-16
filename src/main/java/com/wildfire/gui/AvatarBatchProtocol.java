package com.wildfire.gui;

import com.google.gson.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;

/** Small face-only protocol. No remote URLs or full skins are accepted. */
public final class AvatarBatchProtocol {
    public static final class LimitedBody implements java.net.http.HttpResponse.BodySubscriber<byte[]> {
        private final java.net.http.HttpResponse.BodySubscriber<byte[]> delegate=java.net.http.HttpResponse.BodySubscribers.ofByteArray();
        private final int maximum;private long count;private java.util.concurrent.Flow.Subscription subscription;private boolean failed;
        public LimitedBody(int maximum){this.maximum=maximum;}
        public java.util.concurrent.CompletionStage<byte[]> getBody(){return delegate.getBody();}
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription){this.subscription=subscription;delegate.onSubscribe(subscription);}
        public void onNext(List<ByteBuffer> chunks){
            if(failed)return;for(var chunk:chunks)count+=chunk.remaining();
            if(count>maximum){failed=true;subscription.cancel();delegate.onError(new java.io.IOException("Avatar body too large"));}else delegate.onNext(chunks);
        }
        public void onError(Throwable error){if(!failed){failed=true;delegate.onError(error);}}
        public void onComplete(){if(!failed)delegate.onComplete();}
    }
    public record Face(String name,String status,String hash,byte[] png,boolean unchanged){}
    public static boolean name(String value){return value!=null&&value.matches("[A-Za-z0-9_]{1,16}");}
    public static boolean hash(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    public static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    public static void validateImage(byte[] bytes,String hash){
        if(bytes.length<24||bytes.length>32768||!sha(bytes).equals(hash)||
            ByteBuffer.wrap(bytes).getLong()!=0x89504E470D0A1A0AL||ByteBuffer.wrap(bytes,16,8).getInt()!=64||ByteBuffer.wrap(bytes,20,4).getInt()!=64)
            throw new IllegalArgumentException("Invalid avatar image");
    }
    public static String request(Map<String,String> players){
        if(players.isEmpty()||players.size()>32)throw new IllegalArgumentException("Avatar batch size");
        var array=new JsonArray();var seen=new HashSet<String>();
        players.forEach((name,hash)->{
            if(!name(name)||!hash.isEmpty()&&!hash(hash)||!seen.add(name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Avatar player");
            var player=new JsonObject();player.addProperty("name",name);player.addProperty("hash",hash);array.add(player);
        });
        var root=new JsonObject();root.addProperty("version",1);root.add("players",array);return root.toString();
    }
    public static List<Face> parse(String json,Map<String,String> requested){
        if(json.length()>2097152)throw new IllegalArgumentException("Avatar response size");
        var root=JsonParser.parseString(json).getAsJsonObject();if(root.get("version").getAsInt()!=1)throw new IllegalArgumentException("Avatar version");
        var array=root.getAsJsonArray("avatars");if(array.size()>32)throw new IllegalArgumentException("Avatar count");
        var result=new ArrayList<Face>();var seen=new HashSet<String>();
        for(var raw:array){
            var item=raw.getAsJsonObject();String name=item.get("name").getAsString();
            if(!name(name)||!name.equals(name.toLowerCase(Locale.ROOT))||!requested.containsKey(name)||!seen.add(name))throw new IllegalArgumentException("Avatar identity");
            String status=item.get("status").getAsString();
            if(status.equals("missing")||status.equals("retry")){result.add(new Face(name,status,"",null,false));continue;}
            if(!status.equals("ok"))throw new IllegalArgumentException("Avatar status");
            String hash=item.get("hash").getAsString();if(!hash(hash))throw new IllegalArgumentException("Avatar hash");
            boolean unchanged=item.has("unchanged")&&item.get("unchanged").getAsBoolean();byte[] png=null;
            if(unchanged){if(!hash.equals(requested.get(name))||item.has("png"))throw new IllegalArgumentException("Uncached unchanged avatar");}
            else{
                String encoded=item.get("png").getAsString();if(encoded.length()>45000)throw new IllegalArgumentException("Avatar encoded size");
                png=Base64.getDecoder().decode(encoded);validateImage(png,hash);
            }
            result.add(new Face(name,status,hash,png,unchanged));
        }
        return List.copyOf(result);
    }
}
