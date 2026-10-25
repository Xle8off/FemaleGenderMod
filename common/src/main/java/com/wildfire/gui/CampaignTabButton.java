package com.wildfire.gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Pixel icons consistent with the existing notification sidebar. */
public final class CampaignTabButton extends Button {
    private final boolean admin;
    public CampaignTabButton(Screen parent,boolean admin,int x,int y){
        super(x,y,18,18,Component.empty(),b->Minecraft.getInstance().gui.setScreen(new CampaignScreen(parent,admin)),DEFAULT_NARRATION);
        this.admin=admin;setTooltip(Tooltip.create(Component.literal(admin?"Администрирование":"Голосования")));
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,UiTheme.ACCENT);
        int x=getX()+4,y=getY()+4;
        if(admin){
            g.fill(x+1,y,x+9,y+6,0xFFB99BEF);g.fill(x+2,y+6,x+8,y+8,0xFFB99BEF);g.fill(x+4,y+8,x+6,y+10,0xFFB99BEF);
            g.fill(x+4,y+2,x+6,y+5,0xFFF3D279);
        }else{
            g.fill(x,y+4,x+2,y+10,0xFF9BC5FF);g.fill(x+4,y,x+6,y+10,0xFFB99BEF);g.fill(x+8,y+2,x+10,y+10,0xFFF3D279);
        }
    }
}
