package com.wildfire.gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Colored small illustrations with rounded shapes and a dark outline. */
public final class ColorMenuTitleIcons {
    private static final int INK=0xFF171E29,BLUE=0xFF99BAFF,GOLD=0xFFFFBF45,PURPLE=0xFFBB95E5;
    private ColorMenuTitleIcons(){}
    private static void round(GuiGraphicsExtractor g,int x,int y,int w,int h,int r,int color){
        for(int row=0;row<h;row++){int edge=Math.min(row,h-row-1),inset=edge<r?(int)Math.ceil(r-Math.sqrt(r*r-(r-edge-.5)*(r-edge-.5))):0;g.fill(x+inset,y+row,x+w-inset,y+row+1,color);}
    }
    private static void box(GuiGraphicsExtractor g,int x,int y,int w,int h,int r,int color){round(g,x,y,w,h,r,INK);round(g,x+1,y+1,w-2,h-2,Math.max(1,r-1),color);}
    private static void dome(GuiGraphicsExtractor g,int x,int y,int w,int h,int color){
        for(int row=0;row<h;row++){double t=(row+.5)/h;int inset=(int)Math.floor(w/2d*(1-Math.sqrt(1-(1-t)*(1-t))));g.fill(x+inset,y+row,x+w-inset,y+row+1,color);}
    }
    private static void circle(GuiGraphicsExtractor g,int x,int y,int size,int color){
        double radius=size/2d;
        for(int row=0;row<size;row++){
            double dy=row+.5-radius,span=Math.sqrt(radius*radius-dy*dy);
            int left=(int)Math.ceil(radius-span-.5),right=(int)Math.floor(radius+span-.5)+1;
            g.fill(x+left,y+row,x+right,y+row+1,color);
        }
    }
    public static void draw(GuiGraphicsExtractor g,int kind,int x,int y){
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(kind==0||kind==3?1f:1.2f,1f);x=0;y=0;
        switch(kind){
            case 2 -> {
                box(g,x,y+7,14,11,4,GOLD);
                for(int row=0;row<4;row++){g.fill(x+3,y+17+row,x+8-row,y+18+row,INK);if(row<3)g.fill(x+4,y+17+row,x+7-row,y+18+row,GOLD);}
                for(int i=0;i<3;i++)round(g,x+3+i*3,y+11,2,2,1,INK);
                box(g,x+7,y,13,13,4,BLUE);
                for(int row=0;row<4;row++){g.fill(x+13+row,y+12+row,x+18,y+13+row,INK);if(row<3)g.fill(x+14+row,y+12+row,x+17,y+13+row,BLUE);}
                round(g,x+10,y+4,7,2,1,INK);round(g,x+10,y+8,7,2,1,INK);
            }
            case 1 -> {
                // Cancel the group's horizontal widening for both circular heads only.
                g.pose().pushMatrix();g.pose().scale(1f/1.2f,1f);
                circle(g,x+13,y+2,8,INK);circle(g,x+14,y+3,6,BLUE);
                circle(g,x+3,y+1,9,INK);circle(g,x+4,y+2,7,PURPLE);
                g.pose().popMatrix();
                dome(g,x+9,y+11,11,7,INK);dome(g,x+10,y+12,9,5,BLUE);
                dome(g,x,y+11,13,9,INK);dome(g,x+1,y+12,11,7,PURPLE);
            }
            case 4 -> TownsPanel.icon(g,x,y);
            case 3 -> {
                for(int i=0;i<8;i++){double angle=i*Math.PI/4;int cx=x+10+(int)Math.round(Math.cos(angle)*7),cy=y+10+(int)Math.round(Math.sin(angle)*7);box(g,cx-3,cy-3,6,6,2,BLUE);}
                round(g,x+2,y+2,16,16,8,INK);round(g,x+3,y+3,14,14,7,BLUE);
                round(g,x+6,y+6,8,8,4,INK);round(g,x+7,y+7,6,6,3,UiTheme.CHROME);g.fill(x+5,y+5,x+7,y+6,0xFFD8E6FF);
            }
            default -> {
                box(g,x+8,y,4,4,2,GOLD);box(g,x+3,y+2,14,14,6,GOLD);g.fill(x+4,y+9,x+16,y+16,GOLD);
                round(g,x,y+14,20,4,2,INK);round(g,x+1,y+15,18,2,1,0xFFE69C32);
                round(g,x+7,y+18,6,2,1,GOLD);round(g,x+5,y+5,2,7,1,0xFFFFE09A);
            }
        }
        g.pose().popMatrix();
    }
}
