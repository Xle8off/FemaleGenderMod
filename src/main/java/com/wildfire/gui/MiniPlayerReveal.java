package com.wildfire.gui;

/** Keeps the last track while its card slides away; reversals begin at the current position. */
public final class MiniPlayerReveal {
    public record Track(String title,String time,int accent,float progress){}
    public record Frame(Track track,float visibility){}
    private Track last;
    private float from,target;
    private long changed;
    private float value(long now){
        float t=Math.clamp((now-changed)/220_000_000f,0f,1f);
        float eased=t*t*(3f-2f*t);
        return from+(target-from)*eased;
    }
    public Frame update(Track live,boolean allowed,long now){
        float current=value(now);
        if(live!=null)last=live;
        float next=live!=null&&allowed?1f:0f;
        if(next!=target){from=current;target=next;changed=now;}
        float amount=value(now);
        if(target==0&&amount<=0){last=null;return null;}
        return last==null?null:new Frame(last,amount);
    }
    public void reset(){last=null;from=target=0;changed=0;}
}
