package com.wildfire.client.music;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphicsExtractor;import net.minecraft.client.gui.components.Tooltip;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;
public final class StreamHudButton extends ChatOverlayButton {
    public StreamHudButton(Screen parent){super(34,14,20,20,Component.literal("Стримы"),b->Minecraft.getInstance().gui.setScreen(new StreamCenterScreen(parent)));setTooltip(Tooltip.create(Component.literal("Активные стримы")));}
    public void sync(int y,boolean permitted){setY(y);visible=permitted;}
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){com.wildfire.gui.FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,0xFFFF5264);int c=StreamNoticeClient.active().isEmpty()?0xFF9A6570:0xFFFF5264;g.fill(getX()+4,getY()+5,getX()+7,getY()+15,c);g.fill(getX()+7,getY()+7,getX()+10,getY()+13,c);g.fill(getX()+10,getY()+9,getX()+15,getY()+11,c);}
}
