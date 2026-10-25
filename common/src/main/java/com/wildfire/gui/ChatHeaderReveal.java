package com.wildfire.gui;

/** Scroll-like reveal: the top edge travels up while the input stays anchored below. */
public final class ChatHeaderReveal {
    private ChatHeaderReveal(){}
    public static float offset(long elapsedNanos,int height){
        float t=Math.clamp(elapsedNanos/240_000_000f,0f,1f);
        return height*(1f-t)*(1f-t)*(1f-t);
    }
}
