package com.wildfire.client.music;

import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Code-native pixel icons; hidden buttons never claim hover or keyboard focus. */
public final class UpdateHudButton extends ChatOverlayButton {
    private final IntSupplier status;
    public UpdateHudButton(IntSupplier status,Runnable action) {
        super(10,38,20,20,Component.literal("Обновление мода"),button -> action.run());
        this.status=status;
        visible=false;
    }
    public void sync(int y,boolean permitted) {
        int state=status.getAsInt();
        setY(y);visible=permitted && state!=0;active=state==2 || state==3;
        setTooltip(Tooltip.create(Component.literal(state==2 ? "Обновление готово. Нажмите, чтобы закрыть игру и установить"
            : state==3 ? "Ошибка обновления. Нажмите, чтобы скачать вашу сборку в браузере"
            : "Обновление загружается…")));
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta) {
        com.wildfire.gui.FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,com.wildfire.gui.UiTheme.ACCENT);drawIcon(g,getX(),getY(),status.getAsInt());
    }
    public static void drawIcon(GuiGraphicsExtractor g,int x,int y,int state) {
        int color=state==3 ? 0xFFFF5264 : 0xFF59B6FF;
        // Down arrow and download tray, inset into the same 20px tile as the bell.
        g.fill(x+9,y+3,x+12,y+11,color);
        for(int row=0;row<4;row++)g.fill(x+5+row,y+9+row,x+16-row,y+10+row,color);
        g.fill(x+4,y+15,x+17,y+17,color);
        g.fill(x+4,y+13,x+6,y+16,color);g.fill(x+15,y+13,x+17,y+16,color);
        if(state==3) {
            g.fill(x+16,y+2,x+18,y+7,color);
            g.fill(x+16,y+8,x+18,y+10,color);
        }
    }
}
