package dev.aeltorx.inflow.client;

/** Right-aligned HUD chip, 11px (half the 22px music card) above the player at height-32. */
public final class MinimizedModerationLayout {
    private MinimizedModerationLayout(){}
    public static ModerationWindowBounds.Rect of(int width,int height){
        int w=Math.max(1,Math.min(182,width-20)),h=Math.min(22,Math.max(1,height-16));
        return new ModerationWindowBounds.Rect(Math.max(0,width-w-10),Math.max(8,height-32-11-h),w,h);
    }
}
