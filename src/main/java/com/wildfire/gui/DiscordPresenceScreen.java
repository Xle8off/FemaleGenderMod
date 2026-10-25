package com.wildfire.gui;

import com.wildfire.client.discord.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DiscordPresenceScreen extends Screen {
 private final Screen parent;private int x,y,w,h;
 public DiscordPresenceScreen(Screen parent){super(Component.literal("Discord Rich Presence"));this.parent=parent;}
 @Override protected void init(){
  w=Math.min(340,width-20);h=Math.min(270,height-16);x=(width-w)/2;y=(height-h)/2;
  String[] labels={"Показывать активность в Discord","Показывать ник","Показывать привилегию","Показывать текущий город","Показывать мир"};String[] keys={"enabled","nickname","privilege","city","world"};
  int pitch=Math.max(16,Math.min(28,(h-82)/keys.length));for(int i=0;i<keys.length;i++){String key=keys[i];addRenderableWidget(SettingsControls.toggle(labels[i],0,()->PresenceSettings.get(key),b->PresenceSettings.toggle(key)).bounds(x+12,y+35+i*pitch,w-24,Math.min(22,pitch-2)).build());}
  addRenderableWidget(FlatButtons.builder(Component.literal("Назад"),b->onClose()).bounds(x+w/2-45,y+h-28,90,20).build());
 }
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
  RoundedPanels.fill(g,x,y,x+w,y+h,UiTheme.ACCENT,8);RoundedPanels.fill(g,x+1,y+1,x+w-1,y+h-1,UiTheme.BACKGROUND,7);g.centeredText(font,"Discord Rich Presence",width/2,y+12,UiTheme.TEXT);super.extractRenderState(g,mx,my,delta);
  g.text(font,font.plainSubstrByWidth(DiscordPresence.status(),w-24),x+12,y+h-43,UiTheme.MUTED,false);
 }
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
