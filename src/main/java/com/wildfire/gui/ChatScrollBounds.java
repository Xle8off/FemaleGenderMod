package com.wildfire.gui;

/** Pixel scrolling stops exactly when the oldest content reaches the viewport top. */
public final class ChatScrollBounds {
    private ChatScrollBounds(){}
    public static int wheel(int current,int maximum,double amount){
        return (int)Math.clamp((long)current+Math.clamp(Math.round(amount*14),-Integer.MAX_VALUE,Integer.MAX_VALUE),0L,Math.max(0,maximum));
    }
    public record Thumb(int top,int height){}
    public static Thumb thumb(int viewport,int maximum,int scroll){
        int height=Math.max(0,viewport);
        if(maximum<=0||height==0)return new Thumb(0,height);
        int size=Math.min(height,Math.max(12,(int)((long)height*height/((long)height+maximum))));
        int top=(int)Math.round((height-size)*(1-Math.clamp(scroll,0,maximum)/(double)maximum));
        return new Thumb(top,size);
    }
    public static int scrollAt(double thumbTop,int viewport,int maximum){
        var thumb=thumb(viewport,maximum,0);int travel=viewport-thumb.height();
        if(travel<=0||maximum<=0)return 0;
        return (int)Math.round(maximum*(1-Math.clamp(thumbTop/travel,0,1)));
    }
    public static int maximum(int[] heights,int viewportHeight){
        long total=0;for(int height:heights)total+=Math.max(0,height);
        return (int)Math.min(Integer.MAX_VALUE,Math.max(0,total-Math.max(1,viewportHeight)));
    }
}
