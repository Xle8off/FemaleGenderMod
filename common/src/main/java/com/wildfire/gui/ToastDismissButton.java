package com.wildfire.gui;
import java.util.function.*;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
/** Stable toast identity protects against dismissal of a replacement card. */
public final class ToastDismissButton extends com.wildfire.client.music.ChatOverlayButton {
    private final LongSupplier id;
    private final IntSupplier y;
    private final DoubleSupplier reveal;
    private final LongConsumer dismiss;
    private long displayed=-1;
    public ToastDismissButton(LongSupplier id,IntSupplier y,DoubleSupplier reveal,LongConsumer dismiss){
        super(210,0,16,16,Component.literal("Закрыть уведомление"),b->((ToastDismissButton)b).close());
        this.id=id;this.y=y;this.reveal=reveal;this.dismiss=dismiss;
        visible=false;
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Закрыть уведомление")));
    }
    public void sync(int guiWidth){
        displayed=id.getAsLong();visible=guiWidth>=100&&displayed>=0&&reveal.getAsDouble()>.01;
        setX(Math.min(232,guiWidth)-22);setY(y.getAsInt()+3);
    }
    private void close(){if(displayed>=0&&id.getAsLong()==displayed)dismiss.accept(displayed);}
    @Override public boolean isMouseOver(double x,double y){return reveal.getAsDouble()>=.99&&super.isMouseOver(x,y);}
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubled){
        return reveal.getAsDouble()>=.99&&super.mouseClicked(event,doubled);
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        int color=((int)Math.round(255*reveal.getAsDouble())<<24)|(UiTheme.TEXT&0xFFFFFF);
        for(int i=0;i<7;i++){
            g.fill(getX()+5+i,getY()+5+i,getX()+6+i,getY()+6+i,color);
            g.fill(getX()+11-i,getY()+5+i,getX()+12-i,getY()+6+i,color);
        }
    }
}
