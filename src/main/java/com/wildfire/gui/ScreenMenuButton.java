package com.wildfire.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.wildfire.client.screens.ScreenInteraction;

public final class ScreenMenuButton extends Button {
 public ScreenMenuButton(Screen parent,int x,int y){super(x,y,18,18,Component.literal("Экраны"),b->ScreenInteraction.openNearest(parent),DEFAULT_NARRATION);setTooltip(Tooltip.create(Component.literal("Управление ближайшим экраном · ПКМ по экрану")));}
 @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
  FlatButtons.plate(g,getX(),getY(),18,18,active,isHoveredOrFocused(),false,UiTheme.ACCENT);
  int x=getX()+3,y=getY()+4;g.fill(x,y,x+12,y+8,0xFFB99BEF);g.fill(x+1,y+1,x+11,y+7,0xFF222936);
  g.fill(x+5,y+8,x+7,y+10,0xFFB99BEF);g.fill(x+3,y+10,x+9,y+11,0xFFB99BEF);
 }
}
