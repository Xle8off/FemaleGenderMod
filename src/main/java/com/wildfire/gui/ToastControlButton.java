package com.wildfire.gui;
import java.util.function.DoubleSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
/** Reveal the action row with the notification card; partial controls are not clickable. */
public class ToastControlButton extends com.wildfire.client.music.ChatOverlayButton {
    protected final DoubleSupplier reveal;
    public ToastControlButton(int x,int y,int width,int height,Component label,OnPress action,DoubleSupplier reveal){
        super(x,y,width,height,label,action);this.reveal=reveal;
    }
    @Override public boolean isMouseOver(double x,double y){return reveal.getAsDouble()>=.99&&super.isMouseOver(x,y);}
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubled){
        return reveal.getAsDouble()>=.99&&super.mouseClicked(event,doubled);
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        int shown=Math.clamp((int)Math.round(25*reveal.getAsDouble())-5,0,getHeight());
        if(shown==0)return;
        g.enableScissor(getX(),getY(),getX()+getWidth(),getY()+shown);
        super.extractContents(g,mx,my,delta);g.disableScissor();
    }
}
