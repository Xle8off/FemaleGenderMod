package com.wildfire.client.screens;
import com.wildfire.client.music.*;
import com.wildfire.gui.RoundedPanels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
/** The card occupies the music HUD slot. Its controls are installed only in ChatScreen. */
public final class ScreenAudioHud {
 private ScreenAudioHud(){}
 public static boolean visible(){return com.wildfire.client.music.SpringPlusSession.enabled()&&(ScreenAudio.inRange()&&BrowserRuntime.running()||!MusicPriority.notice().isEmpty());}
 private static int width(int w){return Math.max(80,Math.min(240,w-20));}
 public static boolean draw(GuiGraphicsExtractor g){
  if(!visible())return false;
  var font=Minecraft.getInstance().font;int w=width(g.guiWidth()),x=g.guiWidth()-w-10,y=g.guiHeight()-66;
  RoundedPanels.fill(g,x,y,x+w,y+56,0xE92C3541);
  String heading=ScreenAudio.inRange()?ScreenAudio.site()+" · "+Math.round(BrowserRuntime.volume()*100)+"%":"Музыка";
  g.text(font,font.plainSubstrByWidth(heading,w-12),x+6,y+5,0xFFE4DFEF,false);
  if(ScreenAudio.inRange()){
   int left=x+8,right=x+w-8;
   g.fill(left,y+19,right,y+22,0xFF525966);
   int thumb=left+Math.round((right-left)*BrowserRuntime.volume());
   g.fill(left,y+19,thumb,y+22,0xFFB794DD);g.fill(thumb-1,y+17,thumb+2,y+24,0xFFEBD9FF);
  }
  g.text(font,"Приоритет: "+(AudioPreferences.clientPriority()?"своя музыка":"пластинки"),x+6,y+29,0xFFB794DD,false);
  String notice=MusicPriority.notice();
  g.text(font,font.plainSubstrByWidth(notice.isEmpty()?"Управление — при открытом чате":notice,w-12),x+6,y+43,0xFFAAB3C0,false);
  return true;
 }
 public static AbstractWidget widget(){return new Controls();}
 private static final class Controls extends AbstractWidget {
  private boolean dragging;
  Controls(){super(0,0,240,56,Component.literal("Звук экрана и приоритет музыки"));}
  private void layout(){var mc=Minecraft.getInstance();setWidth(width(mc.getWindow().getGuiScaledWidth()));setX(mc.getWindow().getGuiScaledWidth()-getWidth()-10);setY(mc.getWindow().getGuiScaledHeight()-66);visible=true;active=ScreenAudioHud.visible();}
  @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){layout();String text=MusicPriority.notice();setTooltip(Tooltip.create(Component.literal((text.isEmpty()?"Ползунок: громкость экрана.":text)+"\nНажмите «Приоритет», чтобы переключить источник.")));}
  private void change(double mouse){BrowserRuntime.volume((float)Math.clamp((mouse-getX()-8)/(getWidth()-16d),0,1));}
  @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){layout();return super.mouseClicked(e,twice);}
  @Override public void onClick(MouseButtonEvent e,boolean twice){if(e.y()<getY()+26&&ScreenAudio.inRange()){dragging=true;change(e.x());}else if(e.y()>=getY()+26&&e.y()<getY()+42)AudioPreferences.togglePriority();}
  @Override protected void onDrag(MouseButtonEvent e,double dx,double dy){if(dragging)change(e.x());}
  @Override public void onRelease(MouseButtonEvent e){if(dragging)AudioPreferences.save();dragging=false;}
  @Override public boolean shouldTakeFocusAfterInteraction(){return false;}
  @Override public net.minecraft.client.gui.ComponentPath nextFocusPath(net.minecraft.client.gui.navigation.FocusNavigationEvent e){return null;}
  @Override public boolean isMouseOver(double x,double y){layout();return active&&super.isMouseOver(x,y);}
  @Override public boolean keyPressed(KeyEvent e){if(e.key()==262||e.key()==263){BrowserRuntime.volume(BrowserRuntime.volume()+(e.key()==262?.05f:-.05f));AudioPreferences.save();return true;}if(e.key()==257||e.key()==32){AudioPreferences.togglePriority();return true;}return super.keyPressed(e);}
  @Override protected void updateWidgetNarration(NarrationElementOutput out){out.add(NarratedElementType.TITLE,getMessage());}
 }
}
