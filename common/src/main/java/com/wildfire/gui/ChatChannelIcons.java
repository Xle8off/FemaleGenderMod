package com.wildfire.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Compact, font-independent 16px channel icons. */
public final class ChatChannelIcons {
    private ChatChannelIcons(){}
    private static void box(GuiGraphicsExtractor g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+h,color);}
    public static void draw(GuiGraphicsExtractor g,String mode,int x,int y){
        int blue=0xFF99BAFF,gold=0xFFFFBF45,green=0xFF70D8B0,ink=0xFF171E29;
        if(mode.equals("discord")||mode.equals("staff")){
            var id=Identifier.fromNamespaceAndPath("appearance_sync","textures/gui/chat/"+mode+".png");
            g.blit(RenderPipelines.GUI_TEXTURED,id,x,y,0f,0f,16,16,16,16,16,16);return;
        }
        switch(mode){
            case "global" -> {
                for(int row=0;row<16;row++){double dy=row-7.5;int inset=(int)Math.ceil(8-Math.sqrt(Math.max(0,64-dy*dy)));box(g,x+inset,y+row,16-2*inset,1,blue);}
                box(g,x+4,y+3,4,2,green);box(g,x+3,y+5,4,3,green);box(g,x+5,y+8,3,2,green);box(g,x+6,y+10,2,3,green);
                box(g,x+11,y+4,2,3,green);box(g,x+10,y+9,3,3,green);box(g,x+4,y+2,3,1,0xFFD9EAFF);
            }
            case "local" -> {
                // Standalone location pin, centered in the same 16px canvas.
                box(g,x+5,y+1,6,1,gold);box(g,x+3,y+2,10,2,gold);box(g,x+2,y+4,12,4,gold);
                box(g,x+3,y+8,10,2,gold);box(g,x+4,y+10,8,1,gold);box(g,x+5,y+11,6,1,gold);
                box(g,x+6,y+12,4,1,gold);box(g,x+7,y+13,2,2,gold);
                box(g,x+6,y+3,4,1,ink);box(g,x+5,y+4,6,3,ink);box(g,x+6,y+7,4,1,ink);
            }
            case "system" -> {
                box(g,x,y+1,16,14,blue);box(g,x+1,y+4,14,10,ink);box(g,x+2,y+2,2,1,gold);
                box(g,x+3,y+6,1,1,green);box(g,x+4,y+7,1,1,green);box(g,x+5,y+8,1,1,green);box(g,x+4,y+9,1,1,green);box(g,x+3,y+10,1,1,green);box(g,x+8,y+10,4,1,green);
            }
            case "spy" -> {
                for(int col=0;col<16;col++){int half=Math.max(0,4-Math.abs(col-7)/2);box(g,x+col,y+8-half,1,half*2+1,blue);}
                box(g,x+5,y+5,6,7,ink);box(g,x+6,y+6,4,5,0xFFBB95E5);box(g,x+7,y+7,2,3,ink);box(g,x+6,y+6,1,1,0xFFFFFFFF);
            }
            default -> {
                box(g,x,y+1,12,9,blue);box(g,x+2,y+10,3,2,blue);box(g,x+4,y+6,12,8,gold);box(g,x+12,y+14,2,2,gold);
                box(g,x+2,y+4,7,1,ink);box(g,x+6,y+9,8,1,ink);box(g,x+6,y+11,5,1,ink);
            }
        }
    }
}
