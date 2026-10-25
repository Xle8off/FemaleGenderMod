package dev.aeltorx.inflow.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Fixed-size dialogs: only the whole canvas scales, never individual controls or rows. */
public abstract class FixedModerationScreen extends Screen {
    private ModerationViewport viewport=ModerationViewport.of(ModerationViewport.WIDTH,ModerationViewport.HEIGHT);
    private long appearanceStarted;
    protected FixedModerationScreen(Component title){super(title);}
    @Override protected final void init(){
        viewport=ModerationViewport.of(width,height);
        width=ModerationViewport.WIDTH;height=ModerationViewport.HEIGHT;
        appearanceStarted=System.nanoTime();
        initMenu();
    }
    protected void initMenu(){}
    @Override public void resize(int width,int height){
        // Logical controls do not move or reinitialize: keep drafts, focus, caret and selected pages.
        viewport=ModerationViewport.of(width,height);
    }
    protected abstract void drawMenu(GuiGraphicsExtractor g,int mx,int my,float delta);
    protected boolean ownsWindowChrome(){return false;}
    protected boolean clipsWindow(){return true;}
    protected final int screenWidth(){return (int)Math.round(viewport.x()*2+width*viewport.scale());}
    protected final int screenHeight(){return (int)Math.round(viewport.y()*2+height*viewport.scale());}
    protected final ModerationWindowBounds.Rect localRect(ModerationWindowBounds.Rect r){return new ModerationWindowBounds.Rect((int)Math.round(viewport.localX(r.x())),(int)Math.round(viewport.localY(r.y())),(int)Math.round(r.width()/viewport.scale()),(int)Math.round(r.height()/viewport.scale()));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){}
    @Override public final void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        var v=viewport;
        g.fill(0,0,(int)Math.ceil(v.x()*2+width*v.scale()),(int)Math.ceil(v.y()*2+height*v.scale()),0x66050912);
        g.pose().pushMatrix();
        g.pose().translate((float)v.x(),(float)v.y());g.pose().scale((float)v.scale(),(float)v.scale());
        long elapsed=System.nanoTime()-appearanceStarted;
        float opening=(float)(1-Math.pow(1-Math.clamp(elapsed/220_000_000.0,0,1),3));
        g.pose().translate(0,12*(1-opening));
        long oldFrame=ModerationButtons.frame(elapsed);
        try{
            var b=ModerationWindowBounds.of(width,height);
            if(!ownsWindowChrome())ModerationSurfaces.panel(g,b.x(),b.y(),b.width(),b.height());
            boolean clip=clipsWindow();if(clip)g.enableScissor(b.x(),b.y(),b.x()+b.width(),b.y()+b.height());
            try{drawMenu(g,(int)Math.floor(v.localX(mx)),(int)Math.floor(v.localY(my)),delta);}finally{if(clip)g.disableScissor();}
        }finally{ModerationButtons.frame(oldFrame);g.pose().popMatrix();}
    }
    protected final void renderWidgets(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);}
    protected final void menuTooltip(GuiGraphicsExtractor g,Component text,int x,int y){g.setTooltipForNextFrame(text,(int)(viewport.x()+x*viewport.scale()),(int)(viewport.y()+y*viewport.scale()));}
    private MouseButtonEvent local(MouseButtonEvent e){return new MouseButtonEvent(viewport.localX(e.x()),viewport.localY(e.y()),e.buttonInfo());}
    @Override public final boolean mouseClicked(MouseButtonEvent e,boolean twice){if(System.nanoTime()-appearanceStarted<240_000_000L)return true;return viewport.contains(e.x(),e.y())&&clickMenu(local(e),twice);}
    protected boolean clickMenu(MouseButtonEvent e,boolean twice){return super.mouseClicked(e,twice);}
    @Override public final boolean mouseReleased(MouseButtonEvent e){return super.mouseReleased(local(e));}
    @Override public final boolean mouseDragged(MouseButtonEvent e,double dx,double dy){return super.mouseDragged(local(e),dx/viewport.scale(),dy/viewport.scale());}
    @Override public final void mouseMoved(double x,double y){super.mouseMoved(viewport.localX(x),viewport.localY(y));}
    @Override public final boolean mouseScrolled(double x,double y,double horizontal,double vertical){return viewport.contains(x,y)&&scrollMenu(viewport.localX(x),viewport.localY(y),horizontal,vertical);}
    protected boolean scrollMenu(double x,double y,double horizontal,double vertical){return super.mouseScrolled(x,y,horizontal,vertical);}
}
