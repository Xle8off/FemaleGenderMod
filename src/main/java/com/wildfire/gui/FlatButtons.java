package com.wildfire.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

/** Shared button surface; no vanilla sprites, bevels or text shadows. */
public final class FlatButtons {
    public static void backdrop(GuiGraphicsExtractor g,int left,int top,int right,int bottom,int color,int radius){rounded(g,left,top,right-left,bottom-top,color,radius);}
    /** Round only the outside ends of joined panels, without overlapping translucent fills. */
    public static void joinedBackdrop(GuiGraphicsExtractor g,int left,int top,int right,int bottom,int color,int radius,boolean roundTop,boolean roundBottom){
        int height=bottom-top,width=right-left;if(height<=0||width<=0)return;
        int r=Math.min(radius,Math.min(width,height)/2);
        for(int row=0;row<height;row++){
            int edge=Math.min(roundTop?row:height,roundBottom?height-row-1:height);
            int inset=edge<r?(int)Math.ceil(r-Math.sqrt(r*r-(r-edge-.5)*(r-edge-.5))):0;
            g.fill(left+inset,top+row,right-inset,top+row+1,color);
        }
    }
    /** Exposed body corner when only the settings tab remains above the right side. */
    public static void topLeftBackdrop(GuiGraphicsExtractor g,int left,int top,int right,int bottom,int color,int radius){
        int height=bottom-top,width=right-left;if(height<=0||width<=0)return;
        int r=Math.min(radius,Math.min(width,height)/2);
        for(int row=0;row<height;row++){
            int inset=row<r?(int)Math.ceil(r-Math.sqrt(r*r-(r-row-.5)*(r-row-.5))):0;
            g.fill(left+inset,top+row,right,top+row+1,color);
        }
    }
    private FlatButtons() {}
    public static void joinedFrame(GuiGraphicsExtractor g,int left,int bodyTop,int right,int bottom,int tabLeft,int tabTop,int color,int radius){
        // Clip the body's top edge at the tab, and let the tab's right edge continue downward.
        g.enableScissor(left,bodyTop,tabLeft,bodyTop+radius);
        frame(g,left,bodyTop,right,bottom,color,radius);g.disableScissor();
        g.enableScissor(left,bodyTop+radius,right,bottom);
        frame(g,left,bodyTop,right,bottom,color,radius);g.disableScissor();
        g.enableScissor(tabLeft,tabTop,right,bodyTop);
        frame(g,tabLeft,tabTop,right,bottom,color,radius);g.disableScissor();
        g.fill(right-1,bodyTop,right,Math.min(bottom,bodyTop+radius),color);
    }
    public static void frame(GuiGraphicsExtractor g,int left,int top,int right,int bottom,int color,int radius){
        int width=right-left,height=bottom-top;if(width<3||height<3)return;
        int r=Math.min(radius,Math.min(width,height)/2),innerRadius=Math.max(0,r-1);
        for(int row=0;row<height;row++){
            int edge=Math.min(row,height-row-1),outer=edge<r?(int)Math.ceil(r-Math.sqrt(r*r-(r-edge-.5)*(r-edge-.5))):0;
            if(row==0||row==height-1){g.fill(left+outer,top+row,right-outer,top+row+1,color);continue;}
            int innerEdge=Math.min(row-1,height-row-2),inner=innerEdge<innerRadius?(int)Math.ceil(innerRadius-Math.sqrt(innerRadius*innerRadius-(innerRadius-innerEdge-.5)*(innerRadius-innerEdge-.5))):0;
            g.fill(left+outer,top+row,left+1+inner,top+row+1,color);
            g.fill(right-1-inner,top+row,right-outer,top+row+1,color);
        }
    }
    public static void plate(GuiGraphicsExtractor g,int x,int y,int width,int height,boolean active,boolean hot,boolean pressed,int accent){
        if(width<1||height<1)return;
        rounded(g,x,y,width,height,active&&hot?accent:UiTheme.BORDER,4);
        rounded(g,x+1,y+1,width-2,height-2,!active?0xFF202630:pressed?0xFF414052:hot?0xFF353D4C:UiTheme.SURFACE,3);
    }
    private static void rounded(GuiGraphicsExtractor g,int x,int y,int width,int height,int color,int radius){
        if(width<1||height<1)return;
        int r=Math.min(radius,Math.min(width,height)/2);
        // The straight middle is one rectangle, not one draw element per scanline.
        if(height>2*r)g.fill(x,y+r,x+width,y+height-r,color);
        for(int row=0;row<r;row++){
            int inset=(int)Math.ceil(r-Math.sqrt(r*r-(r-row-.5)*(r-row-.5)));
            g.fill(x+inset,y+row,x+width-inset,y+row+1,color);
            g.fill(x+inset,y+height-row-1,x+width-inset,y+height-row,color);
        }
    }
    public static Builder builder(Component message,Button.OnPress action){return new Builder(message,action);}
    public static final class Builder {
        private final Component message;private final Button.OnPress action;
        private int x,y,width=150,height=20;
        private Tooltip tooltip;
        private Button.CreateNarration narration=StyledButton.defaultNarration();
        private Builder(Component message,Button.OnPress action){this.message=message;this.action=action;}
        public Builder bounds(int x,int y,int width,int height){return pos(x,y).size(width,height);}
        public Builder pos(int x,int y){this.x=x;this.y=y;return this;}
        public Builder size(int width,int height){this.width=width;this.height=height;return this;}
        public Builder width(int width){this.width=width;return this;}
        public Builder tooltip(Tooltip tooltip){this.tooltip=tooltip;return this;}
        public Builder createNarration(Button.CreateNarration narration){this.narration=narration;return this;}
        public Button build(){var button=new StyledButton(x,y,width,height,message,action,narration);button.setTooltip(tooltip);return button;}
    }
    private static final class StyledButton extends Button {
        static CreateNarration defaultNarration(){return DEFAULT_NARRATION;}
        private long pressedUntil;
        StyledButton(int x,int y,int width,int height,Component message,OnPress action,CreateNarration narration){
            super(x,y,width,height,message,action,narration);
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers event){
            pressedUntil=System.nanoTime()+120000000L;super.onPress(event);
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            boolean hot=active&&isHoveredOrFocused();
            plate(g,getX(),getY(),getWidth(),getHeight(),active,hot,System.nanoTime()<pressedUntil,UiTheme.ACCENT);
            var font=Minecraft.getInstance().font;
            var label=net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(getMessage(),Math.max(0,getWidth()-8)));
            g.text(font,label,getX()+(getWidth()-font.width(label))/2,getY()+(getHeight()-font.lineHeight)/2,active?UiTheme.TEXT:UiTheme.MUTED,false);
            if(isHovered())g.requestCursor(active?CursorTypes.POINTING_HAND:CursorTypes.NOT_ALLOWED);
        }
    }
}
