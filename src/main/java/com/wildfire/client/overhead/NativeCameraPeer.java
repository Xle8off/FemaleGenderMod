package com.wildfire.client.overhead;

import dev.onvoid.webrtc.*;
import dev.onvoid.webrtc.media.video.*;
import dev.onvoid.webrtc.media.FourCC;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Native video-only peer. All lifecycle/SDP calls serialized away from the render thread. */
final class NativeCameraPeer implements AutoCloseable {
    static final ScheduledExecutorService WORKER=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"SpringPlus-camera-WebRTC");t.setDaemon(true);return t;});
    private PeerConnectionFactory factory;private RTCPeerConnection peer;
    private CustomVideoSource source;private VideoTrack local,remote;private RTCRtpSender sender;
    private final Object sourceLock=new Object();
    private final AtomicReference<NativeCameraPreview.Frame> frame=new AtomicReference<>();
    private final CompletableFuture<Void> gathered=new CompletableFuture<>();
    private volatile boolean closed,connected,sendFrames;
    private volatile String failure="";
    private long received;
    private volatile NativeCameraPreview capture;
    private final VideoTrackSink sink=this::receive;
    static CompletableFuture<NativeCameraPeer> create(){
        return CompletableFuture.supplyAsync(()->{var result=new NativeCameraPeer();try{result.init();return result;}catch(Throwable error){result.closeOnWorker();throw new CompletionException("Не удалось запустить WebRTC",error);}},WORKER);
    }
    private void init(){
        factory=new PeerConnectionFactory();var config=new RTCConfiguration();var stun=new RTCIceServer();stun.urls.add("stun:stun.cloudflare.com:3478");config.iceServers.add(stun);
        peer=factory.createPeerConnection(config,new PeerConnectionObserver(){
            @Override public void onIceCandidate(RTCIceCandidate candidate){}
            @Override public void onIceGatheringChange(RTCIceGatheringState state){if(state==RTCIceGatheringState.COMPLETE)gathered.complete(null);}
            @Override public void onConnectionChange(RTCPeerConnectionState state){
                connected=state==RTCPeerConnectionState.CONNECTED;
                if(state==RTCPeerConnectionState.FAILED)failure="Не удалось соединиться с Cloudflare";
            }
            @Override public void onTrack(RTCRtpTransceiver transceiver){
                var receiver=transceiver.getReceiver();var track=receiver.getTrack();receiver.dispose();transceiver.dispose();
                if(track==null)return;
                WORKER.execute(()->{if(closed){track.dispose();return;}if(track instanceof VideoTrack video){if(remote!=null){remote.removeSink(sink);remote.dispose();}remote=video;remote.addSink(sink);}else track.dispose();});
            }
        });
    }
    boolean connected(){return connected&&!closed;}
    String failure(){return failure;}
    void sendFrames(boolean value){sendFrames=value;}
    NativeCameraPreview.Frame poll(){return frame.getAndSet(null);}
    CompletableFuture<RTCSessionDescription> offer(NativeCameraPreview preview){
        return offer().thenApplyAsync(description->{
            requireOpen();capture=preview;
            preview.outgoing(video->{synchronized(sourceLock){if(!closed&&source!=null&&sendFrames)source.pushFrame(video);}});
            return description;
        },WORKER);
    }
    /** Prepare a video-only source; no device is opened by peer negotiation. */
    CompletableFuture<RTCSessionDescription> offer(){
        return CompletableFuture.supplyAsync(()->{
            requireOpen();source=new CustomVideoSource();local=factory.createVideoTrack("camera",source);sender=peer.addTrack(local,List.of("camera"));
            return createDescription(true);
        },WORKER).thenCompose(f->f).thenComposeAsync(description->setDescription(description,true),WORKER).thenComposeAsync(v->localWithIce(),WORKER);
    }
    CompletableFuture<Void> applyAnswer(RTCSessionDescription answer){
        return CompletableFuture.supplyAsync(()->setDescription(answer,false),WORKER).thenCompose(f->f).thenRunAsync(()->{
            requireOpen();var params=sender.getParameters();if(params.encodings==null||params.encodings.isEmpty())throw new IllegalStateException("Нет параметров видео");
            for(var encoding:params.encodings){encoding.maxBitrate=800_000;encoding.maxFramerate=30.0;}
            sender.setParameters(params);
        },WORKER);
    }
    CompletableFuture<RTCSessionDescription> answer(RTCSessionDescription offer){
        return CompletableFuture.supplyAsync(()->setDescription(offer,false),WORKER).thenCompose(f->f)
            .thenComposeAsync(v->createDescription(false),WORKER).thenComposeAsync(d->setDescription(d,true),WORKER).thenComposeAsync(v->localWithIce(),WORKER);
    }
    private CompletableFuture<RTCSessionDescription> localWithIce(){
        requireOpen();if(peer.getIceGatheringState()==RTCIceGatheringState.COMPLETE)gathered.complete(null);
        // Start the budget AFTER setLocalDescription, not at peer construction.
        // A slow STUN interface must not discard already gathered candidates.
        var deadline=WORKER.schedule(()->{
            if(closed||gathered.isDone())return;
            var description=peer.getLocalDescription();
            long count=candidates(description);
            if(count>0)gathered.complete(null);
            else gathered.completeExceptionally(new IllegalStateException("CAMERA_ICE_NO_CANDIDATES"));
        },8,TimeUnit.SECONDS);
        return gathered.thenApplyAsync(v->{
            requireOpen();var description=peer.getLocalDescription();long count=candidates(description);
            if(count==0)throw new IllegalStateException("CAMERA_ICE_NO_CANDIDATES");
            return description;
        },WORKER).whenComplete((v,error)->deadline.cancel(false));
    }
    private static long candidates(RTCSessionDescription description){
        return description==null||description.sdp==null?0:description.sdp.lines().filter(s->s.startsWith("a=candidate:")).count();
    }
    private static <T> CompletableFuture<T> deadline(CompletableFuture<T> future,String code){
        var timeout=WORKER.schedule(()->future.completeExceptionally(new IllegalStateException(code)),10,TimeUnit.SECONDS);
        return future.whenComplete((v,error)->timeout.cancel(false));
    }
    private CompletableFuture<RTCSessionDescription> createDescription(boolean offer){
        requireOpen();var future=new CompletableFuture<RTCSessionDescription>();
        var observer=new CreateSessionDescriptionObserver(){public void onSuccess(RTCSessionDescription result){future.complete(result);}public void onFailure(String error){future.completeExceptionally(new IllegalStateException("CAMERA_SDP_CREATE_FAILED"));}};
        if(offer)peer.createOffer(new RTCOfferOptions(),observer);else peer.createAnswer(new RTCAnswerOptions(),observer);
        return deadline(future,"CAMERA_SDP_CREATE_TIMEOUT");
    }
    private CompletableFuture<Void> setDescription(RTCSessionDescription description,boolean local){
        requireOpen();var future=new CompletableFuture<Void>();var observer=new SetSessionDescriptionObserver(){public void onSuccess(){future.complete(null);}public void onFailure(String error){future.completeExceptionally(new IllegalStateException(local?"CAMERA_LOCAL_SDP_FAILED":"CAMERA_REMOTE_SDP_FAILED"));}};
        if(local)peer.setLocalDescription(description,observer);else peer.setRemoteDescription(description,observer);
        return deadline(future,local?"CAMERA_LOCAL_SDP_TIMEOUT":"CAMERA_REMOTE_SDP_TIMEOUT");
    }
    private void requireOpen(){if(closed||peer==null)throw new IllegalStateException("Camera peer closed");}
    private void receive(VideoFrame video){
        try{
            if(closed)return;long now=System.nanoTime();if(now-received<33_333_333L)return;received=now;
            int w=video.buffer.getWidth(),h=video.buffer.getHeight();
            // Reject oversized decoded frames, never allocate a 4K preview texture.
            if(w<16||h<16||w>CameraOptions.MAX_WIDTH||h>CameraOptions.MAX_HEIGHT)return;
            byte[] bytes=new byte[w*h*4];VideoBufferConverter.convertFromI420(video.buffer,bytes,FourCC.ABGR);
            if(!closed)frame.set(new NativeCameraPreview.Frame(w,h,video.rotation,bytes,now));
        }catch(Exception|LinkageError ignored){failure="Не удалось декодировать видео";}
        finally{video.release();}
    }
    @Override public void close(){if(closed)return;closed=true;connected=false;sendFrames=false;var owned=capture;if(owned!=null)owned.outgoing(null);frame.set(null);gathered.completeExceptionally(new CancellationException());WORKER.execute(this::closeOnWorker);}
    private void closeOnWorker(){
        closed=true;
        var owned=capture;capture=null;if(owned!=null)owned.outgoing(null);
        synchronized(sourceLock){
            if(remote!=null){remote.removeSink(sink);remote.dispose();remote=null;}
            if(peer!=null){peer.close();peer=null;}
            if(sender!=null){sender.dispose();sender=null;}if(local!=null){local.dispose();local=null;}
            if(source!=null){source.dispose();source=null;}if(factory!=null){factory.dispose();factory=null;}
        }
    }
}
