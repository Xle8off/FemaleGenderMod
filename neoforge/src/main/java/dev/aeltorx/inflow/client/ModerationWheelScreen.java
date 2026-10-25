package dev.aeltorx.inflow.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.texture.DynamicTexture;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Mouse-driven radial workflow. Nothing is sent until the explicit confirmation click. */
public final class ModerationWheelScreen extends FixedModerationScreen {
    @Override protected boolean ownsWindowChrome(){return true;}
    private enum Stage { ACTION, PLAYERS, RULES, TYPES, MUTE_TIME, BAN_TIME, STYLE, CONFIRM }
    private record Choice(String label, Runnable select, boolean enabled,WheelIcons.Kind icon,String person) {
        Choice(String label,Runnable select,boolean enabled,WheelIcons.Kind icon){this(label,select,enabled,icon,"");}
        Choice(String label,Runnable select){this(label,select,true,WheelIcons.forLabel(label));}
        Choice(String label,Runnable select,boolean enabled){this(label,select,enabled,WheelIcons.forLabel(label));}
    }
    private Stage stage = Stage.ACTION;
    private int page;
    private String action = "punish", message = "";
    private ModerationService.Target target;
    private ModerationRules.Rule rule;
    private boolean warn, mute, ban;
    private String muteTime = "30m", banTime = "1d";
    private LocalModerationAnimation.Style style = LocalModerationAnimation.Style.NONE;
    private DynamicTexture texture;
    private net.minecraft.resources.Identifier textureId=newTextureId();
    private net.minecraft.resources.Identifier outgoingId;
    private long pageStarted;
    private int pageDirection;
    private static final long PAGE_DURATION=360_000_000L;
    private static net.minecraft.resources.Identifier newTextureId(){return net.minecraft.resources.Identifier.fromNamespaceAndPath("inflow_moderation","wheel/"+java.util.UUID.randomUUID());}
    private void finishPageTurn(){if(outgoingId!=null)minecraft.getTextureManager().release(outgoingId);if(pageStarted!=0)revealStarted=System.nanoTime()-WheelReveal.TOTAL;outgoingId=null;pageStarted=0;}
    private long revealStarted=System.nanoTime();
    private long closingStarted,closingFrom;
    @Override protected void initMenu(){disposed=false;closingStarted=0;restartReveal();}
    private String previous = "";
    private List<Choice> choices = List.of();
    private List<Choice> outgoingChoices=List.of();
    private boolean rasterPending,disposed;
    private long rasterGeneration;
    private int hover = -1;
    private int lastHover=-1;
    private long lastHoverSound;
    private static final int PER_PAGE = WheelPagination.ITEMS;
    private int summaryScroll;
    private final ModerationService.Target aimed;
    public ModerationWheelScreen() { super(Component.literal("Модерация — InFlow"));aimed=ModerationService.crosshairTarget(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        if(closingStarted!=0){if(System.nanoTime()-closingStarted>=closingFrom)minecraft.gui.setScreen(null);return;}
        if (!ModerationService.allowed()) { onClose(); return; }
        if (target != null && !action.equals("preview") && !ModerationService.online(target)) {
            message = "Игрок вышел — выбери другого"; target = null; move(Stage.PLAYERS);
        }
    }
    @Override public void onClose() {
        if(closingStarted!=0)return;
        closingStarted=System.nanoTime();closingFrom=Math.clamp(closingStarted-revealStarted,0,WheelReveal.TOTAL);
    }
    @Override public void removed() { disposed=true;rasterGeneration++;finishPageTurn();if (texture != null) minecraft.getTextureManager().release(textureId); texture = null;previous=""; }
    private void restartReveal(){rasterGeneration++;revealStarted=System.nanoTime();choices=List.of();previous="";hover=lastHover=-1;}
    private void changePage(int next){if(next==page||pageStarted!=0||rasterPending)return;pageDirection=Integer.compare(next,page);if(texture!=null){outgoingChoices=choices;outgoingId=textureId;texture=null;textureId=newTextureId();pageStarted=System.nanoTime();}page=next;restartReveal();}
    private void move(Stage next) { finishPageTurn();stage = next; page = 0; summaryScroll=0;restartReveal();play("ui.open",.22f,1.1f); }
    private void play(String path,float volume,float pitch) {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
            net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.fromNamespaceAndPath("inflow_moderation",path)),pitch,volume));
    }
    private void back() {
        message = "";
        switch(stage) {
            case ACTION -> onClose();
            case PLAYERS -> move(Stage.ACTION);
            case RULES -> move(Stage.PLAYERS);
            case TYPES -> move(Stage.RULES);
            case MUTE_TIME, BAN_TIME -> move(Stage.TYPES);
            case STYLE -> move(action.equals("punish") ? Stage.TYPES : Stage.PLAYERS);
            case CONFIRM -> move(action.equals("punish")?(ban?Stage.STYLE:Stage.TYPES):Stage.PLAYERS);
        }
    }
    private List<Choice> allChoices() {
        List<Choice> result = new ArrayList<>();
        switch(stage) {
            case ACTION -> {
                for(var a:ModerationFavorites.ordered())result.add(new Choice(a.label,()->perform(a),true,WheelIcons.Kind.valueOf(a.name())));
                result.add(new Choice("Настроить избранное",()->minecraft.gui.setScreen(new ModerationFavoritesScreen(this))));
            }
            case PLAYERS -> {
                if(action.equals("preview"))result.add(new Choice("Рядом со мной",()->{
                    target=new ModerationService.Target(minecraft.player.getUUID(),minecraft.player.getGameProfile().name());move(Stage.STYLE);
                },true,null,minecraft.player.getGameProfile().name()));
                for (var player : ModerationService.players()) result.add(new Choice(player.name(), () -> {
                    target=player; message="";
                    if(action.equals("card")){var panel=new ModerationPanelScreen(this);panel.select(player);ModerationWorkspace.present(panel);return;}
                    move(action.equals("punish") ? Stage.RULES : action.equals("preview")?Stage.STYLE:Stage.CONFIRM);
                },true,null,player.name()));
            }
            case RULES -> {
                for (var r : ModerationRules.ALL.stream().sorted(java.util.Comparator.comparingInt(r->r.section()==2?0:r.section()==1?1:2)).toList()) result.add(new Choice(r.number()+" "+r.title(), () -> {
                    rule=r; warn=r.section()!=5; mute=false; ban=r.section()==5; message=""; move(Stage.TYPES);
                }));
            }
            case TYPES -> {
                if (rule.section()!=5) result.add(new Choice("Варн: "+(warn?"вкл":"выкл"), () -> warn=!warn));
                if (rule.section()==1) {
                    result.add(new Choice("Мут: "+(mute?"вкл":"выкл"), () -> mute=!mute));
                    result.add(new Choice("Срок мута: "+timeLabel(muteTime), () -> move(Stage.MUTE_TIME)));
                }
                if (rule.section()!=1) {
                    result.add(new Choice("Бан: "+(ban?"вкл":"выкл"), () -> ban=!ban));
                    result.add(new Choice("Срок бана: "+timeLabel(banTime), () -> move(Stage.BAN_TIME)));
                }
            }
            case MUTE_TIME -> {
                for(String time:ModerationRules.SHORT_TIMES) result.add(new Choice(timeLabel(time), () -> {
                    muteTime=time; mute=true; move(Stage.TYPES);
                }));
                result.add(new Choice("Свой срок…",()-> {
                    minecraft.gui.setScreen(new CustomDurationScreen(this,value->{muteTime=value;mute=true;move(Stage.TYPES);}));
                }));
            }
            case BAN_TIME -> {
                for(String time:ModerationRules.BAN_TIMES) result.add(new Choice(timeLabel(time), () -> {
                    banTime=time; ban=true; move(Stage.TYPES);
                }));
                result.add(new Choice("Свой срок…",()-> {
                    minecraft.gui.setScreen(new CustomDurationScreen(this,value->{banTime=value;ban=true;move(Stage.TYPES);}));
                }));
            }
            case STYLE -> {
                for(var s:LocalModerationAnimation.Style.values()) result.add(new Choice(s.label, () -> {
                    style=s; message=""; move(Stage.CONFIRM);
                },true,WheelIcons.Kind.PREVIEW));
            }
            case CONFIRM -> {
                result.add(new Choice(action.equals("preview") ? "Только просмотр" : "Подтвердить", this::submit,true,action.equals("preview")?WheelIcons.Kind.PREVIEW:WheelIcons.Kind.CONFIRM));
            }
        }
        return result;
    }
    private void perform(ModerationFavorites.Action selected){
        if(!ModerationService.allowed()){ModerationService.notice("У вас нет доступа");return;}
        switch(selected){
            case PANEL->ModerationWorkspace.open(this);
            case MODERATORS->minecraft.gui.setScreen(new StaffProfilesScreen(this,false));
            case CROSSHAIR->{
                if(aimed==null||!ModerationService.online(aimed)){message="Наведи прицел на видимого игрока перед открытием колеса";return;}
                var panel=new ModerationPanelScreen(this);panel.select(aimed);ModerationWorkspace.present(panel);
            }
            case REPORTS->{var panel=new ModerationPanelScreen(this);panel.showReports();ModerationWorkspace.present(panel);}
            case CARD->{action="card";target=null;move(Stage.PLAYERS);}
            case INSPECT->ModerationWorkspace.request("co-inspect",new com.google.gson.JsonObject(),r->ModerationService.notice(r.get("message").getAsString()));
            case TELEPORT->{action="teleport";target=null;rule=null;style=LocalModerationAnimation.Style.NONE;move(Stage.PLAYERS);}
            case CUSTOM_RULES->minecraft.gui.setScreen(new CustomRuleScreen(this));
            case PUNISH->{action="punish";target=null;rule=null;move(Stage.PLAYERS);}
            case CHECK->{action="check";target=null;move(Stage.PLAYERS);}
            case PASS->{action="pass";target=null;move(Stage.PLAYERS);}
            case HISTORY->{action="history";target=null;move(Stage.PLAYERS);}
            case PREVIEW->{action="preview";target=null;rule=null;warn=false;mute=false;ban=false;move(Stage.PLAYERS);}
        }
    }
    private void advanceTypes(){
        if(!warn&&!mute&&!ban){message="Выбери хотя бы одно наказание";return;}
        message="";if(ban)move(Stage.STYLE);else{style=LocalModerationAnimation.Style.NONE;move(Stage.CONFIRM);}
    }
    private void submit() {
        if(action.equals("preview")) {
            if(!ModerationService.allowed()){message="Требуются OP-права";return;}
            if(LocalModerationAnimation.start(target,style)||style==LocalModerationAnimation.Style.NONE)onClose();
            else message="Игрок вне загруженной области";
            return;
        }
        if (!ModerationService.allowed() || !ModerationService.online(target)) { message="Нет прав или игрок вышел"; return; }
        var commands = ModerationService.commands(target,rule,warn,mute,ban,muteTime,banTime,action);
        if (ModerationService.submit(target,commands,style)) {if(minecraft.gui.screen()==this)onClose();}
        else message="Не отправлено — смотри сообщение в чате";
    }
    private static String timeLabel(String t) {
        return switch(t) { case "1m" -> "1 минута"; case "30m" -> "30 минут"; case "1h" -> "1 час"; case "4h" -> "4 часа";
            case "1d" -> "1 день"; case "16d" -> "16 дней"; case "30d" -> "30 дней";
            case "permanent" -> "Навсегда"; default -> t; };
    }
    private String stageTitle() {
        return switch(stage) { case ACTION -> "Модерация"; case PLAYERS -> "Онлайн-игроки";
            case RULES -> "Правило"; case TYPES -> "Типы наказаний"; case MUTE_TIME -> "Длительность мута";
            case BAN_TIME -> "Длительность бана"; case STYLE -> "Локальная анимация"; case CONFIRM -> "Подтверждение"; };
    }
    private int hit(double x,double y) {
        if(closingStarted!=0||pageStarted!=0||rasterPending||texture==null)return -1;
        var b=ModerationWindowBounds.of(width,height);
        int index=WheelGridLayout.hit(x-b.x(),y-b.y());
        return index>=0&&index<choices.size()&&WheelReveal.at(WheelGridLayout.revealOrder(index),System.nanoTime()-revealStarted)>.95f&&choices.get(index).enabled()&&!choices.get(index).label().isBlank()?index:-1;
    }
    @Override protected void drawMenu(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        if(pageStarted!=0&&!rasterPending&&texture!=null&&System.nanoTime()-pageStarted>=PAGE_DURATION)finishPageTurn();
        var all=allChoices();var paging=WheelPagination.of(all.size(),page);int pages=paging.pages();
        if(page!=paging.page())changePage(paging.page());
        var visible=new ArrayList<>(all.subList(paging.start(),paging.end()));
        while(visible.size()<WheelGridLayout.ACTIONS)visible.add(new Choice("",()->{},false));
        visible.add(new Choice("Назад",()->{if(page>0)changePage(page-1);else back();},page>0||stage!=Stage.ACTION));
        visible.add(new Choice("Вперёд",()->{if(page+1<pages)changePage(page+1);else advanceTypes();},page+1<pages||stage==Stage.TYPES));
        visible.add(new Choice("×",this::onClose));
        choices=List.copyOf(visible);
        hover=hit(mouseX,mouseY);
        if(hover!=lastHover) {
            if(hover>=0 && System.nanoTime()-lastHoverSound>70_000_000L){play("ui.click",.1f,1.35f);lastHoverSound=System.nanoTime();}
            lastHover=hover;
        }
        List<String> labels=choices.stream().map(Choice::label).toList();
        var details=summary().stream().flatMap(line->StaffProfilePainter.lines(line,WheelGridLayout.SUMMARY_CHARS).stream()).toList();
        summaryScroll=Math.clamp(summaryScroll,0,Math.max(0,details.size()-WheelGridLayout.SUMMARY_LINES));
        var summary=details.subList(summaryScroll,Math.min(details.size(),summaryScroll+WheelGridLayout.SUMMARY_LINES));
        String model=stage+"|"+labels+"|"+page+"|"+summary+"|"+message+choices.stream().map(c->c.enabled()+":"+c.icon()).toList();
        if(!model.equals(previous)&&!rasterPending) {
            rasterPending=true;long generation=rasterGeneration;
            String title=stageTitle(),selected=target==null?"":target.name(),notice=message;boolean confirm=stage==Stage.CONFIRM;int selectedPage=page;
            var artLabels=choices.stream().map(c->c.person().isBlank()?c.label():"").toList();var icons=choices.stream().map(Choice::icon).toList();var flags=choices.stream().map(Choice::enabled).toList();var detail=List.copyOf(summary);
            ModerationRaster.paint(()->WheelPainter.atlas(WheelPainter.paint(title,artLabels,-1,confirm,detail,selected,notice,selectedPage,pages,icons,flags)),image->{
                rasterPending=false;if(disposed||generation!=rasterGeneration||image==null)return;
                if(texture==null){texture=new DynamicTexture(()->"InFlow moderation wheel",new NativeImage(image.getWidth(),image.getHeight(),false));minecraft.getTextureManager().register(textureId,texture);}
                ModerationRaster.upload(texture,image);previous=model;revealStarted=System.nanoTime();if(pageStarted!=0)pageStarted=System.nanoTime();
            });
        }
        if(texture==null)return;
        var b=ModerationWindowBounds.of(width,height);
        // Draw static regions once, then reveal cropped atlas cells on the GPU in clockwise order.
        long elapsed=closingStarted==0?System.nanoTime()-revealStarted:WheelReveal.reverseTime(closingFrom,System.nanoTime()-closingStarted);
        float chrome=closingStarted==0?1:(float)Math.clamp(elapsed/(double)Math.max(1,closingFrom),0,1);
        blitPart(g,b,0,0,560,50,chrome);blitPiece(g,b,0,350,0,WheelGridLayout.ATLAS_FOOTER,560,90,chrome);
        blitSector(g,b,8,chrome);
        if(pageStarted!=0&&closingStarted==0){
            float t=(float)Math.clamp((System.nanoTime()-pageStarted)/(double)PAGE_DURATION,0,1);
            float eased=t*t*(3-2*t);
            spinPage(g,b,outgoingId,pageDirection*90*eased,1-eased);
            spinPage(g,b,textureId,-pageDirection*90*(1-eased),eased);
            blitSector(g,b,7,1);
        }else {for(int slot=0;slot<8;slot++)blitSector(g,b,slot,WheelReveal.at(WheelGridLayout.revealOrder(slot<7?slot:WheelGridLayout.BACK),elapsed));drawPeople(g,b,choices,1);}
        if(hover>=0&&pageStarted==0)drawHover(g,b,hover);
    }
    private void spinPage(GuiGraphicsExtractor g,ModerationWindowBounds b,net.minecraft.resources.Identifier id,float degrees,float alpha){
        g.pose().pushMatrix();
        try{g.pose().translate(b.x()+280,b.y()+200);g.pose().rotate((float)Math.toRadians(degrees));g.pose().translate(-b.x()-280,-b.y()-200);
            for(int slot=0;slot<7;slot++){var r=WheelGridLayout.atlasBounds(slot);blitPiece(g,b,id,r.x(),r.y(),slot%4*WheelGridLayout.ATLAS_CELL,50+slot/4*WheelGridLayout.ATLAS_CELL,r.width(),r.height(),alpha);}
            drawPeople(g,b,id.equals(outgoingId)?outgoingChoices:choices,alpha);
        }finally{g.pose().popMatrix();}
    }
    private void drawPeople(GuiGraphicsExtractor g,ModerationWindowBounds b,List<Choice> people,float alpha){
        if(alpha<.05f)return;
        for(int i=0;i<Math.min(7,people.size());i++){var choice=people.get(i);if(choice.person().isBlank())continue;float visibility=alpha;if(pageStarted==0){long elapsed=closingStarted==0?System.nanoTime()-revealStarted:WheelReveal.reverseTime(closingFrom,System.nanoTime()-closingStarted);visibility*=WheelReveal.at(WheelGridLayout.revealOrder(i),elapsed);}if(visibility<.1f)continue;var r=WheelGridLayout.tile(i);int cx=b.x()+r.x()+r.width()/2,cy=b.y()+r.y()+r.height()/2;
            ModerationPeople.head(g,choice.person(),cx-12,cy-27,24);
            var lines=minecraft.font.split(ModerationPeople.name(choice.person()),82);for(int n=0;n<Math.min(2,lines.size());n++){var line=lines.get(n);g.text(minecraft.font,line,cx-minecraft.font.width(line)/2,cy+3+n*10,((int)(visibility*255)<<24)|0xf0edf7,false);}
        }
    }
    private void drawHover(GuiGraphicsExtractor g,ModerationWindowBounds b,int index){
        for(var row:WheelGridLayout.hoverRows(index))g.fill(b.x()+row[0],b.y()+row[1],b.x()+row[2],b.y()+row[1]+1,0x335f4090);
    }
    private void blitSector(GuiGraphicsExtractor g,ModerationWindowBounds b,int slot,float progress){var r=WheelGridLayout.atlasBounds(slot);blitPiece(g,b,r.x(),r.y(),slot%4*WheelGridLayout.ATLAS_CELL,50+slot/4*WheelGridLayout.ATLAS_CELL,r.width(),r.height(),progress);}
    private void blitPart(GuiGraphicsExtractor g,ModerationWindowBounds b,int x,int y,int w,int h,float progress){
        blitPiece(g,b,x,y,x,y,w,h,progress);
    }
    private void blitPiece(GuiGraphicsExtractor g,ModerationWindowBounds b,int x,int y,int sourceX,int sourceY,int w,int h,float progress){
        blitPiece(g,b,textureId,x,y,sourceX,sourceY,w,h,progress);
    }
    private void blitPiece(GuiGraphicsExtractor g,ModerationWindowBounds b,net.minecraft.resources.Identifier id,int x,int y,int sourceX,int sourceY,int w,int h,float progress){
        if(progress<=0)return;float scale=.84f+.16f*progress;
        g.pose().pushMatrix();g.pose().translate(b.x()+x+w/2f,b.y()+y+h/2f);g.pose().scale(scale,scale);
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,-w/2,-h/2,sourceX*3f,sourceY*3f,w,h,w*3,h*3,560*3,WheelGridLayout.ATLAS_HEIGHT*3,((int)(255*progress)<<24)|0xffffff);
        g.pose().popMatrix();
    }
    private List<String> summary() {
        List<String> text=new ArrayList<>();
        text.add(target==null?"Доступ по серверным правам":target.name());
        if(rule!=null && action.equals("punish")) text.add("Правило "+rule.number()+" — "+rule.title());
        if(stage==Stage.TYPES || stage==Stage.CONFIRM) {
            if(action.equals("punish")) {
                if(warn) text.add("Варн: срок определяет сервер (не 30м/1ч)");
                if(mute) text.add("Мут: "+timeLabel(muteTime));
                if(ban) text.add("Бан: "+timeLabel(banTime));
            } else if(action.equals("check")) {
                text.add("Вызов /cheatcheck: тайтл и ограничения");
                text.add("Выход с проверки: пожизненный IP-бан!");
            } else if(action.equals("teleport")){text.add("Телепортироваться к "+target.name());text.add("Включая OP-игроков · /tp "+target.name());}
            else if(action.equals("pass")) text.add("Снять ограничения проверки /cheatpass");
            else if(action.equals("history")) text.add("Открыть административную историю");
            else text.add("Просмотр без наказания и без команд");
        }
        if(stage==Stage.CONFIRM) {
            if(action.equals("preview")){text.add("Эффект: "+style.label+" · только локально");
                if(style==LocalModerationAnimation.Style.DRONE)text.add("Демо-полёт, без управления и наказания");}
            if(action.equals("punish")&&ban)text.add("Эффект: "+style.label+(ModerationService.commandAvailable("wcanim")?" (общий)":" (локальный)"));
            if(action.equals("punish")&&ban&&style==LocalModerationAnimation.Style.VAN&&ModerationService.commandAvailable("wcanim"))
                text.add("Бусик: переместит игрока на 1.8 блока");
            if(action.equals("punish")) {
                for(var command:ModerationService.commands(target,rule,warn,mute,ban,muteTime,banTime,action)) text.add("/"+command);
                if((warn?1:0)+(mute?1:0)+(ban?1:0)>1) text.add("Команды отдельные: сервер может принять не все");
            }
        }
        return text;
    }
    @Override protected boolean clickMenu(MouseButtonEvent event,boolean doubleClick) {
        if(closingStarted!=0)return true;
        if(event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {back();return true;}
        if(event.button()!=com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return super.clickMenu(event,doubleClick);
        int hit=hit(event.x(),event.y());
        if(hit>=0) {play("ui.click",.25f,1); choices.get(hit).select().run(); return true; }
        return true;
    }
    @Override protected boolean scrollMenu(double x,double y,double horizontal,double vertical) {
        if(closingStarted!=0)return true;
        var b=ModerationWindowBounds.of(width,height);
        if(x>=b.x()+14&&x<b.x()+546&&y>=b.y()+360&&y<b.y()+428){
            int count=summary().stream().mapToInt(line->StaffProfilePainter.lines(line,WheelGridLayout.SUMMARY_CHARS).size()).sum();
            summaryScroll=Math.clamp(summaryScroll+(vertical<0?1:-1),0,Math.max(0,count-WheelGridLayout.SUMMARY_LINES));return true;
        }
        int pages=Math.max(1,(allChoices().size()+PER_PAGE-1)/PER_PAGE);
        if(pages==1)return true;
        int direction=vertical<0?1:-1;
        changePage(Math.clamp(page+direction,0,pages-1));play("ui.click",.18f,1.1f);return true;
    }
}
