package com.wildfire.gui;
/** Continuous 220 ms expansion, including reversal midway through a transition. */
public final class ToastControlsReveal {
    private boolean open;
    private long changed;
    private double from;
    private double progress;
    public static int height(boolean action,double reveal){
        return 43+(action?(int)Math.round(25*Math.clamp(reveal,0,1)):0);
    }
    public double update(boolean next,long now){
        double t=Math.clamp((now-changed)/220_000_000d,0,1);
        progress=from+((open?1:0)-from)*(t*t*(3-2*t));
        if(next!=open){from=progress;open=next;changed=now;}
        return progress;
    }
}
