package com.wildfire.gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
/** Appearance entry drawn as a shirt, independent of installed fonts. */
public final class PlayerAssetsButton extends Button {
 public PlayerAssetsButton(Screen parent,Consumer<String> request,int x,int y){
  super(x,y,18,18,Component.literal("Скины и плащи"),b->Minecraft.getInstance().gui.setScreen(new PlayerAssetsScreen(parent,request)),DEFAULT_NARRATION);
  setTooltip(Tooltip.create(getMessage()));
 }
 @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
  FlatButtons.plate(g,getX(),getY(),18,18,active,isHoveredOrFocused(),false,UiTheme.ACCENT);
  int x=getX()+3,y=getY()+3;
  g.fill(x+3,y+2,x+9,y+12,0xFFAE86D3);
  g.fill(x+1,y+2,x+4,y+6,0xFFD4B7F2);g.fill(x+8,y+2,x+11,y+6,0xFFD4B7F2);
  g.fill(x+4,y+1,x+8,y+3,0xFF24212E);g.fill(x+4,y+10,x+8,y+11,0xFFD4B7F2);
 }
}
