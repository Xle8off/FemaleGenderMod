package com.wildfire.gui.screen;

/** Pixel-based viewport shared by rendering, hit testing and read accounting. */
public record NoticeListLayout(int top,int bottom,int count,int offset) {
    public static final int PITCH=38,CARD_HEIGHT=36;
    public static NoticeListLayout create(int screenHeight,int count,int requestedOffset){
        int top=36,bottom=Math.max(top+1,screenHeight-46);
        int contentHeight=Math.max(0,count*PITCH-2);
        int max=Math.max(0,contentHeight-(bottom-top));
        return new NoticeListLayout(top,bottom,count,Math.clamp(requestedOffset,0,max));
    }
    public int rowTop(int index){return top+index*PITCH-offset;}
    public int first(){return Math.min(count,offset/PITCH);}
    public int end(){return Math.min(count,(offset+bottom-top+PITCH-1)/PITCH);}
    public int maxOffset(){return Math.max(0,count*PITCH-2-(bottom-top));}
    public int hit(double y){
        if(y<top||y>=bottom)return -1;
        int local=(int)Math.floor(y-top)+offset,index=local/PITCH;
        return index<count&&local%PITCH<CARD_HEIGHT?index:-1;
    }
}
