package com.wildfire.client.music;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;

/** Mouse-only chat overlay: hidden buttons must not intercept hit-testing or arrow navigation. */
public class ChatOverlayButton extends Button {
    public ChatOverlayButton(int x,int y,int width,int height,Component label,OnPress action) {
        super(x,y,width,height,label,action,DEFAULT_NARRATION);
    }
    @Override public boolean isMouseOver(double x,double y) {return visible && active && super.isMouseOver(x,y);}
    @Override public ComponentPath nextFocusPath(FocusNavigationEvent event) {return null;}
    @Override public boolean shouldTakeFocusAfterInteraction() {return false;}
    @Override protected void extractContents(net.minecraft.client.gui.GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        com.wildfire.gui.FlatButtons.plate(graphics,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,com.wildfire.gui.UiTheme.ACCENT);
        var font=net.minecraft.client.Minecraft.getInstance().font;
        graphics.text(font,getMessage(),getX()+(getWidth()-font.width(getMessage()))/2,
            getY()+(getHeight()-8)/2,active?0xFFE8EDF3:0xFF929AA6,false);
    }
}
