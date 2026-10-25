package dev.aeltorx.inflow.client;

import dev.aeltorx.inflow.common.PunishmentDuration;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

public final class CustomDurationScreen extends FixedModerationScreen {
    private final Screen parent;private final Consumer<String> accept;private EditBox input;private String error="";
    public CustomDurationScreen(Screen parent,Consumer<String> accept){super(Component.literal("Свой срок"));this.parent=parent;this.accept=accept;}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void initMenu(){
        input=addRenderableWidget(new ModerationTextField(font,220,20,Component.literal("Срок")));
        input.setRectangle(220,20,width/2-110,height/2-10);input.setMaxLength(12);input.setValue("10m");
        addRenderableWidget(ModerationButtons.builder(Component.literal("Применить"),b->apply()).bounds(width/2-110,height/2+22,105,20).build());
        addRenderableWidget(ModerationButtons.builder(Component.literal("Назад"),b->onClose()).bounds(width/2+5,height/2+22,105,20).build());
        setInitialFocus(input);
    }
    private void apply(){String value=input.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        if(!PunishmentDuration.valid(value,false)){error="Формат: 10m, 2h, 7d; от 1 секунды до 365 дней";return;}
        accept.accept(value);minecraft.gui.setScreen(parent);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean keyPressed(KeyEvent event){if(event.key()==257||event.key()==335){apply();return true;}return super.keyPressed(event);}
    @Override protected void drawMenu(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.centeredText(font,"Свой срок наказания",width/2,height/2-60,0xFFFFFFFF);
        g.centeredText(font,"s — секунды · m — минуты · h — часы · d — дни",width/2,height/2-40,0xFFADBDDD);
        g.centeredText(font,error,width/2,height/2+55,0xFFFFAC9A);renderWidgets(g,mx,my,delta);
    }
}
