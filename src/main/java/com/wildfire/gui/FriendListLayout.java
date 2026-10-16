package com.wildfire.gui;

/** Bounded virtual list: only viewport rows have widgets, never page buttons. */
public record FriendListLayout(int viewport, int maxOffset, int offset, int slots) {
    public static final int TOP=83, PITCH=38;
    public static FriendListLayout create(int height,int count,int requestedOffset) {
        int viewport=Math.max(1,height-156);
        int maxOffset=(int)Math.max(0,Math.min(Integer.MAX_VALUE,(long)Math.max(0,count)*PITCH-viewport));
        return new FriendListLayout(viewport,maxOffset,Math.clamp(requestedOffset,0,maxOffset),(viewport+PITCH-1)/PITCH+1);
    }
    public int first() { return offset/PITCH; }
    public int rowTop(int index) { return TOP+(index-first())*PITCH-offset%PITCH; }
    public boolean buttonVisible(int index) { int y=rowTop(index)+9; return y>=TOP && y+20<=TOP+viewport; }
}
