package com.wildfire.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Centered, deterministic icons, independent of font glyph bearings. */
public final class ChatActionButton extends Button {
    private final int kind;
    public ChatActionButton(int kind,int x,int y,OnPress action){
        super(x,y,22,20,Component.literal(kind==0?"Изображение":kind==1?"Эмодзи и стикеры":"Отправить"),action,DEFAULT_NARRATION);this.kind=kind;
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,UiTheme.ACCENT);
        int x=getX()+(getWidth()-12)/2,y=getY()+(getHeight()-12)/2,c=active?UiTheme.TEXT:UiTheme.MUTED;
        drawIcon(g,kind,x,y,c);
    }
    public static void drawIcon(GuiGraphicsExtractor g,int kind,int x,int y,int c){
        if(kind==0){
            g.fill(x,y+1,x+12,y+2,c);g.fill(x,y+10,x+12,y+11,c);
            g.fill(x,y+2,x+1,y+10,c);g.fill(x+11,y+2,x+12,y+10,c);
            g.fill(x+8,y+3,x+10,y+5,c);
            for(int i=0;i<5;i++)g.fill(x+2+i,y+8-Math.min(i,4-i),x+3+i,y+9,c);
            g.fill(x+7,y+7,x+10,y+9,c);
        }else if(kind==1){
            g.fill(x+3,y,x+9,y+1,c);g.fill(x+1,y+1,x+3,y+2,c);g.fill(x+9,y+1,x+11,y+2,c);
            g.fill(x,y+2,x+1,y+10,c);g.fill(x+11,y+2,x+12,y+10,c);
            g.fill(x+1,y+10,x+3,y+11,c);g.fill(x+9,y+10,x+11,y+11,c);g.fill(x+3,y+11,x+9,y+12,c);
            g.fill(x+3,y+3,x+4,y+5,c);g.fill(x+8,y+3,x+9,y+5,c);
            g.fill(x+3,y+7,x+4,y+8,c);g.fill(x+8,y+7,x+9,y+8,c);g.fill(x+4,y+8,x+8,y+9,c);
        }else{
            for(int row=0;row<12;row++){int length=12-Math.abs(2*row-11);g.fill(x,y+row,x+length,y+row+1,c);}
            g.fill(x,y+5,x+6,y+7,UiTheme.SURFACE);
        }
    }
}
