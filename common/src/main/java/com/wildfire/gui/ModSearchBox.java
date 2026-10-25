package com.wildfire.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Shared search surface for reaction pickers; native editing without vanilla framing. */
public final class ModSearchBox extends EditBox {
    public ModSearchBox(Font font,int x,int y,int width,int height,Component label){
        super(font,x,y,width,height,label);
        setBordered(false);setTextColor(UiTheme.TEXT);setTextColorUneditable(UiTheme.MUTED);
    }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        int x=getX(),y=getY(),width=getWidth(),height=getHeight();
        FlatButtons.plate(g,x,y,width,height,true,isFocused(),false,UiTheme.ACCENT);
        setX(x+6);setY(y+(height-9)/2);setWidth(Math.max(1,width-12));
        try{super.extractWidgetRenderState(g,mx,my,delta);}
        finally{setX(x);setY(y);setWidth(width);}
    }
}
