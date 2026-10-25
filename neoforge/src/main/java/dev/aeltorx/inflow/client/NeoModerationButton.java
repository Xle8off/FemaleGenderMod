package dev.aeltorx.inflow.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Shield entry inside the notification drawer, with server-authorized access. */
public final class NeoModerationButton extends com.wildfire.client.music.ChatOverlayButton {
 public NeoModerationButton(Screen parent){super(2,8,18,18,Component.literal("Панель модерации"),b->InFlowApi.openPanel(parent));setTooltip(net.minecraft.client.gui.components.Tooltip.create(getMessage()));}
 @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
  int c=com.wildfire.gui.UiTheme.ACCENT;com.wildfire.gui.FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,c);
  int x=getX()+3,y=getY()+3;g.fill(x+1,y,x+11,y+2,c);g.fill(x+1,y+2,x+3,y+8,c);g.fill(x+9,y+2,x+11,y+8,c);
  for(int i=0;i<4;i++)g.fill(x+2+i,y+8+i,x+10-i,y+9+i,c);g.fill(x+5,y+3,x+7,y+8,c);
 }
}
