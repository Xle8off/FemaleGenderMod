package com.wildfire.client.overhead;
import java.util.*;
public final class OverheadMediaCheck {
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        check(!CameraVisibility.previewAllowed(true,true),"own first-person preview is immediately disabled");
        check(CameraVisibility.previewAllowed(true,false),"own F5 preview uses local capture");
        check(CameraVisibility.previewAllowed(false,true),"first-person view still receives other players");
        for(int i=0;i<CameraCircleGeometry.SEGMENTS;i++){
            double a=CameraCircleGeometry.angle(i),b=CameraCircleGeometry.angle(i+1);
            double ax=Math.sin(a),ay=-Math.cos(a),bx=Math.sin(b),by=-Math.cos(b);
            check(ax*by-ay*bx<0,"camera fan must face viewer like the text/sticker quads");
        }
        var visibility=new CameraVisibility();long clock=10_000_000_000L;
        check(!visibility.wanted(true,0,clock),"unseen camera does not subscribe");
        visibility.visible(clock);
        check(visibility.wanted(true,1024,clock),"32 block boundary included");
        check(!visibility.wanted(true,1024.01,clock),"beyond 32 blocks stops immediately");
        check(!visibility.wanted(false,1,clock),"quit, world change or camera off stops immediately");
        check(visibility.wanted(true,1,clock+2_999_999_999L),"turning keeps subscription for three seconds");
        check(!visibility.wanted(true,1,clock+3_000_000_000L),"three seconds offscreen stops subscription");
        visibility.visible(clock+4_000_000_000L);
        check(visibility.wanted(true,1,clock+4_000_000_000L),"visible again resumes subscription");
        var state=new OverheadMedia();UUID owner=UUID.randomUUID(),session=UUID.randomUUID();long now=1_000_000_000L;
        check(state.message(owner,"one","Hello",5_000_000_000L,now),"approved text accepted");
        check(!state.message(owner,"one","Hello",5_000_000_000L,now+1),"duplicate cannot extend lifetime");
        check(!state.message(owner,"bad","private\u0000text",5,now),"unsafe control characters rejected");
        check(state.view(owner,now).mode()==OverheadMedia.Mode.MESSAGES,"messages default");
        state.beginCamera(owner,session,now);
        check(state.view(owner,now).mode()==OverheadMedia.Mode.CAMERA&&state.view(owner,now).camera()==OverheadMedia.CameraStatus.WAITING,"camera selects one exclusive waiting state");
        byte[] rgba=new byte[16*16*4];rgba[0]=42;
        check(!state.frame(owner,session,0,16,16,rgba,now),"no frames before explicit LIVE state");
        check(state.cameraStatus(owner,session,OverheadMedia.CameraStatus.LIVE,now),"explicit stream state");
        check(!state.frame(owner,UUID.randomUUID(),0,16,16,rgba,now),"wrong session rejected");
        check(!state.frame(owner,session,0,512,512,rgba,now),"oversized frames rejected");
        check(state.frame(owner,session,0,16,16,rgba,now),"valid frame accepted");rgba[0]=0;
        var decoded=state.frameForRender(owner,now);check(decoded!=null&&decoded.rgba()[0]==42,"frame input copied and owned");
        check(state.frameForRender(owner,now)==null,"one pending frame, not unbounded replay");
        check(!state.frame(owner,session,1,16,16,rgba,now+1),"30fps input limit");
        check(state.frame(owner,session,1,16,16,rgba,now+34_000_000L),"30fps accepted");
        check(state.frame(owner,session,2,16,16,rgba,now+100_000_000L),"new frame accepted after pacing");
        check(!state.frame(owner,session,1,16,16,rgba,now+200_000_000L),"out-of-order sequence rejected");
        check(state.view(owner,now+4_000_000_000L).camera()==OverheadMedia.CameraStatus.WAITING,"stale video never freezes as live");
        check(state.cameraStatus(owner,session,OverheadMedia.CameraStatus.PAUSED,now+4_000_000_000L),"pause state");
        check(state.frameForRender(owner,now+4_000_000_000L)==null,"pause releases pending image");
        state.endCamera(owner,UUID.randomUUID());check(state.view(owner,now).mode()==OverheadMedia.Mode.CAMERA,"old session cannot stop new camera");
        state.endCamera(owner,session);check(state.view(owner,now).mode()==OverheadMedia.Mode.MESSAGES,"stop restores message mode");
        state.mode(owner,OverheadMedia.Mode.OFF,now);check(state.view(owner,now).mode()==OverheadMedia.Mode.OFF,"off is exclusive");
        UUID newer=UUID.randomUUID();state.beginCamera(owner,newer,now);
        check(!state.beginCamera(owner,session,now+1)&&state.view(owner,now+1).session().equals(newer),"retired start cannot replace new camera");
        check(state.view(owner,now+16_000_000_000L).mode()==OverheadMedia.Mode.MESSAGES,"lost remote lease restores messages");
        state.clear();var cameras=new ArrayList<UUID>();
        for(int i=0;i<20;i++){
            UUID id=UUID.randomUUID();cameras.add(id);state.beginCamera(id,session,now);state.cameraStatus(id,session,OverheadMedia.CameraStatus.LIVE,now);state.frame(id,session,0,16,16,rgba,now);
        }
        int pending=0;for(UUID id:cameras)if(state.frameForRender(id,now)!=null)pending++;
        check(pending==8,"pending frame memory capped at eight latest cameras");
        state.clear();check(state.view(cameras.getFirst(),now)==null,"disconnect clears camera and message state");
        System.out.println("Overhead media: exclusive modes, explicit live, TTL, sessions, sequence, frame bounds, pacing and cleanup passed");
    }
}
