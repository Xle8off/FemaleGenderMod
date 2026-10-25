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
    private static final org.slf4j.Logger CAMERA_LOG=org.slf4j.LoggerFactory.getLogger("SpringPlus/Camera");
    private static long lastDiagnostic;
    private static String safeDiagnostic(String value){return value!=null&&value.matches("[A-Z0-9_]{1,48}")?value:"OTHER_ERROR";}
    private static void trace(String stage,UUID owner,String code,long frames){CAMERA_LOG.info("[SpringPlus Camera] stage={} owner={} code={} frames={}",safeDiagnostic(stage),owner==null?"self":owner,safeDiagnostic(code),frames);}
    private static final class SignalingFailure extends IllegalStateException {
        SignalingFailure(JsonObject response){super(safe(response));}
        private static String safe(JsonObject response){
            try{return com.wildfire.gui.CameraSignalDiagnostics.format(
                response.has("sfuStage")?response.get("sfuStage").getAsString():"",
                response.has("sfuCode")?response.get("sfuCode").getAsString():"",
                response.has("sfuHttpStatus")?response.get("sfuHttpStatus").getAsInt():0);
            }catch(RuntimeException ignored){return com.wildfire.gui.CameraSignalDiagnostics.format("","INTERNAL_ERROR",0);}
        }
    }
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
    private static final class Incoming {final UUID camera;NativeCameraPeer peer;boolean connectionReported,noFramesReported,requestPending;long retryAfter,started=System.currentTimeMillis(),lastFrame,frames;Incoming(UUID camera){this.camera=camera;}}
    public record Viewer(String name,String status,String code,long frames,long ageMillis){}
    private static List<Viewer> viewers=List.of();
    private static boolean viewerDiagnostics;
    private static long viewerUpdated;
    private static int subscriberCount,viewerTotal;
    public static List<Viewer> viewers(){return viewers;}
    public static boolean viewerDiagnostics(){return viewerDiagnostics;}
    public static long viewerUpdated(){return viewerUpdated;}
    public static int subscriberCount(){return subscriberCount;}
    public static int viewerTotal(){return viewerTotal;}
    public static boolean publicationConnected(){return publisher!=null&&publisher.connected();}
    private static NativeCameraPreview capture;private static NativeCameraPeer publisher;
    private static UUID ownCamera;private static long lastSend,lastState,lastReply,publishAt,epoch,publishGeneration;
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
            if(response.has("type")&&response.get("type").getAsString().equals("demand")){
                if(capture!=null&&ownCamera!=null&&ownCamera.toString().equals(response.get("camera").getAsString())){
                    if(response.has("restart")&&response.get("restart").getAsBoolean()&&!publishing)beginPublication(capture,ownCamera);
                    else if(publisher!=null)publisher.sendFrames(response.has("active")&&response.get("active").getAsBoolean());
                }
                return;
            }
            if(response.has("type")&&response.get("type").getAsString().equals("ended")){
                UUID owner=UUID.fromString(response.get("owner").getAsString()),camera=UUID.fromString(response.get("camera").getAsString());
                if(camera.equals(cameras.get(owner))){cameras.remove(owner);OverheadMedia.INSTANCE.endCamera(owner,camera);}
                if(incoming.containsKey(owner)&&incoming.get(owner).camera.equals(camera))unwatch(owner);
                if(camera.equals(ownCamera))fail("Сессия камеры завершена");
                return;
            }
            String id=response.get("request").getAsString();var req=pending.remove(id);if(req==null)return;
            if(response.has("ok")&&!response.get("ok").getAsBoolean())CAMERA_LOG.warn("[SpringPlus Camera] coordinator_error stage={} code={} http={}",safeDiagnostic(response.has("sfuStage")?response.get("sfuStage").getAsString():"COORDINATOR"),safeDiagnostic(response.has("sfuCode")?response.get("sfuCode").getAsString():response.has("error")?response.get("error").getAsString():"OTHER_ERROR"),response.has("sfuHttpStatus")?Math.clamp(response.get("sfuHttpStatus").getAsInt(),0,599):0);
            if(!response.has("ok")||!response.get("ok").getAsBoolean()){req.future.completeExceptionally(response.has("sfuStage")?new SignalingFailure(response):new IllegalStateException("Координатор: "+(response.has("error")?response.get("error").getAsString():"ошибка")));return;}
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
        beginPublication(preview,null);
    }
    private static void beginPublication(NativeCameraPreview preview,UUID replacing){
        long attempt=++publishGeneration;
        var previous=publisher;publisher=null;if(previous!=null)previous.close();
        trace("PUBLISH_START",null,"OK",0);
        capture=preview;publishing=true;lastFailure="";publishAt=System.currentTimeMillis();status="Подключение к Cloudflare…";long token=epoch;
        NativeCameraPeer.create().thenCompose(peer->{
            main(()->{if(capture!=preview||epoch!=token||publishGeneration!=attempt)peer.close();else publisher=peer;});
            return peer.offer(preview);
        }).whenComplete((offer,error)->main(()->{
            if(capture!=preview||epoch!=token||publishGeneration!=attempt)return;
            if(error!=null){fail("Не удалось подготовить видео",error);return;}
            String mid=offer.sdp.lines().filter(line->line.startsWith("a=mid:")).map(line->line.substring(6).trim()).findFirst().orElse("");
            var publication=new HashMap<String,Object>();publication.put("action",replacing==null?"publish":"republish");publication.put("mid",mid);publication.put("sessionDescription",description(offer));if(replacing!=null)publication.put("camera",replacing.toString());
            request(publication).whenComplete((response,failure)->{
                if(failure!=null){if(capture==preview&&publishGeneration==attempt)fail("Не удалось опубликовать камеру",failure);return;}
                UUID nonce=UUID.fromString(response.get("camera").getAsString());
                if(capture!=preview||epoch!=token||publishGeneration!=attempt){if(replacing==null)request(Map.of("action","stop","camera",nonce.toString()));return;}
                ownCamera=nonce;trace("PUBLISH_ACCEPTED",null,"OK",0);
                publisher.applyAnswer(description(response,RTCSdpType.ANSWER)).whenComplete((ignored,negotiation)->main(()->{
                    if(capture!=preview||epoch!=token||publishGeneration!=attempt)return;
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
            if(error instanceof SignalingFailure)code=" · "+detail;
            else if(detail.matches("Координатор: [A-Z_]{1,40}")||detail.matches("CAMERA_[A-Z_]{1,40}"))code=" · "+detail;
            else code=" · "+error.getClass().getSimpleName();
        }
        trace("PUBLISH_ERROR",null,error==null?"STATE_ERROR":error.getClass().getSimpleName().toUpperCase(Locale.ROOT),0);
        stop();lastFailure=message+code;status=lastFailure;
    }
    public static void stop(){
        publishGeneration++;
        viewers=List.of();subscriberCount=viewerTotal=0;viewerUpdated=0;
        var previous=capture;capture=null;publishing=false;
        if(ownCamera!=null)request(Map.of("action","stop","camera",ownCamera.toString()));
        if(publisher!=null){publisher.close();publisher=null;}
        if(previous!=null)previous.close();
        var client=Minecraft.getInstance();if(client.player!=null&&ownCamera!=null)OverheadMedia.INSTANCE.endCamera(client.player.getUUID(),ownCamera);
        ownCamera=null;status="Камера выключена";
        if(CosmeticPreferences.overheadMode()==OverheadMedia.Mode.CAMERA)CosmeticPreferences.setOverheadMode(OverheadMedia.Mode.MESSAGES);
    }
    private static void unwatch(UUID owner){
        unwatch(owner,true);
    }
    private static void unwatch(UUID owner,boolean notify){
        var value=incoming.remove(owner);if(value==null)return;
        if(value.peer!=null)value.peer.close();if(notify)request(Map.of("action","unsubscribe","owner",owner.toString(),"camera",value.camera.toString()));
    }
    private static void reportError(UUID owner,Incoming receiving,String code){
        if(viewerDiagnostics)request(Map.of("action","viewer-report","owner",owner.toString(),"camera",receiving.camera.toString(),"status","ERROR","code",code,"frames",receiving.frames)).exceptionally(error->null);
    }
    private static void failedWatch(UUID owner,Incoming receiving,String code){
        if(incoming.get(owner)!=receiving)return;
        trace("SUBSCRIBE_ERROR",owner,code,receiving.frames);
        unwatch(owner);retryAt.put(owner,System.currentTimeMillis()+10000);reportError(owner,receiving,code);
    }
    private static List<Map<String,Object>> reports(long now){
        var result=new ArrayList<Map<String,Object>>();
        for(var entry:incoming.entrySet()){
            var value=entry.getValue();boolean connected=value.peer!=null&&value.peer.connected();
            String state=value.lastFrame>0&&now-value.lastFrame<5000?"RECEIVING":connected?(now-value.started>10000?"NO_FRAMES":"CONNECTED"):"CONNECTING";
            result.add(Map.of("owner",entry.getKey().toString(),"camera",value.camera.toString(),"status",state,"frames",value.frames));
        }
        return result;
    }
    private static void watch(UUID owner,UUID camera){
        var receiving=new Incoming(camera);incoming.put(owner,receiving);trace("SUBSCRIBE_START",owner,"OK",0);pull(owner,receiving,epoch);
    }
    private static void pull(UUID owner,Incoming receiving,long token){
        UUID camera=receiving.camera;receiving.requestPending=true;
        request(Map.of("action","subscribe","owner",owner.toString(),"camera",camera.toString())).whenComplete((response,error)->{
            receiving.requestPending=false;
            if(error!=null){failedWatch(owner,receiving,"SUBSCRIBE_FAILED");return;}
            if(incoming.get(owner)!=receiving||token!=epoch){request(Map.of("action","unsubscribe","owner",owner.toString(),"camera",camera.toString()));return;}
            if(response.has("preparing")&&response.get("preparing").getAsBoolean()){receiving.retryAfter=System.currentTimeMillis()+Math.clamp(response.has("retryAfterMillis")?response.get("retryAfterMillis").getAsInt():1500,500,3000);return;}
            trace("SUBSCRIBE_OFFER",owner,"OK",0);
            receiving.requestPending=true;
            NativeCameraPeer.create().whenComplete((peer,failure)->main(()->{
                if(incoming.get(owner)!=receiving||token!=epoch){if(peer!=null)peer.close();return;}
                if(failure!=null){failedWatch(owner,receiving,"PEER_INIT_FAILED");return;}
                receiving.peer=peer;receiving.requestPending=false;receiving.started=System.currentTimeMillis();
                peer.answer(description(response,RTCSdpType.OFFER)).whenComplete((answer,answerError)->main(()->{
                    if(incoming.get(owner)!=receiving||token!=epoch)return;
                    if(answerError!=null){failedWatch(owner,receiving,"ANSWER_FAILED");return;}
                    request(Map.of("action","answer","owner",owner.toString(),"camera",camera.toString(),"sessionDescription",description(answer))).whenComplete((accepted,denied)->{
                        if(denied!=null)failedWatch(owner,receiving,"ANSWER_REJECTED");else trace("ANSWER_ACCEPTED",owner,"OK",0);
                    });
                }));
            }));
        });
    }
    private static void state(JsonObject data){
        viewerDiagnostics=data.has("viewerDiagnostics")&&data.get("viewerDiagnostics").getAsBoolean();
        subscriberCount=data.has("viewerCount")?Math.clamp(data.get("viewerCount").getAsInt(),0,128):0;
        var rows=new ArrayList<Viewer>();
        if(viewerDiagnostics&&data.has("viewers"))for(var item:data.getAsJsonArray("viewers")){
            if(rows.size()>=64)break;
            var row=item.getAsJsonObject();String name=row.get("name").getAsString();
            if(!name.matches("[A-Za-z0-9_-]{1,36}"))continue;
            rows.add(new Viewer(name,row.get("status").getAsString(),row.get("code").getAsString(),Math.max(0,row.get("frames").getAsLong()),row.get("ageMillis").getAsLong()));
        }
        viewers=List.copyOf(rows);viewerTotal=data.has("viewerTotal")?Math.clamp(data.get("viewerTotal").getAsInt(),0,128):rows.size();viewerUpdated=System.currentTimeMillis();
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
        if(publisher!=null)publisher.sendFrames(data.has("demandCount")?data.get("demandCount").getAsInt()>0:data.has("viewerCount")&&data.get("viewerCount").getAsInt()>0);
        if(data.has("watching"))for(var entry:List.copyOf(incoming.entrySet()))if(entry.getValue().peer!=null&&(!data.getAsJsonObject("watching").has(entry.getKey().toString())||!entry.getValue().camera.toString().equals(data.getAsJsonObject("watching").get(entry.getKey().toString()).getAsString())))unwatch(entry.getKey(),false);
    }
    public static void tick(){
        var client=Minecraft.getInstance();long now=System.currentTimeMillis();
        if(!SpringPlusSession.enabled()||client.level==null){if(world!=null)reset();return;}
        if(world!=client.level){reset();world=client.level;}
        var desired=OverheadCameraTextures.subscriptions();
        for(var req:List.copyOf(pending.values()))if(now-req.sent>12000){pending.remove(req.id);req.future.completeExceptionally(new TimeoutException());}
        if(now-lastSend>=250&&!queue.isEmpty()&&supported.getAsBoolean()){
            var req=queue.removeFirst();lastSend=now;pending.put(req.id,new Request(req.id,req.json,req.future,now));sender.accept(req.json);
        }
        boolean diagnosticsOpen=client.gui.screen() instanceof com.wildfire.gui.CameraViewersScreen;
        boolean used=capture!=null||client.gui.screen() instanceof com.wildfire.gui.CameraScreen||diagnosticsOpen||CosmeticPreferences.overheadCameras();
        if(used&&now-lastState>=(diagnosticsOpen?2000:5000)){lastState=now;var demandRows=desired.entrySet().stream().map(e->Map.of("owner",e.getKey().toString(),"camera",e.getValue().toString())).toList();request(viewerDiagnostics?Map.of("action","state","views",reports(now),"demands",demandRows):Map.of("action","state","demands",demandRows)).thenAccept(CameraClient::state).exceptionally(error->null);}
        if(capture!=null){
            if(now-lastReply>20000){fail("Потеряна связь с координатором камер");return;}
            if(publisher!=null&&publisher.connected()){if(publishing)trace("PUBLISH_CONNECTED",null,"OK",0);publishing=false;status="Камера включена · 480p / 30 FPS максимум";}
            if(publisher!=null&&!publisher.failure().isEmpty())fail(publisher.failure());
            else if(publishing&&now-publishAt>30000)fail("Истекло время подключения камеры");
            if(capture!=null&&ownCamera!=null&&client.player!=null&&CameraVisibility.previewAllowed(true,client.options.getCameraType().isFirstPerson())){var frame=capture.poll();if(frame!=null){OverheadMedia.INSTANCE.cameraStatus(client.player.getUUID(),ownCamera,OverheadMedia.CameraStatus.LIVE,System.nanoTime());OverheadCameraTextures.acceptFrame(client.player.getUUID(),ownCamera,frame.time(),frame.width(),frame.height(),frame.rgba());}}
        }
        if(now-lastDiagnostic>=15000&&(capture!=null||!cameras.isEmpty()||!incoming.isEmpty())){lastDiagnostic=now;CAMERA_LOG.info("[SpringPlus Camera] state directory={} wanted={} incoming={} remoteEnabled={} publishing={} connected={}",cameras.size(),desired.size(),incoming.size(),CosmeticPreferences.overheadCameras(),capture!=null,publisher!=null&&publisher.connected());}
        for(UUID owner:List.copyOf(incoming.keySet()))if(!incoming.get(owner).camera.equals(desired.get(owner))||!incoming.get(owner).camera.equals(cameras.get(owner)))unwatch(owner);
        if(CosmeticPreferences.overheadCameras())for(var entry:desired.entrySet()){
            if(client.player!=null&&entry.getKey().equals(client.player.getUUID()))continue;
            if(entry.getValue().equals(cameras.get(entry.getKey()))&&!incoming.containsKey(entry.getKey())&&incoming.size()<8&&now>=retryAt.getOrDefault(entry.getKey(),0L))watch(entry.getKey(),entry.getValue());
        }
        for(var entry:List.copyOf(incoming.entrySet())){
            var incomingPeer=entry.getValue();if(incomingPeer.peer==null){if(now-incomingPeer.started>30000)failedWatch(entry.getKey(),incomingPeer,"WARMUP_TIMEOUT");else if(!incomingPeer.requestPending&&now>=incomingPeer.retryAfter)pull(entry.getKey(),incomingPeer,epoch);continue;}
            if(!incomingPeer.peer.failure().isEmpty()||now-incomingPeer.started>20000&&!incomingPeer.peer.connected()){failedWatch(entry.getKey(),incomingPeer,incomingPeer.peer.failure().isEmpty()?"CONNECT_TIMEOUT":"ICE_FAILED");continue;}
            if(incomingPeer.peer.connected()&&!incomingPeer.connectionReported){incomingPeer.connectionReported=true;trace("SUBSCRIBE_CONNECTED",entry.getKey(),"OK",incomingPeer.frames);}
            if(incomingPeer.peer.connected()&&incomingPeer.frames==0&&now-incomingPeer.started>10000&&!incomingPeer.noFramesReported){incomingPeer.noFramesReported=true;trace("NO_FRAMES",entry.getKey(),"DECODER_EMPTY",0);}
            var frame=incomingPeer.peer.poll();if(frame!=null){incomingPeer.lastFrame=now;incomingPeer.frames++;OverheadMedia.INSTANCE.cameraStatus(entry.getKey(),incomingPeer.camera,OverheadMedia.CameraStatus.LIVE,System.nanoTime());boolean accepted=OverheadCameraTextures.acceptFrame(entry.getKey(),incomingPeer.camera,frame.time(),frame.width(),frame.height(),frame.rgba());if(incomingPeer.frames==1)trace("FIRST_FRAME",entry.getKey(),accepted?"ACCEPTED":"DROPPED",1);}
        }
    }
    public static void reset(){
        viewerDiagnostics=false;
        epoch++;stop();for(UUID owner:List.copyOf(incoming.keySet()))unwatch(owner);
        cameras.clear();retryAt.clear();
        var requests=new ArrayList<>(pending.values());requests.addAll(queue);pending.clear();queue.clear();requests.forEach(req->req.future.completeExceptionally(new CancellationException()));
        world=null;lastSend=lastState=lastReply=0;
    }
}
