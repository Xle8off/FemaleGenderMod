package com.wildfire.gui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Preference rows retain native keyboard navigation and button narration. */
public final class SettingsControls {
    private SettingsControls() {}
    public static Builder toggle(String label, int icon, BooleanSupplier value, Button.OnPress action) {
        return new Builder(label,icon,value,action);
    }
    public static Builder category(String label,int icon,Button.OnPress action) {
        return new Builder(label,icon,null,action);
    }
    public static final class Builder {
        private final String label; private final int icon; private final BooleanSupplier value; private final Button.OnPress action;
        private int x,y,w,h;
        Builder(String label,int icon,BooleanSupplier value,Button.OnPress action){this.label=label;this.icon=icon;this.value=value;this.action=action;}
        public Builder bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
        public Button build(){return new Control(x,y,w,h,label,icon,value,action);}
    }
    private static final class Control extends Button {
        private final String label; private final int icon; private final BooleanSupplier value;
        private float position; private long lastFrame=System.nanoTime();
        Control(int x,int y,int w,int h,String label,int icon,BooleanSupplier value,OnPress action){
            super(x,y,w,h,Component.literal(label),action,DEFAULT_NARRATION);
            this.label=label;this.icon=icon;this.value=value;position=value!=null&&value.getAsBoolean()?1:0;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            var font=Minecraft.getInstance().font;
            FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),true,isHoveredOrFocused()||value==null&&!active,false,UiTheme.ACCENT);
            String text=font.plainSubstrByWidth(label,Math.max(0,getWidth()-(value==null?24:58)));
            int start=value==null?getX()+(getWidth()-font.width(text)-18)/2:getX()+6;
            g.pose().pushMatrix();g.pose().translate(start,getY()+(getHeight()-12)/2f);g.pose().scale(.6f,.6f);
            ColorMenuTitleIcons.draw(g,icon,0,0);g.pose().popMatrix();
            g.text(font,text,start+18,getY()+(getHeight()-font.lineHeight)/2,UiTheme.TEXT,false);
            if(value==null)return;
            long now=System.nanoTime();double dt=Math.min(.05,(now-lastFrame)/1e9);lastFrame=now;
            position+=(float)(((value.getAsBoolean()?1:0)-position)*(1-Math.exp(-20*dt)));
            int x=getX()+getWidth()-32,y=getY()+(getHeight()-12)/2;
            int color=value.getAsBoolean()?0xFF53BFA0:0xFF596171;
            // Rounded capsule and round thumb, independent of the font's glyphs.
            for(int r=0;r<12;r++){
                int inset=(int)Math.ceil(6-Math.sqrt(36-Math.pow(r+.5-6,2)));
                g.fill(x+inset,y+r,x+26-inset,y+r+1,color);
            }
            int thumb=x+2+Math.round(position*14);
            for(int r=0;r<8;r++){
                int inset=(int)Math.ceil(4-Math.sqrt(16-Math.pow(r+.5-4,2)));
                g.fill(thumb+inset,y+2+r,thumb+8-inset,y+3+r,0xFFF1F4FA);
            }
        }
    }
}
