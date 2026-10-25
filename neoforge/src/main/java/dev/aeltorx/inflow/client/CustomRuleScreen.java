package dev.aeltorx.inflow.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local presets only; server permissions are never changed. */
public final class CustomRuleScreen extends FixedModerationScreen {
    private final Screen parent;private EditBox number,title;private int section=2;private String status="";
    public CustomRuleScreen(Screen parent){super(Component.literal("Свои правила"));this.parent=parent;}
    @Override public boolean isPauseScreen(){return false;}
    private String types(){return section==1?"Варн + мут":section==2?"Варн + бан":"Только бан";}
    @Override protected void initMenu(){
        int x=width/2-140,y=height/2-40;
        number=addRenderableWidget(new ModerationTextField(font,280,20,Component.literal("Номер")));number.setRectangle(280,20,x,y);number.setMaxLength(7);number.setValue("9.1");
        title=addRenderableWidget(new ModerationTextField(font,280,20,Component.literal("Описание")));title.setRectangle(280,20,x,y+38);title.setMaxLength(80);
        addRenderableWidget(ModerationButtons.builder(Component.literal(types()),b->{section=section==1?2:section==2?5:1;b.setMessage(Component.literal(types()));}).bounds(x,y+66,280,20).build());
        addRenderableWidget(ModerationButtons.builder(Component.literal("Сохранить"),b->save(false)).bounds(x,y+92,135,20).build());
        addRenderableWidget(ModerationButtons.builder(Component.literal("Удалить по номеру"),b->save(true)).bounds(x+145,y+92,135,20).build());
        addRenderableWidget(ModerationButtons.builder(Component.literal("Назад"),b->onClose()).bounds(x,y+118,280,20).build());setInitialFocus(number);
    }
    private void save(boolean remove){
        if(!ModerationService.allowed()){status="Требуются OP-права";return;}
        try{ModerationRules.save(new ModerationRules.Rule(number.getValue().trim(),remove?"Удалить":title.getValue().trim(),section),remove);status=remove?"Удалено":"Сохранено — доступно в колесе";}
        catch(Exception e){status=e instanceof IllegalArgumentException?e.getMessage():"Не удалось сохранить файл настроек";}
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override protected void drawMenu(GuiGraphicsExtractor g,int mx,int my,float delta){
        int y=height/2-40;
        g.centeredText(font,"Свои правила и наказания",width/2,y-48,0xFFFFFFFF);
        g.centeredText(font,"Номер правила (например 9.1)",width/2,y-14,0xFFB8C8E8);
        g.centeredText(font,"Описание",width/2,y+24,0xFFB8C8E8);
        g.centeredText(font,status,width/2,y+150,0xFFFFCBA0);renderWidgets(g,mx,my,delta);
    }
}
