package dev.aeltorx.inflow.client;

/** Shared logical bounds for the workspace and its welcome window. */
public record ModerationWindowBounds(int x,int y,int width,int height) {
    public record Rect(int x,int y,int width,int height){}
    /** One inset rule for widget hitboxes and their rendered surfaces. */
    public Rect fit(int px,int py,int pw,int ph){int ix=x+8,iy=y+8,iw=Math.max(1,width-20),ih=Math.max(1,height-18);int w=Math.clamp(pw,1,iw),h=Math.clamp(ph,1,ih);return new Rect(Math.clamp(px,ix,ix+iw-w),Math.clamp(py,iy,iy+ih-h),w,h);}
    public static ModerationWindowBounds of(int screenWidth,int screenHeight){
        int w=Math.max(1,Math.min(screenWidth-24,560)),h=Math.max(1,Math.min(screenHeight-16,440));
        return new ModerationWindowBounds((screenWidth-w)/2,(screenHeight-h)/2,w,h);
    }
    public float contentScale(){return Math.max(.001f,Math.min(1,Math.min((width-28)/440f,(height-36)/264f)));}
}
