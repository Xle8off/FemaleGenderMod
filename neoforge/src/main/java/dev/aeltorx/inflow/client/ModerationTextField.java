package dev.aeltorx.inflow.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
/** Real native text/caret/selection, with matching padding and a SpringPlus surface. */
public final class ModerationTextField extends EditBox {
 public ModerationTextField(Font font,int width,int height,Component label){super(font,Math.max(1,width-12),height,label);setBordered(false);setTextColor(ModerationTheme.TEXT.getRGB());}
 @Override public void setRectangle(int width,int height,int x,int y){super.setRectangle(Math.max(1,width-12),height,x+6,y);}
 @Override public boolean isMouseOver(double x,double y){return x>=getX()-6&&x<getX()+getWidth()+6&&y>=getY()&&y<getY()+getHeight();}
 @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
  ModerationSurfaces.control(g,getX()-6,getY(),getWidth()+12,getHeight(),active,isFocused(),true);
  // Borderless EditBox draws its text at getY(), not at the vertical centre.
  // Move only its drawing, leaving the actual hitbox and caret X calculation intact.
  g.pose().pushMatrix();
  try{g.pose().translate(0,(getHeight()-8)/2f);super.extractWidgetRenderState(g,mx,my,delta);}
  finally{g.pose().popMatrix();}
 }
}
