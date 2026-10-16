package com.wildfire.client.overhead;

import dev.onvoid.webrtc.media.MediaDevices;
import dev.onvoid.webrtc.media.FourCC;
import dev.onvoid.webrtc.media.video.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Explicit local-only capture. Native calls never run on the game/render thread. */
public final class NativeCameraPreview implements AutoCloseable {
    public record Device(String name,List<CameraOptions.Mode> modes){}
    public record Frame(int width,int height,int rotation,byte[] rgba,long time){}
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{var t=new Thread(r,"SpringPlus-camera-capture");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final AtomicLong generation=new AtomicLong();
    private final AtomicReference<Frame> latest=new AtomicReference<>();
    private volatile List<Device> devices=List.of();
    private List<VideoDevice> nativeDevices=List.of();
    private volatile String status="Поиск камер…";
    private volatile boolean closed,ready,scanned;
    private volatile int selectedDevice=-1;
    private volatile CameraOptions.Mode selectedMode;
    private VideoCapture capture;
    private long lastFrame;
    private final Object frameLock=new Object();
    private java.util.function.Consumer<VideoFrame> outgoing;
    public NativeCameraPreview(){
        execute(()->{
            try{
                var found=MediaDevices.getVideoCaptureDevices();var rows=new ArrayList<Device>();
                for(var camera:found){
                    var modes=new ArrayList<CameraOptions.Mode>();
                    for(var mode:MediaDevices.getVideoCaptureCapabilities(camera)){
                        // WebRTC capabilities report a maximum frame rate, not a mandatory rate.
                        if(mode.frameRate>0)modes.add(new CameraOptions.Mode(mode.width,mode.height,Math.min(30,mode.frameRate)));
                    }
                    rows.add(new Device(Objects.toString(camera.getName(),"Камера"),CameraOptions.allowed(modes)));
                }
                nativeDevices=List.copyOf(found);devices=List.copyOf(rows);status=rows.isEmpty()?"Камеры не найдены":"Выберите камеру для локального предпросмотра";
            }catch(LinkageError|RuntimeException unavailable){status="Не удалось загрузить камеру. Проверьте доступ и драйвер устройства";}
            finally{scanned=true;}
        });
    }
    public List<Device> devices(){return devices;}
    public String status(){return status;}
    public boolean scanned(){return scanned;}
    public boolean ready(){return ready&&!closed;}
    public int selectedDevice(){return selectedDevice;}
    public CameraOptions.Mode selectedMode(){return selectedMode;}
    public Frame poll(){return latest.get();}
    public void disable(String reason){
        generation.incrementAndGet();ready=false;latest.set(null);outgoing(null);status=reason;
        worker.getQueue().clear();
        execute(this::stopCapture);
    }
    public void select(int device,CameraOptions.Mode mode){
        if(closed||device<0||device>=devices.size()||!devices.get(device).modes.contains(mode)||!mode.allowed())return;
        selectedDevice=device;selectedMode=mode;
        long token=generation.incrementAndGet();ready=false;latest.set(null);status="Запускаем локальный предпросмотр…";
        execute(()->{
            stopCapture();if(closed||generation.get()!=token)return;
            try{
                var next=new VideoCapture();capture=next;lastFrame=0;
                next.setVideoCaptureDevice(nativeDevices.get(device));
                next.setVideoCaptureCapability(new VideoCaptureCapability(mode.width(),mode.height(),mode.fps()));
                next.setVideoSink(frame->frame(frame,token,mode.fps()));
                if(closed||generation.get()!=token){stopCapture();return;}
                next.start();
                if(closed||generation.get()!=token)stopCapture();
            }catch(LinkageError|RuntimeException unavailable){stopCapture();if(generation.get()==token)status="Не удалось открыть камеру. Возможно, она занята или доступ запрещён";}
        });
    }
    private void frame(VideoFrame frame,long token,int fps){
        VideoFrameBuffer scaled=null;
        try{
            synchronized(frameLock){
                long now=System.nanoTime();
                if(closed||generation.get()!=token||now-lastFrame<1_000_000_000L/fps)return;
                lastFrame=now;
                var size=CameraOptions.fit(frame.buffer.getWidth(),frame.buffer.getHeight());
                scaled=CameraBuffers.resize(frame.buffer,size[0],size[1]);
                byte[] rgba=new byte[size[0]*size[1]*4];
                // libyuv ABGR is RGBA byte order on supported little-endian desktop platforms.
                VideoBufferConverter.convertFromI420(scaled,rgba,FourCC.ABGR);
                if(closed||generation.get()!=token)return;
                latest.set(new Frame(size[0],size[1],frame.rotation,rgba,now));ready=true;status="Предпросмотр · только у вас";
                if(outgoing!=null)outgoing.accept(new VideoFrame(scaled,frame.rotation,frame.timestampNs));
            }
        }catch(Exception|LinkageError invalid){ready=false;status="Не удалось обработать кадр камеры";}
        finally{try{if(scaled!=null)scaled.release();}finally{frame.release();}}
    }
    /** Sink borrows frames only during the callback; must retain if needed asynchronously. */
    public void outgoing(java.util.function.Consumer<VideoFrame> sink){synchronized(frameLock){outgoing=sink;}}
    private void execute(Runnable task){try{worker.execute(task);}catch(RejectedExecutionException busy){status="Камера занята. Повторите выбор";}}
    private void stopCapture(){
        var previous=capture;capture=null;
        if(previous!=null){try{previous.stop();}catch(RuntimeException ignored){}finally{try{previous.dispose();}catch(RuntimeException ignored){}}}
    }
    @Override public void close(){
        if(closed)return;closed=true;ready=false;generation.incrementAndGet();latest.set(null);outgoing(null);
        worker.getQueue().clear();execute(this::stopCapture);worker.shutdown();
    }
}
