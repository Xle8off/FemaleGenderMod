package com.wildfire.client.overhead;

import java.util.*;

/** Transport-independent state. No camera device, socket, decoder or automatic capture. */
public final class OverheadMedia {
    public enum Mode { MESSAGES, CAMERA, OFF }
    public enum CameraStatus { WAITING, LIVE, PAUSED }
    public record Message(String id,String body,long expires) {}
    public record View(Mode mode,CameraStatus camera,Message message,UUID session) {}
    static record Frame(UUID session,long sequence,int width,int height,byte[] rgba,long received) {}
    private static final long CAMERA_LEASE=15_000_000_000L,FRAME_TTL=3_000_000_000L;
    public static final OverheadMedia INSTANCE=new OverheadMedia();
    private static final class Entry {
        Mode mode=Mode.MESSAGES;CameraStatus camera=CameraStatus.WAITING;
        UUID session;Message message;Frame frame;long lease,lastFrame,sequence=-1;
        final LinkedHashSet<UUID> retired=new LinkedHashSet<>();
    }
    private static void retire(Entry e){
        if(e.session!=null)e.retired.add(e.session);
        while(e.retired.size()>8)e.retired.remove(e.retired.iterator().next());
    }
    private final LinkedHashMap<UUID,Entry> entries=new LinkedHashMap<>(128,.75f,true);
    private Entry entry(UUID owner){
        Objects.requireNonNull(owner);Entry value=entries.computeIfAbsent(owner,id->new Entry());
        while(entries.size()>128)entries.remove(entries.keySet().iterator().next());return value;
    }
    public synchronized void mode(UUID owner,Mode mode,long now){
        Objects.requireNonNull(mode);Entry e=entry(owner);
        if(e.mode!=mode){retire(e);e.mode=mode;e.session=null;e.frame=null;e.sequence=-1;e.lastFrame=0;e.camera=CameraStatus.WAITING;}
        e.lease=mode==Mode.CAMERA?now+CAMERA_LEASE:0;
    }
    /** Only accepted/public chat content belongs here, not raw chat or private messages. */
    public synchronized boolean message(UUID owner,String id,String body,long durationNanos,long now){
        if(owner==null||id==null||!id.matches("[A-Za-z0-9_.:-]{1,64}")||body==null||body.isBlank()||body.length()>512
            ||body.codePoints().anyMatch(c->c==0xA7||(Character.isISOControl(c)&&c!='\n'))||durationNanos<=0||durationNanos>10_000_000_000L)return false;
        Entry e=entry(owner);if(e.message!=null&&e.message.id.equals(id))return false;
        e.message=new Message(id,body,now+durationNanos);return true;
    }
    public synchronized boolean beginCamera(UUID owner,UUID session,long now){
        Objects.requireNonNull(session);Entry e=entry(owner);
        if(e.retired.contains(session))return false;
        if(e.mode==Mode.CAMERA&&session.equals(e.session))return true;
        mode(owner,Mode.CAMERA,now);retire(e);e.frame=null;e.sequence=-1;e.lastFrame=0;
        e.session=session;e.camera=CameraStatus.WAITING;return true;
    }
    public synchronized boolean cameraStatus(UUID owner,UUID session,CameraStatus status,long now){
        Entry e=entries.get(owner);if(e==null||e.mode!=Mode.CAMERA||!Objects.equals(e.session,session)||session==null||status==null||now>e.lease)return false;
        e.camera=status;e.lease=now+CAMERA_LEASE;if(status!=CameraStatus.LIVE)e.frame=null;return true;
    }
    /** RGBA8, at most 480p / 30fps. Latest frame replaces older pending data. */
    public synchronized boolean frame(UUID owner,UUID session,long sequence,int width,int height,byte[] rgba,long now){
        Entry e=entries.get(owner);
        if(e==null||e.mode!=Mode.CAMERA||e.camera!=CameraStatus.LIVE||session==null||!session.equals(e.session)||now>e.lease
            ||sequence<0||sequence<=e.sequence||width<16||height<16||width>CameraOptions.MAX_WIDTH||height>CameraOptions.MAX_HEIGHT||rgba==null||rgba.length!=width*height*4
            ||(e.sequence>=0&&now-e.lastFrame<33_333_333L))return false;
        e.frame=new Frame(session,sequence,width,height,rgba.clone(),now);e.sequence=sequence;e.lastFrame=now;e.lease=now+CAMERA_LEASE;
        int pending=0;for(var item:entries.values())if(item.frame!=null)pending++;
        if(pending>8)for(var item:entries.values())if(item!=e&&item.frame!=null){item.frame=null;break;}
        return true;
    }
    public synchronized View view(UUID owner,long now){
        Entry e=entries.get(owner);if(e==null)return null;
        if(e.mode==Mode.CAMERA&&now>e.lease){retire(e);e.mode=Mode.MESSAGES;e.camera=CameraStatus.WAITING;e.session=null;e.frame=null;}
        if(e.message!=null&&now>=e.message.expires)e.message=null;
        CameraStatus camera=e.camera;
        if(camera==CameraStatus.LIVE&&(e.lastFrame==0||now-e.lastFrame>FRAME_TTL))camera=CameraStatus.WAITING;
        return new View(e.mode,camera,e.message,e.session);
    }
    synchronized Frame frameForRender(UUID owner,long now){
        View view=view(owner,now);if(view==null||view.mode!=Mode.CAMERA||view.camera!=CameraStatus.LIVE)return null;
        Entry e=entries.get(owner);Frame frame=e.frame;e.frame=null;return frame;
    }
    public synchronized void endCamera(UUID owner,UUID session){
        Entry e=entries.get(owner);if(e!=null&&Objects.equals(e.session,session)&&session!=null){retire(e);e.mode=Mode.MESSAGES;e.session=null;e.frame=null;e.sequence=-1;e.camera=CameraStatus.WAITING;}
    }
    public synchronized void remove(UUID owner){entries.remove(owner);}
    public synchronized void clear(){entries.clear();}
}
