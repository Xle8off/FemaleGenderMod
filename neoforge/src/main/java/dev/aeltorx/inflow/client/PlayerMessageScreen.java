package dev.aeltorx.inflow.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.google.gson.JsonObject;
public final class PlayerMessageScreen extends FixedModerationScreen {
    private final Screen parent;private final ModerationService.Target target;private final Object connection;
    private EditBox text;private int template;private String draft,status="",confirmed="";private boolean busy;
    public PlayerMessageScreen(Screen parent,ModerationService.Target target){super(Component.literal("Сообщение игроку"));this.parent=parent;this.target=target;connection=net.minecraft.client.Minecraft.getInstance().getConnection();draft=MessageTemplates.get(0);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void resize(int width,int height){if(text!=null)draft=text.getValue();confirmed="";super.resize(width,height);}
    private void rebuild(){clearWidgets();initMenu();}
    @Override protected void initMenu(){int w=Math.min(440,width-24),x=(width-w)/2;
        addRenderableWidget(ModerationButtons.builder(Component.literal("Шаблон: "+MessageTemplates.TITLES.get(template)),b->{template=(template+1)%4;draft=MessageTemplates.get(template);confirmed="";rebuild();}).bounds(x,36,w,20).build());
        text=addRenderableWidget(new ModerationTextField(font,w,20,Component.literal("Текст сообщения")));text.setRectangle(w,20,x,65);text.setMaxLength(500);text.setValue(draft);
        addRenderableWidget(ModerationButtons.builder(Component.literal("Сохранить шаблон"),b->{try{draft=text.getValue();MessageTemplates.save(template,draft);status="Шаблон сохранён локально";}catch(Exception e){status=e instanceof IllegalArgumentException?e.getMessage():"Не удалось сохранить шаблон";}}).bounds(x,height-63,w/2-3,20).build());
        addRenderableWidget(ModerationButtons.builder(Component.literal("Отправить / подтвердить"),b->send()).bounds(x+w/2,height-63,w/2,20).build());
        addRenderableWidget(ModerationButtons.builder(Component.literal("Назад"),b->onClose()).bounds(x,height-37,w,20).build());setInitialFocus(text);
    }
    private String preview(){return MessageTemplates.expand(text.getValue(),target.name(),minecraft.player.getGameProfile().name());}
    private void send(){if(busy)return;if(!ModerationService.allowed()||connection!=minecraft.getConnection()){status="Нет доступа или сменился сервер";return;}try{String value=preview();draft=text.getValue();if(!confirmed.equals(value)){confirmed=value;status="Проверь адресата и текст. Нажми отправку ещё раз";return;}if(!ModerationWorkspace.summary.has("staffMessages")){status="Нужен серверный плагин 1.0.15";return;}var q=new JsonObject();q.addProperty("target",target.id().toString());q.addProperty("text",value);busy=true;confirmed="";ModerationWorkspace.request("staff-message",q,r->{busy=false;status=r.get("message").getAsString();});}catch(IllegalArgumentException e){status=e.getMessage();}}
    @Override public void tick(){if(!ModerationService.allowed()||connection!=minecraft.getConnection())minecraft.gui.setScreen(null);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override protected void drawMenu(GuiGraphicsExtractor g,int mx,int my,float dt){g.centeredText(font,"Личное сообщение → "+target.name(),width/2,15,0xFFFFFFFF);g.text(font,"Предпросмотр · {player}, {moderator}",12,97,0xFFAAC7ED,false);try{var lines=font.split(Component.literal(preview()),width-24);for(int i=0;i<Math.min(lines.size(),Math.max(1,(height-200)/12));i++)g.text(font,lines.get(i),12,112+i*12,0xFFE5EEF9,false);}catch(Exception ignored){}g.text(font,busy?"Отправка…":status,12,height-81,0xFFFFC17E,false);renderWidgets(g,mx,my,dt);}
}
