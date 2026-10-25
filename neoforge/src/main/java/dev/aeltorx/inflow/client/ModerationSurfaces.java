package dev.aeltorx.inflow.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** One rounded contour; never place a square frame over an already framed texture. */
public final class ModerationSurfaces {
    private ModerationSurfaces(){}
    public static void workspace(GuiGraphicsExtractor g,int x,int y,int w,int h,boolean accountHot){
        rounded(g,x,y,w-4,h-5,ModerationTheme.ACCENT.getRGB(),6);
        rounded(g,x+1,y+1,w-6,h-7,ModerationTheme.BACKGROUND.getRGB(),5);
        g.fill(x+1,y+32,x+w-5,y+33,ModerationTheme.BORDER.getRGB());g.fill(x+128,y+42,x+129,y+h-59,ModerationTheme.BORDER.getRGB());
        control(g,x+140,y+42,ModerationPanelLayout.contentWidth(w),46,true,false,true);
        control(g,x+8,y+h-40,w-20,30,true,accountHot,true);
        g.fill(x+8,y+h-45,x+w-12,y+h-44,ModerationTheme.BORDER.getRGB());
    }
    public static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h){
        rounded(g,x,y,w,h,ModerationTheme.ACCENT.getRGB(),5);
        rounded(g,x+1,y+1,w-2,h-2,ModerationTheme.CHROME.getRGB(),4);
    }
    public static void control(GuiGraphicsExtractor g,int x,int y,int w,int h,boolean active,boolean hot,boolean field){
        control(g,x,y,w,h,active,hot,field,1);
    }
    public static void control(GuiGraphicsExtractor g,int x,int y,int w,int h,boolean active,boolean hot,boolean field,float opacity){
        rounded(g,x,y,w,h,fade((active&&hot?ModerationTheme.ACCENT:ModerationTheme.BORDER).getRGB(),opacity),4);
        rounded(g,x+1,y+1,w-2,h-2,fade((field?ModerationTheme.CHROME:hot?ModerationTheme.HOVER:ModerationTheme.SURFACE).getRGB(),opacity),3);
    }
    static int fade(int color,float opacity){return (color&0xffffff)|((int)((color>>>24)*Math.clamp(opacity,0,1))<<24);}
    private static void rounded(GuiGraphicsExtractor g,int x,int y,int w,int h,int color,int radius){
        if(w<=0||h<=0)return;int r=Math.min(radius,Math.min(w,h)/2);
        if(h>2*r)g.fill(x,y+r,x+w,y+h-r,color);
        for(int row=0;row<r;row++){
            int edge=row;
            int inset=edge<r?(int)Math.ceil(r-Math.sqrt(r*r-(r-edge-.5)*(r-edge-.5))):0;
            g.fill(x+inset,y+row,x+w-inset,y+row+1,color);
            g.fill(x+inset,y+h-row-1,x+w-inset,y+h-row,color);
        }
    }
}
