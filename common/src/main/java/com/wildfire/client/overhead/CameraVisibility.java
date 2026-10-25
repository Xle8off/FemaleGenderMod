package com.wildfire.client.overhead;

/** Monotonic-clock visibility lease, independent of renderer and transport. */
public final class CameraVisibility {
    public static final double MAX_DISTANCE_SQUARED=32*32;
    public static final long OFFSCREEN_GRACE=3_000_000_000L;
    public static boolean previewAllowed(boolean own,boolean firstPerson){return !own||!firstPerson;}
    private long lastSeen;
    private boolean seen;
    public void visible(long now){lastSeen=now;seen=true;}
    public boolean wanted(boolean available,double distanceSquared,long now){
        return available&&distanceSquared<=MAX_DISTANCE_SQUARED&&seen&&now-lastSeen<OFFSCREEN_GRACE;
    }
}
