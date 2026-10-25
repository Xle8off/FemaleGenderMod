package com.wildfire.client.overhead;

import java.util.concurrent.TimeUnit;

/** Actual native SDP/ICE negotiation, but no camera, microphone, SFU session or video capture. */
public final class CameraIceCheck {
    public static void main(String[] args)throws Exception{
        long started=System.nanoTime();
        var peer=NativeCameraPeer.create().get(15,TimeUnit.SECONDS);
        try{
            var offer=peer.offer().get(20,TimeUnit.SECONDS);
            long videos=offer.sdp.lines().filter(s->s.startsWith("m=video ")).count();
            long media=offer.sdp.lines().filter(s->s.startsWith("m=")).count();
            long candidates=offer.sdp.lines().filter(s->s.startsWith("a=candidate:")).count();
            if(videos!=1||media!=1||candidates==0)throw new AssertionError("Video-only SDP/candidate check failed");
            System.out.println("Native camera offer ready in "+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)+" ms; candidates="+candidates+"; no hardware opened");
        }finally{
            peer.close();
            NativeCameraPeer.WORKER.submit(()->{}).get(10,TimeUnit.SECONDS);
        }
    }
}
