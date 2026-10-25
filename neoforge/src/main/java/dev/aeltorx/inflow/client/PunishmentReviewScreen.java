package dev.aeltorx.inflow.client;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** A single-use server plan. No raw command can be edited after preflight. */
public final class PunishmentReviewScreen extends FixedModerationScreen {
    private final ModerationService.Target target;private final List<String> commands;private final LocalModerationAnimation.Style style;private final String token;
    private boolean sent;private String status="Сервер проверил конфликт. Подтверди выдачу";
    public PunishmentReviewScreen(ModerationService.Target target,List<String> commands,LocalModerationAnimation.Style style,String token){super(Component.literal("Подтверждение наказания"));this.target=target;this.commands=List.copyOf(commands);this.style=style;this.token=token;}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void initMenu(){int w=Math.min(360,width-24),x=(width-w)/2,y=height-55;
        addRenderableWidget(ModerationButtons.builder(Component.literal("Выдать"),b->{if(sent)return;sent=true;b.active=false;var q=new JsonObject();q.addProperty("target",target.id().toString());q.addProperty("token",token);ModerationWorkspace.request("confirm",q,r->{String kind=r.get("kind").getAsString();status=r.get("message").getAsString();ModerationService.notice(status);if(kind.equals("scene")||kind.equals("pilot")){ModerationService.remotelyStarted(target,style);if(kind.equals("scene"))onClose();}else if(kind.equals("result"))onClose();});}).bounds(x,y,w/2-4,20).build());
        addRenderableWidget(ModerationButtons.builder(Component.literal("Отмена"),b->onClose()).bounds(x+w/2,y,w/2,20).build());}
    @Override public void onClose(){minecraft.gui.setScreen(null);}
    @Override protected void drawMenu(GuiGraphicsExtractor g,int mx,int my,float delta){int x=12,y=30;g.centeredText(font,target.name(),width/2,y,0xFFE5EEF9);y+=20;
        g.text(font,"UUID: "+target.id(),x,y,0xFFA4B5CB,false);y+=20;for(String c:commands){g.text(font,font.plainSubstrByWidth("/"+c,width-24),x,y,0xFFE5EEF9,false);y+=14;}
        g.text(font,"Эффект: "+style.label,x,y+7,0xFF8EC9FF,false);g.text(font,"Команды раздельные; 3-й варн может вызвать автобан",x,y+25,0xFFFFC17E,false);g.text(font,font.plainSubstrByWidth(status,width-24),x,height-25,0xFF8EC9FF,false);renderWidgets(g,mx,my,delta);}
}
