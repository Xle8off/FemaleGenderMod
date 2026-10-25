package com.wildfire.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Fixed footer control, independent of scrollable navigation categories. */
public final class CameraButton extends Button {
    public CameraButton(Screen parent,int x,int y){
        super(x,y,18,18,Component.literal("Камера"),b->Minecraft.getInstance().gui.setScreen(new CameraScreen(parent)),DEFAULT_NARRATION);
        setTooltip(Tooltip.create(Component.literal("Настроить камеру")));
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,UiTheme.ACCENT);
        int x=getX()+3,y=getY()+4;
        g.fill(x,y+1,x+8,y+9,0xFF92BDF5);g.fill(x+1,y+2,x+7,y+8,0xFF526B9B);
        g.fill(x+9,y+3,x+10,y+7,0xFFB99BEF);g.fill(x+10,y+2,x+12,y+8,0xFFB99BEF);
        g.fill(x+2,y+3,x+4,y+5,0xFFE5EFFF);
        if(com.wildfire.client.overhead.CameraClient.capture()!=null){
            int color=com.wildfire.client.overhead.CameraClient.pending()?0xFFFFC857:0xFF52DD91;
            g.fill(getX()+12,getY()+12,getX()+16,getY()+16,color);
        }
    }
}
