package dev.aeltorx.inflow.client;

/** Clockwise, one-cell-at-a-time reveal; GPU-only, never re-rasterize artwork for animation. */
public final class WheelReveal {
    public static final long STAGGER=45_000_000L,DURATION=130_000_000L,TOTAL=7*STAGGER+DURATION;
    private WheelReveal(){}
    public static long reverseTime(long openedElapsed,long closingElapsed){return Math.max(0,Math.clamp(openedElapsed,0,TOTAL)-Math.max(0,closingElapsed));}
    public static float at(int order,long elapsed){
        if(order<0)return 1;
        double p=Math.clamp((elapsed-order*STAGGER)/(double)DURATION,0,1);
        return (float)(1-Math.pow(1-p,3));
    }
}
