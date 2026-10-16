package com.wildfire.client.overhead;

import com.google.gson.*;
import com.wildfire.client.config.CosmeticPreferences;
import com.wildfire.client.music.SpringPlusSession;
import dev.onvoid.webrtc.*;
import net.minecraft.client.Minecraft;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Main-thread coordinator client. No secrets, browser, microphone or media in game packets. */
public final class CameraClient {
    private static String lastFailure="";
    public static String lastFailure(){return lastFailure;}
    private static java.util.function.Consumer<String> sender=value->{};
    private static java.util.function.BooleanSupplier supported=()->false;
    public static void configure(java.util.function.Consumer<String> send,java.util.function.BooleanSupplier available){sender=Objects.requireNonNull(send);supported=Objects.requireNonNull(available);}
    private record Request(String id,String json,CompletableFuture<JsonObject> future,long sent){}
    private static final ArrayDeque<Request> queue=new ArrayDeque<>();
    private static final Map<String,Request> pending=new HashMap<>();
    private static final Map<UUID,UUID> cameras=new HashMap<>();
    private static final Map<UUID,Incoming> incoming=new HashMap<>();
    private static final Map<UUID,Long> retryAt=new HashMap<>();
    private static final class Incoming {final UUID camera;NativeCameraPeer peer;long started=System.currentTimeMillis();Incoming(UUID camera){this.camera=camera;}}
    private static NativeCameraPreview capture;private static NativeCameraPeer publisher;
    private static UUID ownCamera;private static long lastSend,lastState,lastReply,publishAt,epoch;
    private static boolean publishing;
    private static String status="Камера выключена";
    private static Object world;
    private CameraClient(){}
    public static NativeCameraPreview capture(){return capture;}
    public static boolean pending(){return publishing;}
    public static String status(){return status;}
    public static boolean available(){return SpringPlusSession.enabled()&&System.currentTimeMillis()-lastReply<15000&&supported.getAsBoolean();}
    private static CompletableFuture<JsonObject> request(Map<String,Object> fields){
        var future=new CompletableFuture<JsonObject>();
        if(!SpringPlusSession.enabled()||!supported.getAsBoolean()||pending.size()+queue.size()>=32){future.completeExceptionally(new IllegalStateException("Координатор недоступен"));return future;}
        String id=UUID.randomUUID().toString();var data=new LinkedHashMap<>(fields);data.put("request",id);String json=new Gson().toJson(data);
        if(json.getBytes(StandardCharsets.UTF_8).length>30000){future.completeExceptionally(new IllegalStateException("Слишком большой SDP"));return future;}
        var req=new Request(id,json,future,0);String action=Objects.toString(fields.get("action"),"");
        if(action.equals("stop")||action.equals("unsubscribe"))queue.addFirst(req);else queue.addLast(req);
        return future;
    }
    public static void accept(String json){
        try{
            var response=JsonParser.parseString(json).getAsJsonObject();
            if(response.has("type")&&response.get("type").getAsString().equals("ended")){
                UUID owner=UUID.fromString(response.get("owner").getAsString()),camera=UUID.fromString(response.get("camera").getAsString());
                if(camera.equals(cameras.get(owner))){cameras.remove(owner);OverheadMedia.INSTANCE.endCamera(owner,camera);}
                if(incoming.containsKey(owner)&&incoming.get(owner).camera.equals(camera))unwatch(owner);
                if(camera.equals(ownCamera))fail("Сессия камеры завершена");
                return;
            }
            String id=response.get("request").getAsString();var req=pending.remove(id);if(req==null)return;
            if(!response.has("ok")||!response.get("ok").getAsBoolean()){req.future.completeExceptionally(new IllegalStateException("Координатор: "+(response.has("error")?response.get("error").getAsString():"ошибка")));return;}
            lastReply=System.currentTimeMillis();req.future.complete(response);
        }catch(RuntimeException malformed){/* Never log SDP/IP addresses. */}
    }
    private static Map<String,Object> description(RTCSessionDescription d){return Map.of("type",d.sdpType==RTCSdpType.OFFER?"offer":"answer","sdp",d.sdp);}
    private static RTCSessionDescription description(JsonObject response,RTCSdpType type){
        var d=response.getAsJsonObject("sessionDescription");if(!d.get("type").getAsString().equals(type==RTCSdpType.OFFER?"offer":"answer"))throw new IllegalStateException("Неверный SDP");
        return new RTCSessionDescription(type,d.get("sdp").getAsString());
    }
    private static void main(Runnable action){Minecraft.getInstance().execute(action);}
    public static void publish(NativeCameraPreview preview){
        if(capture!=null||!preview.ready()||!available())return;
        capture=preview;publishing=true;lastFailure="";publishAt=System.currentTimeMillis();status="Подключение к Cloudflare…";long token=epoch;
        NativeCameraPeer.create().thenCompose(peer->{
            main(()->{if(capture!=preview||epoch!=token)peer.close();else publisher=peer;});
            return peer.offer(preview);
        }).whenComplete((offer,error)->main(()->{
            if(capture!=preview||epoch!=token)return;
            if(error!=null){fail("Не удалось подготовить видео",error);return;}
            String mid=offer.sdp.lines().filter(line->line.startsWith("a=mid:")).map(line->line.substring(6).trim()).findFirst().orElse("");
            request(Map.of("action","publish","mid",mid,"sessionDescription",description(offer))).whenComplete((response,failure)->{
                if(failure!=null){if(capture==preview)fail("Не удалось опубликовать камеру",failure);return;}
                UUID nonce=UUID.fromString(response.get("camera").getAsString());
                if(capture!=preview||epoch!=token){request(Map.of("action","stop","camera",nonce.toString()));return;}
                ownCamera=nonce;
                publisher.applyAnswer(description(response,RTCSdpType.ANSWER)).whenComplete((ignored,negotiation)->main(()->{
                    if(capture!=preview||epoch!=token)return;
                    if(negotiation!=null)fail("Не удалось согласовать видеопоток",negotiation);
                    else {
                        CosmeticPreferences.setOverheadMode(OverheadMedia.Mode.CAMERA);
                        var player=Minecraft.getInstance().player;if(player!=null)OverheadMedia.INSTANCE.beginCamera(player.getUUID(),nonce,System.nanoTime());
                        status="Устанавливаем видеосоединение…";
                    }
                }));
            });
        }));
    }
    private static void fail(String message){fail(message,null);}
    private static void fail(String message,Throwable error){
        String code="";
        while(error instanceof CompletionException||error instanceof ExecutionException)error=error.getCause();
        if(error!=null){
            // Only controlled protocol codes/classes: never log native messages, SDP or ICE.
            String detail=Objects.toString(error.getMessage(),"");
            if(detail.matches("Координатор: [A-Z_]{1,40}")||detail.matches("CAMERA_[A-Z_]{1,40}"))code=" · "+detail;
            else code=" · "+error.getClass().getSimpleName();
        }
        stop();lastFailure=message+code;status=lastFailure;
    }
    public static void stop(){
        var previous=capture;capture=null;publishing=false;
        if(ownCamera!=null)request(Map.of("action","stop","camera",ownCamera.toString()));
        if(publisher!=null){publisher.close();publisher=null;}
        if(previous!=null)previous.close();
        var client=Minecraft.getInstance();if(client.player!=null&&ownCamera!=null)OverheadMedia.INSTANCE.endCamera(client.player.getUUID(),ownCamera);
        ownCamera=null;status="Камера выключена";
        if(CosmeticPreferences.overheadMode()==OverheadMedia.Mode.CAMERA)CosmeticPreferences.setOverheadMode(OverheadMedia.Mode.MESSAGES);
    }
    private static void unwatch(UUID owner){
        var value=incoming.remove(owner);if(value==null)return;
        if(value.peer!=null)value.peer.close();request(Map.of("action","unsubscribe","owner",owner.toString(),"camera",value.camera.toString()));
    }
    private static void watch(UUID owner,UUID camera){
        var receiving=new Incoming(camera);incoming.put(owner,receiving);long token=epoch;
        request(Map.of("action","subscribe","owner",owner.toString(),"camera",camera.toString())).whenComplete((response,error)->{
            if(error!=null){if(incoming.get(owner)==receiving){incoming.remove(owner);retryAt.put(owner,System.currentTimeMillis()+10000);}return;}
            if(incoming.get(owner)!=receiving||token!=epoch){request(Map.of("action","unsubscribe","owner",owner.toString(),"camera",camera.toString()));return;}
            NativeCameraPeer.create().whenComplete((peer,failure)->main(()->{
                if(incoming.get(owner)!=receiving||token!=epoch){if(peer!=null)peer.close();return;}
                if(failure!=null){unwatch(owner);retryAt.put(owner,System.currentTimeMillis()+10000);return;}
                receiving.peer=peer;
                peer.answer(description(response,RTCSdpType.OFFER)).whenComplete((answer,answerError)->main(()->{
                    if(incoming.get(owner)!=receiving||token!=epoch)return;
                    if(answerError!=null){unwatch(owner);retryAt.put(owner,System.currentTimeMillis()+10000);return;}
                    request(Map.of("action","answer","owner",owner.toString(),"camera",camera.toString(),"sessionDescription",description(answer))).whenComplete((accepted,denied)->{
                        if(denied!=null&&incoming.get(owner)==receiving){unwatch(owner);retryAt.put(owner,System.currentTimeMillis()+10000);}
                    });
                }));
            }));
        });
    }
    private static void state(JsonObject data){
        var next=new HashMap<UUID,UUID>();
        for(var value:data.getAsJsonArray("cameras")){var c=value.getAsJsonObject();next.put(UUID.fromString(c.get("owner").getAsString()),UUID.fromString(c.get("camera").getAsString()));}
        long now=System.nanoTime();
        for(var entry:cameras.entrySet())if(!entry.getValue().equals(next.get(entry.getKey())))OverheadMedia.INSTANCE.endCamera(entry.getKey(),entry.getValue());
        cameras.clear();cameras.putAll(next);
        var localPlayer=Minecraft.getInstance().player;
        if(ownCamera!=null&&localPlayer!=null&&!ownCamera.equals(next.get(localPlayer.getUUID())))fail("Координатор завершил сессию камеры");
        for(var entry:cameras.entrySet()){
            var view=OverheadMedia.INSTANCE.view(entry.getKey(),now);
            if(view==null||!entry.getValue().equals(view.session()))OverheadMedia.INSTANCE.beginCamera(entry.getKey(),entry.getValue(),now);
            else OverheadMedia.INSTANCE.cameraStatus(entry.getKey(),entry.getValue(),view.camera(),now);
        }
        if(publisher!=null)publisher.sendFrames(data.has("viewerCount")&&data.get("viewerCount").getAsInt()>0);
    }
    public static void tick(){
        var client=Minecraft.getInstance();long now=System.currentTimeMillis();
        if(!SpringPlusSession.enabled()||client.level==null){if(world!=null)reset();return;}
        if(world!=client.level){reset();world=client.level;}
        for(var req:List.copyOf(pending.values()))if(now-req.sent>12000){pending.remove(req.id);req.future.completeExceptionally(new TimeoutException());}
        if(now-lastSend>=250&&!queue.isEmpty()&&supported.getAsBoolean()){
            var req=queue.removeFirst();lastSend=now;pending.put(req.id,new Request(req.id,req.json,req.future,now));sender.accept(req.json);
        }
        boolean used=capture!=null||client.gui.screen() instanceof com.wildfire.gui.CameraScreen||CosmeticPreferences.overheadCameras();
        if(used&&now-lastState>=5000){lastState=now;request(Map.of("action","state")).thenAccept(CameraClient::state).exceptionally(error->null);}
        if(capture!=null){
            if(now-lastReply>20000){fail("Потеряна связь с координатором камер");return;}
            if(publisher!=null&&publisher.connected()){publishing=false;status="Камера включена · 480p / 30 FPS максимум";}
            if(publisher!=null&&!publisher.failure().isEmpty())fail(publisher.failure());
            else if(publishing&&now-publishAt>30000)fail("Истекло время подключения камеры");
            if(capture!=null&&ownCamera!=null&&client.player!=null&&CameraVisibility.previewAllowed(true,client.options.getCameraType().isFirstPerson())){var frame=capture.poll();if(frame!=null){OverheadMedia.INSTANCE.cameraStatus(client.player.getUUID(),ownCamera,OverheadMedia.CameraStatus.LIVE,System.nanoTime());OverheadCameraTextures.acceptFrame(client.player.getUUID(),ownCamera,frame.time(),frame.width(),frame.height(),frame.rgba());}}
        }
        var desired=OverheadCameraTextures.subscriptions();
        for(UUID owner:List.copyOf(incoming.keySet()))if(!incoming.get(owner).camera.equals(desired.get(owner))||!incoming.get(owner).camera.equals(cameras.get(owner)))unwatch(owner);
        if(CosmeticPreferences.overheadCameras())for(var entry:desired.entrySet()){
            if(client.player!=null&&entry.getKey().equals(client.player.getUUID()))continue;
            if(entry.getValue().equals(cameras.get(entry.getKey()))&&!incoming.containsKey(entry.getKey())&&incoming.size()<8&&now>=retryAt.getOrDefault(entry.getKey(),0L))watch(entry.getKey(),entry.getValue());
        }
        for(var entry:List.copyOf(incoming.entrySet())){
            var incomingPeer=entry.getValue();if(incomingPeer.peer==null)continue;
            if(!incomingPeer.peer.failure().isEmpty()||now-incomingPeer.started>20000&&!incomingPeer.peer.connected()){unwatch(entry.getKey());retryAt.put(entry.getKey(),now+10000);continue;}
            var frame=incomingPeer.peer.poll();if(frame!=null){OverheadMedia.INSTANCE.cameraStatus(entry.getKey(),incomingPeer.camera,OverheadMedia.CameraStatus.LIVE,System.nanoTime());OverheadCameraTextures.acceptFrame(entry.getKey(),incomingPeer.camera,frame.time(),frame.width(),frame.height(),frame.rgba());}
        }
    }
    public static void reset(){
        epoch++;stop();for(UUID owner:List.copyOf(incoming.keySet()))unwatch(owner);
        cameras.clear();retryAt.clear();
        var requests=new ArrayList<>(pending.values());requests.addAll(queue);pending.clear();queue.clear();requests.forEach(req->req.future.completeExceptionally(new CancellationException()));
        world=null;lastSend=lastState=lastReply=0;
    }
}
