package dev.aeltorx.inflow.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Brief local-only intro. Permissions remain owned by ModerationService. */
public final class InFlowWelcomeScreen extends FixedModerationScreen {
    @Override protected boolean ownsWindowChrome(){return true;}
 private final ModerationPanelScreen next;
 private final Screen parent;
 private final Object connection;
 private long start=System.nanoTime(),artGeneration;
 private final int variant=ModerationLoadingArt.nextVariant();
 private final Identifier textureId=Identifier.fromNamespaceAndPath("inflow_moderation","loading/"+java.util.UUID.randomUUID());
 private int textureWidth,textureHeight;private boolean uploaded,finished;
 private InFlowWelcomeScreen(ModerationPanelScreen next){super(Component.literal("Модерация"));this.next=next;var mc=net.minecraft.client.Minecraft.getInstance();connection=mc.getConnection();parent=mc.gui.screen();}
 public static void reset(){} // Existing disconnect hook.
 public static boolean intercept(ModerationPanelScreen next){var mc=net.minecraft.client.Minecraft.getInstance();if(!ModerationService.allowed()||mc.player==null)return false;mc.gui.setScreen(new InFlowWelcomeScreen(next));return true;}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float partial){}
 @Override protected void initMenu(){
  release();var b=ModerationWindowBounds.of(width,height);long generation=++artGeneration;
  ModerationRaster.paint(()->ModerationLoadingArt.paint(b.width(),b.height(),variant),image->{if(finished||generation!=artGeneration)return;if(image==null){finish();return;}textureWidth=image.getWidth();textureHeight=image.getHeight();
   var texture=new DynamicTexture(()->"Moderation meme loading",new NativeImage(textureWidth,textureHeight,false));
   minecraft.getTextureManager().register(textureId,texture);ModerationRaster.upload(texture,image);uploaded=true;start=System.nanoTime();
  });
 }
 private void finish(){if(finished)return;finished=true;if(connection!=minecraft.getConnection()||!ModerationService.allowed()){minecraft.gui.setScreen(null);return;}next.revealAfterWelcome();minecraft.gui.setScreen(next);}
 @Override public void tick(){if(connection!=minecraft.getConnection()||!ModerationService.allowed()){finished=true;minecraft.gui.setScreen(null);return;}if(uploaded&&ModerationLoadingArt.progress(System.nanoTime()-start)>=1)finish();}
 @Override public void onClose(){finished=true;minecraft.gui.setScreen(parent);}
 @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){if(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_RETURN||event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER||event.key()==32){finish();return true;}return super.keyPressed(event);}
 @Override protected boolean clickMenu(net.minecraft.client.input.MouseButtonEvent event,boolean twice){var b=ModerationWindowBounds.of(width,height);if(System.nanoTime()-start>150_000_000L&&event.x()>=b.x()&&event.x()<b.x()+b.width()&&event.y()>=b.y()&&event.y()<b.y()+b.height())finish();return true;}
 @Override protected void drawMenu(GuiGraphicsExtractor g,int mx,int my,float partial){
  if(!uploaded)return;var b=ModerationWindowBounds.of(width,height);double progress=ModerationLoadingArt.progress(System.nanoTime()-start);
  g.blit(RenderPipelines.GUI_TEXTURED,textureId,b.x(),b.y(),0,0,b.width(),b.height(),textureWidth,textureHeight,textureWidth,textureHeight);
  int x=b.x()+18,y=b.y()+b.height()-18,w=Math.max(1,b.width()-36);g.fill(x,y,x+w,y+3,ModerationTheme.BORDER.getRGB());g.fill(x,y,x+(int)(w*(1-Math.pow(1-progress,2))),y+3,ModerationTheme.ACCENT.getRGB());
 }
 private void release(){if(uploaded){minecraft.getTextureManager().release(textureId);uploaded=false;}}
 @Override public void removed(){artGeneration++;release();}
}
