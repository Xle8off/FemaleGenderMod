package dev.aeltorx.inflow.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.*;

/** Live server workspace. The earlier card layout, with only a short appearance/minimize motion. */
public final class ModerationPanelScreen extends FixedModerationScreen {
    @Override protected boolean ownsWindowChrome(){return true;}
    @Override protected boolean clipsWindow(){return !folding;}
    private enum Tab { SUMMARY,SEARCH,CARD,CASE,CHECK,CO,AUDIT,EVIDENCE,PUNISH,REPORTS }
    private final Screen parent;
    private Tab tab=Tab.SUMMARY;
    private JsonObject card=new JsonObject(),plan,reportData=new JsonObject();
    private long selectedReport;
    private int reportPage;
    private boolean reportArchive;
    private JsonArray players=new JsonArray(),audit=new JsonArray();
    private String target="",selectedName="",cardError="",status="",query="",note="",description="",rule="2.1",muteTime="30m",banTime="1d";
    private LocalModerationAnimation.Style animation=LocalModerationAnimation.Style.NONE;
    private long selection;
    private boolean warn,mute,ban,evidenceConfirm,busy,folding;
    private String punishmentType="Выберите наказание";
    private int page;private int coreTime,coreRadius;
    private EditBox searchBox,noteBox,ruleBox,muteBox,banBox,descriptionBox;
    private NicknameDropdown nicknameDropdown;
    private RuleDropdown ruleDropdown;
    private RuleDropdown punishmentDropdown;
    private RuleDropdown animationDropdown;
    private Button punishmentCheck,punishmentConfirm;
    private List<Path> evidence=List.of();
    private boolean evidenceUploading;
    private long appeared=System.nanoTime(),refreshAt;
    private static final int TEXT=0xFFF0EDF7,MUTED=0xFFACB6C5;
    private final List<Button> guarded=new ArrayList<>();
    private final List<Button> targetControls=new ArrayList<>();
    private final List<net.minecraft.client.gui.components.AbstractWidget> controls=new ArrayList<>();
    private final List<PanelPainter.Line> paintedLines=new ArrayList<>();
    public ModerationPanelScreen(Screen parent){super(Component.literal("Модерация — InFlow"));this.parent=parent;}
    public void appear(){folding=false;appeared=System.nanoTime();}
    public void revealAfterWelcome(){folding=false;appeared=System.nanoTime()-180000000L;}
    @Override public boolean isPauseScreen(){return false;}
    private ModerationWindowBounds window(){return ModerationWindowBounds.of(width,height);}
    private int panelHeight(){return window().height();}private int origin(){return window().y();}private int left(){return window().x();}private int span(){return window().width();}private int contentLeft(){return left()+ModerationPanelLayout.CONTENT_X;}private int contentWidth(){return ModerationPanelLayout.contentWidth(span());}private int top(){return origin()+ModerationPanelLayout.BODY_Y;}private int bottom(){return origin()+panelHeight()-98;}
    private int rows(){return Math.max(1,(bottom()-top()-44)/(tab==Tab.AUDIT?40:27));}
    private Button button(String label,int x,int y,int w,Runnable run){var r=window().fit(x,y,Math.max(20,w),20);var b=addRenderableWidget(ModerationButtons.builder(Component.literal(label),v->{if(!folding&&System.nanoTime()-appeared>180000000L)run.run();}).bounds(r.x(),r.y(),r.width(),r.height()).build());if(label.equals("←")||label.equals("—"))b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label.equals("←")?"Назад":"Свернуть")));controls.add(b);return b;}
    private Button guarded(String label,int x,int y,int w,Runnable run){var b=button(label,x,y,w,run);guarded.add(b);return b;}
    private EditBox field(String label,int x,int y,int w,int max,String value){var r=window().fit(x,y,Math.max(40,w),20);var f=addRenderableWidget(new ModerationTextField(font,r.width(),r.height(),Component.literal(label)));f.setRectangle(r.width(),r.height(),r.x(),r.y());f.setMaxLength(max);f.setValue(value);controls.add(f);return f;}
    private void preserve(){if(searchBox!=null)query=searchBox.getValue();if(noteBox!=null)note=noteBox.getValue();if(ruleBox!=null)rule=ruleBox.getValue();if(muteBox!=null)muteTime=muteBox.getValue();if(banBox!=null)banTime=banBox.getValue();if(descriptionBox!=null)description=descriptionBox.getValue();}
    private void rebuild(){String focus=getFocused() instanceof EditBox edit?edit.getMessage().getString():"";int cursor=getFocused() instanceof EditBox edit?edit.getCursorPosition():0;preserve();setFocused(null);clearWidgets();initMenu();if(!focus.isEmpty())for(var control:controls)if(control instanceof EditBox edit&&edit.getMessage().getString().equals(focus)){setFocused(edit);edit.setFocused(true);edit.setCursorPosition(Math.min(cursor,edit.getValue().length()));edit.setHighlightPos(edit.getCursorPosition());break;}}
    @Override public void resize(int width,int height){preserve();super.resize(width,height);}
    private void move(Tab next){preserve();tab=next;page=0;plan=null;evidenceConfirm=false;rebuild();refresh();}
    @Override protected void initMenu(){
        ruleDropdown=null;punishmentDropdown=null;animationDropdown=null;punishmentCheck=null;punishmentConfirm=null;
        targetControls.clear();
        ModeratorAccount.ensure();
        nicknameDropdown=null;searchBox=null;noteBox=null;ruleBox=null;muteBox=null;banBox=null;descriptionBox=null;guarded.clear();controls.clear();
        int x=contentLeft(),w=contentWidth(),y=top(),footer=bottom()+14;
        button("←",left()+8,origin()+8,22,this::onClose).setY(origin()+6);button("—",left()+span()-34,origin()+8,22,()->{if(!folding){preserve();setFocused(null);folding=true;appeared=System.nanoTime();}}).setY(origin()+6);
        addRenderableWidget(ModerationButtons.builder(Component.literal("Личный кабинет — "+ModeratorAccount.name()),b->{if(!folding){preserve();minecraft.gui.setScreen(new StaffProfilesScreen(this,true));}}).bounds(left()+8,origin()+panelHeight()-40,span()-20,30).build());
        String[] names={"Обзор","Игроки","История","Заметки","Проверка","CoreProtect","Журнал","Вложения","Наказания","Жалобы"};
        int navRow=0;
        for(int i=0;i<names.length;i++){
            Tab next=Tab.values()[i];
            if(target.isBlank()&&!Set.of(Tab.SUMMARY,Tab.SEARCH,Tab.AUDIT,Tab.REPORTS).contains(next))continue;
            var r=ModerationPanelLayout.nav(navRow++);var nav=button(names[i],left()+r.x(),origin()+r.y(),r.width(),()->move(next));ModerationButtons.selected(nav,tab==next);
        }
        button(target.isBlank()?"Выбрать игрока":"Сменить игрока",x+w-122,origin()+47,116,()->move(Tab.SEARCH));
        if(target.isBlank())button("Найти и выбрать игрока",x,origin()+102,w,()->move(Tab.SEARCH));
        else {String[] actions={"ТП к игроку","ТП сюда","Проверка","Наказание","Наблюдать","Сообщение"};Runnable[] handlers={()->confirmTargetAction("tp"),()->confirmTargetAction("tp-here"),()->move(Tab.CHECK),()->move(Tab.PUNISH),()->confirmTargetAction("spec"),this::openMessage};
        for(int i=0;i<6;i++){var r=ModerationPanelLayout.action(i,span());guarded(actions[i],left()+r.x(),origin()+r.y(),r.width(),handlers[i]);}}
        targetControls.addAll(guarded);
        if(target.isBlank())targetControls.forEach(b->b.active=false);
        if(target.isBlank()&&!Set.of(Tab.SUMMARY,Tab.SEARCH,Tab.AUDIT,Tab.REPORTS).contains(tab)){
            button("Выбрать игрока для этого раздела",x,y+35,w,()->move(Tab.SEARCH));
            return;
        }
        switch(tab){
            case SUMMARY -> {
                button("Обновить",x,footer,w/4-3,this::refresh);
                button("Жалобы ("+number(ModerationWorkspace.summary,"openReports")+")",x+w/4,footer,w/4-3,()->move(Tab.REPORTS));
                button("Модераторы",x+w/2,footer,w/4-3,()->{preserve();minecraft.gui.setScreen(new StaffProfilesScreen(this,false));});
                guarded("Из спека",x+w*3/4,footer,w-w*3/4,()->request("spec-return",new JsonObject(),this::result));
                var entries=new JsonArray();for(var check:array(ModerationWorkspace.summary,"activeChecks"))entries.add(check);for(var file:array(ModerationWorkspace.summary,"cases"))entries.add(file);
                for(int i=page*rows();i<Math.min(entries.size(),(page+1)*rows());i++){JsonObject e=entries.get(i).getAsJsonObject();String uuid=text(e,"uuid");String label=e.has("moderator")?"Проверка: "+text(e,"target")+" · "+text(e,"moderator"):"Дело: "+text(e,"name")+" · "+text(e,"assigned");button(label,x+4,y+30+(i%rows())*27,w-8,()->select(uuid));}pager(entries.size());
            }
            case REPORTS -> {
                var items=array(reportData,"items");int visible=reportVisible();
                for(int i=0;i<Math.min(items.size(),visible);i++){
                    var report=items.get(i).getAsJsonObject();long id=number(report,"id");
                    String state=text(report,"state").equals("open")?"свободна":text(report,"state").equals("closed")?"закрыта":text(report,"assigneeName");
                    button((id==selectedReport?"▶ ":"")+"#"+id+" · "+text(report,"targetName")+" · "+state,x,y+28+i*24,w,()->{selectedReport=id;rebuild();});
                }
                int pages=Math.max(1,(int)number(reportData,"pages"));
                button("‹",x,footer-24,25,()->{reportPage=Math.max(0,reportPage-1);refresh();});
                button("Обновить",x+29,footer-24,85,this::refresh);
                button(reportArchive?"Архив":"Активные",x+118,footer-24,w-147,()->{reportArchive=!reportArchive;reportPage=0;selectedReport=0;refresh();});
                button("›",x+w-25,footer-24,25,()->{reportPage=Math.min(pages-1,reportPage+1);refresh();});
                int slot=w/5;
                guarded("Взять",x,footer,slot-3,()->reportAction("claim"));
                guarded("Освободить",x+slot,footer,slot-3,()->reportAction("release"));
                guarded("Закрыть",x+slot*2,footer,slot-3,()->reportAction("close"));
                button("Карточка",x+slot*3,footer,slot-3,()->{var r=currentReport();if(r.isEmpty()){status="Выбери жалобу";return;}selectedName=text(r,"targetName");select(text(r,"target"));});
                button("Текст",x+slot*4,footer,w-slot*4,()->{var r=currentReport();if(r.isEmpty()){status="Выбери жалобу";return;}var e=new JsonObject();e.add("time",r.get("created"));e.add("actor",r.get("reporterName"));e.addProperty("action","Жалоба #"+number(r,"id")+" на "+text(r,"targetName"));e.add("result",r.get("reason"));minecraft.gui.setScreen(new ModerationEntryScreen(this,e));});
            }
            case SEARCH -> {
                searchBox=field("Ник или UUID",x,y,w-86,40,query);searchBox.setHint(Component.literal("Ник или UUID игрока"));setInitialFocus(searchBox);button("Найти",x+w-79,y,79,this::search);
                nicknameDropdown=new NicknameDropdown(searchBox,this::nicknameCandidates,this::acceptNickname);
                for(int i=page*rows();i<Math.min(players.size(),(page+1)*rows());i++){var e=players.get(i).getAsJsonObject();String uuid=text(e,"uuid");var row=button(text(e,"name")+" · "+(bool(e,"online")?"онлайн":"офлайн"),x+4,y+28+i%rows()*27,w-8,()->select(uuid));ModerationPeople.person(e);ModerationButtons.person(row,text(e,"name"));}pager(players.size());
            }
            case CARD -> {
                button("Обновить карточку",x,footer,w/2-3,this::refresh);button("Общая сводка",x+w/2,footer,w/2,()->move(Tab.SUMMARY));pager(array(card,"sanctions").size());
            }
            case CASE -> {
                noteBox=field("Заметка",x,footer-24,w-84,1000,note);guarded("Сохранить",x+w-79,footer-24,79,()->caseAction("note",noteBox.getValue()));
                int third=w/3;guarded("Взять дело",x,footer,third-3,()->caseAction("case-assign","me"));guarded("Закрыть дело",x+third,footer,third-3,()->caseAction("case-state","closed"));guarded("Открыть дело",x+third*2,footer,w-third*2,()->caseAction("case-state","open"));pager(array(object(card,"case"),"entries").size());
                var list=array(object(card,"case"),"entries");int visible=Math.max(1,rows()-1);for(int i=page*visible;i<Math.min(list.size(),(page+1)*visible);i++){var e=list.get(list.size()-1-i).getAsJsonObject();button("Просмотр",x+w-65,y+30+i%visible*27,65,()->{preserve();minecraft.gui.setScreen(new ModerationEntryScreen(this,e));});}
            }
            case CHECK -> {
                button("Написать проверяемому",x,y+91,w,this::openMessage);
                guarded("Вызвать: выход = IP-бан",x,footer,w/2-3,()->{status="Внимание: выход с проверки — IP-бан. Нажми ещё раз для вызова";if(evidenceConfirm)sendTarget("check");else evidenceConfirm=true;});
                guarded("Проверка пройдена",x+w/2,footer,w/2,()->sendTarget("check-pass"));
            }
            case CO -> {
                String[] times={"30m","1h","4h","1d","7d"},radii={"5","10","30","100"};
                button("Время: "+times[coreTime],x,y+32,w/2-3,()->{coreTime=(coreTime+1)%times.length;rebuild();});button("Радиус: "+radii[coreRadius],x+w/2,y+32,w/2,()->{coreRadius=(coreRadius+1)%radii.length;rebuild();});
                guarded("Инспектор /co i",x,footer,w/2-3,()->sendTarget("co-inspect"));guarded("Логи игрока",x+w/2,footer,w/2,()->{var q=targetRequest();q.addProperty("time",times[coreTime]);q.addProperty("radius",radii[coreRadius]);request("co-lookup",q,this::result);});
            }
            case AUDIT -> {
                button("Обновить журнал",x,footer,w,this::refresh);
                pager(audit.size());
                for(int i=page*rows();i<Math.min(audit.size(),(page+1)*rows());i++){
                    var entry=audit.get(audit.size()-1-i).getAsJsonObject();
                    String label=text(entry,"actor")+" → "+text(entry,"targetName")+" · "+ModerationEntryScreen.actionTitle(entry);
                    var row=button(label,x,y+25+i%rows()*40,w,()->{preserve();minecraft.gui.setScreen(new ModerationEntryScreen(this,entry));});
                    row.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label+"\nНажми, чтобы открыть полную запись")));
                }
            }
            case EVIDENCE -> {
                ruleBox=field("Номер правила",x,y+50,w,7,rule);
                button("Добавить файлы ("+evidence.size()+"/10)",x,y+74,w/2-3,()->{if(evidenceUploading)return;ModerationWorkspace.chooseFiles(paths->{if(evidenceUploading)return;var next=new ArrayList<>(evidence);for(var path:paths)if(!next.contains(path))next.add(path);if(next.size()>10){status="Максимум 10 вложений — выбери меньше";return;}evidence=List.copyOf(next);evidenceConfirm=false;status="Выбрано файлов: "+evidence.size();rebuild();});});
                button("Очистить",x+w-75,y+96,75,()->{if(evidenceUploading)return;evidence=List.of();evidenceConfirm=false;rebuild();});
                guarded(evidenceConfirm?"Подтвердить отправку":"Отправить в архив",x+w/2,y+74,w/2,()->{
                    if(evidenceUploading)return;preserve();
                    if(evidence.isEmpty()){status="Сначала выбери изображения или видео";return;}
                    if(!dev.aeltorx.inflow.common.ModerationWire.rule(rule)){status="Укажи номер правила, например 2.1";return;}
                    if(!evidenceConfirm){evidenceConfirm=true;status="В Discord: ник, модератор, правило и "+evidence.size()+" вложений";rebuild();return;}
                    evidenceConfirm=false;evidenceUploading=true;long generation=selection;
                    ModerationWorkspace.uploadBatch(evidence,target,rule,s->{if(generation!=selection)return;status=s;if(s.startsWith("Оригиналы сохранены")||s.startsWith("Сохранено на сервере"))request("card",targetRequest(),r->{result(r);status=s;rebuild();});},()->{evidenceUploading=false;if(generation==selection)rebuild();});
                });
                var files=array(object(card,"case"),"evidence");for(int i=0;i<Math.min(Math.max(0,(bottom()-y-122)/23),files.size());i++){var f=files.get(files.size()-1-i).getAsJsonObject();button("↓ "+text(f,"name")+" · "+number(f,"size")/(1024*1024)+" МиБ",x,y+122+i*23,w,()->ModerationWorkspace.download(target,text(f,"id"),s->status=s));}
            }
            case PUNISH -> {
                var typeButton=button(punishmentType+" ▾",x,y+36,w/2-4,()->{ruleDropdown.close();punishmentDropdown.toggle();});
                punishmentDropdown=new RuleDropdown(typeButton,punishmentType,List.of("Предупреждение","Мут","Бан"),value->{preserve();punishmentType=value;warn=value.equals("Предупреждение");mute=value.equals("Мут");ban=value.equals("Бан");plan=null;status="";rebuild();});
                var ruleButton=button(rule+" ▾",x+w/2,y+36,w-w/2,()->{punishmentDropdown.close();ruleDropdown.toggle();});
                ruleDropdown=new RuleDropdown(ruleButton,rule,value->{rule=value;plan=null;});
                if(mute)muteBox=field("Срок мута",x,y+82,w,12,muteTime);
                if(ban)banBox=field("Срок бана",x,y+82,w,12,banTime);
                if(ban){var animationButton=button("Анимация бана: "+animation.label+" ▾",x,y+112,w,()->{ruleDropdown.close();punishmentDropdown.close();animationDropdown.toggle();});
                    animationDropdown=new RuleDropdown(animationButton,animation.label,Arrays.stream(LocalModerationAnimation.Style.values()).map(style->style.label).toList(),value->{animation=Arrays.stream(LocalModerationAnimation.Style.values()).filter(style->style.label.equals(value)).findFirst().orElseThrow();plan=null;rebuild();}).above();}
                punishmentCheck=guarded("Проверить конфликт",x,footer,w/2-3,this::preflight);punishmentConfirm=guarded("Выдать наказание",x+w/2,footer,w/2,()->{preserve();if(plan==null){status="Сначала проверь конфликт";return;}if(!rule.equals(text(plan,"rule"))||!muteTime.equals(text(plan,"muteTime"))||!banTime.equals(text(plan,"banTime"))||warn!=bool(plan,"warn")||mute!=bool(plan,"mute")||ban!=bool(plan,"ban")||!(ban?animation.name():"NONE").equals(text(plan,"style"))){plan=null;status="Параметры изменились — проверь конфликт заново";return;}var q=targetRequest();q.addProperty("token",text(plan,"token"));plan=null;request("confirm",q,this::result);});
            }
        }
        if(!ModerationWorkspace.ready()&&status.isBlank())status="Подключаем панель к серверу…";
    }
    private void openMessage(){if(target.isBlank()){status="Сначала выбери игрока";return;}try{minecraft.gui.setScreen(new PlayerMessageScreen(this,new ModerationService.Target(UUID.fromString(target),displayTarget())));}catch(IllegalArgumentException e){status="Обнови карточку игрока";}}
    private void search(){preserve();var q=new JsonObject();q.addProperty("query",query);request("search",q,this::result);}
    private List<String> nicknameCandidates(){var names=new ArrayList<String>();var connection=minecraft.getConnection();if(connection!=null)for(var info:connection.getOnlinePlayers())names.add(info.getProfile().name());for(var value:players)names.add(text(value.getAsJsonObject(),"name"));return names;}
    private void acceptNickname(String name){
        if(busy){status="Дождись ответа сервера";return;}
        for(var value:players){var player=value.getAsJsonObject();if(name.equalsIgnoreCase(text(player,"name"))){select(text(player,"uuid"));selectedName=name;return;}}
        var connection=minecraft.getConnection();if(connection!=null)for(var info:connection.getOnlinePlayers())if(name.equalsIgnoreCase(info.getProfile().name())){select(info.getProfile().id().toString());selectedName=name;return;}
        search();
    }
    @Override protected boolean clickMenu(net.minecraft.client.input.MouseButtonEvent event,boolean twice){
        if(folding||System.nanoTime()-appeared<180000000L)return true;
        if(animationDropdown!=null&&animationDropdown.click(event))return true;
        if(nicknameDropdown!=null&&nicknameDropdown.click(event))return true;
        if(ruleDropdown!=null&&ruleDropdown.click(event))return true;
        if(punishmentDropdown!=null&&punishmentDropdown.click(event))return true;
        boolean handled=super.clickMenu(event,twice);
        // A click handler may rebuild the form. ContainerEventHandler then focuses the
        // old, detached button; repair that focus after it finishes dispatching.
        if(getFocused()!=null&&!children().contains(getFocused())){
            setFocused(null);if(tab==Tab.SEARCH&&searchBox!=null){setFocused(searchBox);searchBox.setFocused(true);}
        }
        return handled;
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){if(!folding&&animationDropdown!=null&&animationDropdown.keyPressed(event))return true;if(!folding&&punishmentDropdown!=null&&punishmentDropdown.keyPressed(event))return true;if(!folding&&ruleDropdown!=null&&ruleDropdown.keyPressed(event))return true;if(!busy&&!folding&&nicknameDropdown!=null&&nicknameDropdown.keyPressed(event))return true;if((event.key()==257||event.key()==335)&&tab==Tab.SEARCH&&getFocused()==searchBox&&!busy&&!folding){search();return true;}return super.keyPressed(event);}
    @Override protected boolean scrollMenu(double x,double y,double horizontal,double vertical){if(!folding&&animationDropdown!=null&&animationDropdown.scroll(x,y,vertical))return true;if(!folding&&punishmentDropdown!=null&&punishmentDropdown.scroll(x,y,vertical))return true;if(!folding&&ruleDropdown!=null&&ruleDropdown.scroll(x,y,vertical))return true;if(!folding&&nicknameDropdown!=null&&nicknameDropdown.scroll(x,y,vertical))return true;return super.scrollMenu(x,y,horizontal,vertical);}
    private List<String> foreignWork(){if(target.isBlank()||Set.of(Tab.SUMMARY,Tab.SEARCH,Tab.AUDIT,Tab.REPORTS).contains(tab))return List.of();var player=net.minecraft.client.Minecraft.getInstance().player;if(player==null)return List.of();return WorkWarnings.others(array(card,"work"),player.getUUID().toString(),player.getGameProfile().name());}
    public void showReports(){tab=Tab.REPORTS;refreshAt=0;}
    private int reportVisible(){return Math.max(1,Math.min(4,(bottom()-top()-75)/24));}
    private JsonObject currentReport(){for(var item:array(reportData,"items")){var r=item.getAsJsonObject();if(number(r,"id")==selectedReport)return r;}return new JsonObject();}
    private JsonObject reportRequest(){var q=new JsonObject();q.addProperty("page",reportPage);q.addProperty("pageSize",reportVisible());q.addProperty("archive",reportArchive);return q;}
    private void reportAction(String action){var r=currentReport();if(r.isEmpty()){status="Выбери жалобу";return;}var q=reportRequest();q.addProperty("report",selectedReport);q.add("revision",r.get("revision"));q.addProperty("action",action);request("report-action",q,this::result);}
    private void pager(int count){
        int visible=tab==Tab.CASE?Math.max(1,rows()-1):rows();int total=Math.max(1,(count+visible-1)/visible);page=Math.min(page,total-1);
        int y=origin()+panelHeight()-(tab==Tab.CASE?130:107);
        button("← Назад",contentLeft(),y,90,()->{page=Math.max(0,page-1);rebuild();}).active=page>0;
        button("Страница "+(page+1)+" / "+total,contentLeft()+contentWidth()/2-65,y,130,()->{}).active=false;
        button("Далее →",contentLeft()+contentWidth()-90,y,90,()->{page=Math.min(total-1,page+1);rebuild();}).active=page+1<total;
    }
    private void resetPunishment(){warn=false;mute=false;ban=false;punishmentType="Выберите наказание";plan=null;animation=LocalModerationAnimation.Style.NONE;}
    private void select(String uuid){resetPunishment();selectedName=uuid;for(var element:players){var p=element.getAsJsonObject();if(uuid.equals(text(p,"uuid")))selectedName=text(p,"name");}selection++;busy=false;cardError="";target=uuid;card=new JsonObject();move(Tab.CARD);}
    public void select(ModerationService.Target player){resetPunishment();selection++;busy=false;selectedName=player.name();cardError="";target=player.id().toString();card=new JsonObject();tab=Tab.CARD;refreshAt=0;}
    private boolean contactSearch;
    public void searchPlayer(String name){query=name;tab=Tab.SEARCH;contactSearch=true;}
    private String pendingTargetAction="";
    private long pendingTargetUntil;
    private void confirmTargetAction(String op){
        long now=System.currentTimeMillis();
        String key=target+":"+op;
        if(!key.equals(pendingTargetAction)||now>pendingTargetUntil){pendingTargetAction=key;pendingTargetUntil=now+5000;status="Подтверди повторным нажатием: "+switch(op){case "tp"->"телепорт к ";case "tp-here"->"телепорт сюда: ";default->"наблюдение за ";}+selectedName;return;}
        pendingTargetAction="";sendTarget(op);
    }
    private JsonObject targetRequest(){var q=new JsonObject();q.addProperty("target",target);return q;}
    private void sendTarget(String op){if(target.isBlank()&&!op.equals("co-inspect")){status="Сначала выбери игрока";return;}request(op,targetRequest(),this::result);}
    private void caseAction(String op,String text){var q=targetRequest();q.addProperty("revision",number(object(card,"case"),"revision"));q.addProperty("text",text);request(op,q,this::result);}
    private void preflight(){preserve();plan=null;if(!warn&&!mute&&!ban){status="Сначала выберите тип наказания";return;}var preset=ModerationRules.ALL.stream().filter(r->r.number().equals(rule)).findFirst();if(preset.isPresent()&&(preset.get().section()==1&&ban||preset.get().section()!=1&&mute||preset.get().section()==5&&warn)){status="Тип наказания не соответствует выбранному правилу";return;}var q=targetRequest();q.addProperty("rule",rule);q.addProperty("muteTime",muteTime);q.addProperty("banTime",banTime);q.addProperty("warn",warn);q.addProperty("mute",mute);q.addProperty("ban",ban);q.addProperty("style",ban?animation.name():"NONE");request("plan",q,this::result);}
    private void request(String op,JsonObject q,java.util.function.Consumer<JsonObject> accept){if(busy){status="Дождись ответа сервера";return;}long generation=selection;busy=true;status="Запрос к серверу…";ModerationWorkspace.request(op,q,r->{if(generation!=selection)return;busy=false;if(op.equals("card"))cardError=text(r,"kind").equals("error")?text(r,"message"):"";accept.accept(r);});}
    private void result(JsonObject q){String kind=text(q,"kind");status=text(q,"message");if(q.has("data"))switch(kind){
        case "card" -> {JsonObject next=q.getAsJsonObject("data");if(tab==Tab.SEARCH||target.isEmpty()||target.equalsIgnoreCase(text(next,"uuid"))){boolean changed=number(object(card,"case"),"revision")!=number(object(next,"case"),"revision")||array(card,"sanctions").size()!=array(next,"sanctions").size()||card.isEmpty()||!array(card,"work").equals(array(next,"work"));card=next;cardError="";selectedName=text(next,"name");target=text(next,"uuid");if(tab==Tab.SEARCH){selection++;tab=Tab.CARD;}if(changed)rebuild();}else{cardError="UUID ответа не совпадает с выбранным игроком";status=cardError;org.slf4j.LoggerFactory.getLogger("InFlow/Moderation").warn("Card identity mismatch: requested {}, received {}",target,text(next,"uuid"));}}
        case "reports" -> {reportData=q.getAsJsonObject("data");reportPage=(int)number(reportData,"page");if(currentReport().isEmpty()){var items=array(reportData,"items");selectedReport=items.isEmpty()?0:number(items.get(0).getAsJsonObject(),"id");}rebuild();}
        case "players" -> {players=q.getAsJsonArray("data");rebuild();}
        case "summary" -> {ModerationWorkspace.summary=q.getAsJsonObject("data");rebuild();}
        case "audit" -> {audit=q.getAsJsonArray("data");rebuild();}
        case "plan" -> {preserve();var candidate=q.getAsJsonObject("data");if(tab==Tab.PUNISH&&rule.equals(text(candidate,"rule"))&&muteTime.equals(text(candidate,"muteTime"))&&banTime.equals(text(candidate,"banTime"))&&warn==bool(candidate,"warn")&&mute==bool(candidate,"mute")&&ban==bool(candidate,"ban")&&(ban?animation.name():"NONE").equals(text(candidate,"style")))plan=candidate;else {plan=null;status="Параметры изменились — проверьте заново";}}
    }if(kind.equals("scene")||kind.equals("pilot")){ModerationService.remotelyStarted(new ModerationService.Target(UUID.fromString(target),displayTarget()),animation);if(kind.equals("scene"))onClose();}if(kind.equals("result"))refreshAt=System.currentTimeMillis()+400;if(kind.equals("error")||bool(card,"historyUnavailable"))refreshAt=System.currentTimeMillis()+30000;}
    private boolean refreshing;
    private void refresh(){
        if(busy||refreshing||tab==Tab.SEARCH)return;
        if(tab==Tab.REPORTS&&(!bool(ModerationWorkspace.summary,"reportQueue")||!bool(ModerationWorkspace.summary,"canReports"))){status="Очередь жалоб недоступна: проверь серверный модуль и права";refreshAt=System.currentTimeMillis()+30000;return;}
        String op=switch(tab){case SUMMARY->"summary";case AUDIT->"audit";case REPORTS->"reports";default->"card";};
        if(op.equals("card")&&target.isBlank())return;
        Tab requestedTab=tab;long generation=selection;
        refreshing=true;refreshAt=System.currentTimeMillis()+5000;
        // A periodic read must never disable moderation buttons or swallow a user action.
        ModerationWorkspace.request(op,op.equals("reports")?reportRequest():op.equals("card")?targetRequest():new JsonObject(),reply->{
            refreshing=false;
            if(generation!=selection||requestedTab!=tab){refreshAt=0;return;}
            if(!busy)result(reply);
        });
    }
    @Override public void tick(){super.tick();if(!ModerationService.allowed()){onClose();return;}if(folding&&System.nanoTime()-appeared>180000000L){folding=false;ModerationWorkspace.minimized=this;minecraft.gui.setScreen(null);return;}
        if(contactSearch&&tab==Tab.SEARCH&&ModerationWorkspace.ready()&&!busy){
            contactSearch=false;var q=new JsonObject();q.addProperty("query",query);request("search",q,this::result);
        }
        if(System.currentTimeMillis()>refreshAt&&tab!=Tab.PUNISH&&tab!=Tab.EVIDENCE)refresh();guarded.forEach(b->b.active=ModerationWorkspace.ready()&&!busy&&!folding);if(punishmentCheck!=null)punishmentCheck.active&=warn||mute||ban;if(punishmentConfirm!=null)punishmentConfirm.active&=plan!=null;if(target.isBlank())targetControls.forEach(b->b.active=false);}
    @Override public void onClose(){preserve();ModerationWorkspace.minimized=null;minecraft.gui.setScreen(parent);}
    private static String text(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"—";}
    private static JsonObject object(JsonObject o,String key){return o.has(key)&&o.get(key).isJsonObject()?o.getAsJsonObject(key):new JsonObject();}
    private static JsonArray array(JsonObject o,String key){return o.has(key)&&o.get(key).isJsonArray()?o.getAsJsonArray(key):new JsonArray();}
    private static boolean bool(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()&&o.get(key).getAsBoolean();}
    private static long number(JsonObject o,String key){return o.has(key)?o.get(key).getAsLong():0;}
    private void text(GuiGraphicsExtractor g,String value,int y,int color){paintedLines.add(new PanelPainter.Line(ModerationPanelLayout.CONTENT_X,y-origin(),ModerationLabels.readable(value),color,contentWidth()));}
    private String displayTarget(){return card.has("name")?text(card,"name"):target.isBlank()?"Игрок не выбран":selectedName;}
    private String sectionHelp(){
        if(tab==Tab.SUMMARY)return "Начни с поиска игрока или очереди жалоб";
        if(tab==Tab.REPORTS)return "Выбери жалобу, затем возьми её в работу или открой полный текст";
        if(tab==Tab.AUDIT)return "Серверный журнал действий модераторов";
        if(tab==Tab.SEARCH)return "Введи ник или UUID, нажми «Найти», затем выбери игрока в списке";
        if(target.isBlank())return "Для действий над игроком сначала открой вкладку «Найти игрока»";
        return switch(tab){
            case PUNISH -> "Выбери правило и наказание → проверь конфликт → подтверди выдачу";
            case EVIDENCE -> "Укажи правило → добавь файлы → отправь и подтверди вложения";
            case CASE -> "Заметка сохраняется в деле выбранного игрока на сервере";
            case CHECK -> "Проверка — реальное действие; перед вызовом прочитай предупреждение";
            case CO -> "Задай время и радиус; результат поиска появится в игровом чате";
            case REPORTS -> "Выбери жалобу, затем возьми её в работу или открой полный текст";
            default -> "Выбранный игрок закреплён сверху; вкладки не меняют цель действий";
        };
    }
    public String minimizedLabel(){return displayTarget();}
    @Override protected void drawMenu(GuiGraphicsExtractor g,int mx,int my,float delta){
        double progress=Math.min(1,(System.nanoTime()-appeared)/180000000.0),ease=1-Math.pow(1-progress,3);
        paintedLines.clear();
        int y=top()+6;String who=displayTarget();
        var busyWith=foreignWork();text(g,busyWith.isEmpty()?sectionHelp():"Уже работает: "+busyWith.getFirst()+(busyWith.size()>1?" · ещё "+(busyWith.size()-1):""),origin()+ModerationPanelLayout.HELP_Y,busyWith.isEmpty()?MUTED:0xFFFFC17E);
        if(target.isBlank()&&!Set.of(Tab.SUMMARY,Tab.SEARCH,Tab.AUDIT,Tab.REPORTS).contains(tab))text(g,"Сначала выбери игрока, с которым хочешь работать",y,TEXT);
        else switch(tab){
            case SUMMARY -> {text(g,"Сводка: дел "+text(ModerationWorkspace.summary,"openCases")+" · проверок "+text(ModerationWorkspace.summary,"checks"),y,TEXT);}
            case REPORTS -> {
                text(g,"Жалобы · "+(reportArchive?"архив":"активные")+": "+number(reportData,"count")+" · стр. "+(reportPage+1)+"/"+Math.max(1,number(reportData,"pages")),y,TEXT);
                var r=currentReport();if(!r.isEmpty()){int yy=top()+30+reportVisible()*24; text(g,"От: "+text(r,"reporterName")+" → "+text(r,"targetName"),yy,MUTED);text(g,"Модератор: "+(text(r,"assigneeName").isBlank()?"не назначен":text(r,"assigneeName")),yy+12,MUTED);if(yy+24<bottom()-18)text(g,"Причина: "+text(r,"reason"),yy+24,TEXT);}else if(array(reportData,"items").isEmpty())text(g,"Жалоб нет · /wcreport <ник> <текст>",y+35,MUTED);
            }
            case CARD -> {
                text(g,"История наказаний",y,TEXT);
                JsonObject currentMute=object(card,"mute");text(g,"Мут: "+(!bool(card,"muteKnown")?"данные недоступны":currentMute.isEmpty()?"нет":"активен · "+text(currentMute,"reason")),y+14,MUTED);
                JsonArray list=array(card,"sanctions");for(int i=page*rows();i<Math.min(list.size(),(page+1)*rows());i++){var s=list.get(i).getAsJsonObject();int yy=y+40+i%rows()*27;text(g,"#"+text(s,"id")+" "+text(s,"type")+" · "+text(s,"reason")+(bool(s,"active")?" · активно":" · завершено"),yy,TEXT);text(g,text(s,"staff")+" · до "+text(s,"expires"),yy+11,MUTED);}if(list.isEmpty())text(g,!cardError.isBlank()?"Не удалось загрузить: "+cardError:card.isEmpty()?"Ожидаем реальные данные сервера…":bool(card,"historyUnavailable")?"История не получена из базы SpringPlusBan":"Баны и варны в истории отсутствуют",y+40,MUTED);
            }
            case CASE -> {
                JsonObject file=object(card,"case");text(g,who+" · "+text(file,"state")+" · ответственный: "+text(file,"assigned"),y,TEXT);
                JsonArray list=array(file,"entries");int limit=Math.max(1,rows()-1);for(int i=page*limit;i<Math.min(list.size(),(page+1)*limit);i++){var e=list.get(list.size()-1-i).getAsJsonObject();text(g,font.plainSubstrByWidth(text(e,"actor")+" · "+text(e,"action"),contentWidth()-78),y+24+i%limit*27,TEXT);text(g,font.plainSubstrByWidth(text(e,"result"),span()-90),y+35+i%limit*27,MUTED);}
            }
            case CHECK -> {text(g,who,y,TEXT);JsonObject c=object(card,"check");text(g,c.isEmpty()?"Проверка не активна":"Ведёт "+text(c,"name")+" · "+Math.max(0,(System.currentTimeMillis()-number(c,"since"))/60000)+" мин",y+24,MUTED);text(g,"Реальная проверка SpringPlusBan, не демонстрация",y+45,MUTED);text(g,"Выход с активной проверки может вызвать IP-бан!",y+66,0xFFFFC17E);}
            case CO -> {text(g,"CoreProtect · "+who,y,TEXT);text(g,"Инспектор переключается; результаты поиска — в чате",y+66,MUTED);}
            case AUDIT -> {text(g,audit.isEmpty()?"В журнале пока нет записей":"Журнал · нажми запись для подробностей",y,TEXT);for(int i=page*rows();i<Math.min(audit.size(),(page+1)*rows());i++){var e=audit.get(audit.size()-1-i).getAsJsonObject();String time=java.time.Instant.ofEpochMilli(number(e,"time")).atZone(java.time.ZoneId.of("Europe/Kiev")).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm:ss"));text(g,time+" · "+text(e,"result"),y+47+i%rows()*40,MUTED);}}
            case EVIDENCE -> {text(g,"Архив нарушений · "+who,y,TEXT);text(g,evidence.isEmpty()?"До 10 файлов · суммарно до 128 МиБ":evidence.size()+" вложений · "+evidence.getFirst().getFileName(),y+17,MUTED);text(g,"Номер правила (например 2.1)",y+29,MUTED);text(g,bool(ModerationWorkspace.summary,"discord")?"Discord настроен на сервере":"Discord отключён — webhook не настроен",y+96,MUTED);}
            case PUNISH -> {text(g,"Наказание: "+who+" · причина — номер правила",y,TEXT);
                paintedLines.add(new PanelPainter.Line(ModerationPanelLayout.CONTENT_X,y+21-origin(),"Тип наказания",MUTED,contentWidth()/2-8));
                paintedLines.add(new PanelPainter.Line(ModerationPanelLayout.CONTENT_X+contentWidth()/2,y+21-origin(),"Пункт правил",MUTED,contentWidth()/2-8));
                if(mute||ban)text(g,mute?"Срок мута: например 30m или 1h":"Срок бана: например 1d или permanent",y+68,MUTED);
                if(plan==null)text(g,warn?"Внимание: 3-й варн может выдать автобан":mute||ban?"Проверь тип, правило и срок перед выдачей":"Сначала выберите тип наказания",y+150,MUTED);
                if(plan!=null)text(g,"Подтверждение: "+who+" · "+punishmentType+" · "+rule+(mute?" · "+muteTime:ban?" · "+banTime:""),y+165,0xFF94DDB3);}
            case SEARCH -> {if(players.isEmpty())text(g,busy?"Ищем игрока…":"Результаты поиска появятся здесь",top()+36,MUTED);}
        }
        text(g,status,origin()+panelHeight()-58,0xFFBC94E6);
        var account=new PanelPainter.Account(ModeratorAccount.name(),ModeratorAccount.avatar(),mx>=left()+8&&mx<left()+span()-12&&my>=origin()+panelHeight()-40&&my<origin()+panelHeight()-10);
        var chip=localRect(MinimizedModerationLayout.of(screenWidth(),screenHeight()));
        double f=folding?ease:0;int x=(int)(left()+(chip.x()-left())*f),yy=(int)(origin()+(chip.y()-origin())*f),ww=(int)(span()+(chip.width()-span())*f),hh=(int)(panelHeight()+(chip.height()-panelHeight())*f);
        // Native widgets/text share one transform with the surface, including minimize animation.
        g.pose().pushMatrix();g.pose().translate(x,yy);g.pose().scale(ww/(float)span(),hh/(float)panelHeight());g.pose().translate(-left(),-origin());
        ModerationSurfaces.workspace(g,left(),origin(),span(),panelHeight(),account.hovered());
        ModerationPeople.head(g,account.name(),left()+14,origin()+panelHeight()-36,22);
        g.centeredText(font,"Модерация",left()+span()/2,origin()+12,TEXT);
        g.text(font,"РАЗДЕЛЫ",left()+14,origin()+37,MUTED,false);g.text(font,"F9 · колесо",left()+14,origin()+panelHeight()-105,MUTED,false);g.text(font,"F10 · панель",left()+14,origin()+panelHeight()-91,MUTED,false);
        if(!target.isBlank())ModerationPeople.head(g,who,contentLeft()+6,origin()+47,20);
        ModerationPeople.person(card);g.text(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(ModerationPeople.name(who),contentWidth()-170)),contentLeft()+(target.isBlank()?10:32),origin()+52,TEXT,false);
        String details=target.isBlank()?"Выбери игрока в разделе «Игроки»":card.isEmpty()?"Загрузка карточки…":(bool(card,"online")?"Онлайн":"Офлайн")+" · Пинг: "+text(card,"ping")+" · Сессия: "+Math.max(0,number(card,"session")/60000)+" мин";
        g.text(font,font.plainSubstrByWidth(details,contentWidth()-20),contentLeft()+10,origin()+74,MUTED,false);
        g.text(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(ModerationPeople.name(account.name()),250)),left()+44,origin()+panelHeight()-36,TEXT,false);g.text(font,"Аккаунт модератора",left()+44,origin()+panelHeight()-22,MUTED,false);
        String accountAction="Личный кабинет ›";g.text(font,accountAction,left()+span()-24-font.width(accountAction),origin()+panelHeight()-29,0xFFBC94E6,false);
        for(var line:paintedLines){int available=line.width()>0?line.width():contentWidth();String value=line.text();if(font.width(value)>available)value=font.plainSubstrByWidth(value,Math.max(1,available-font.width("…")))+"…";g.text(font,value,left()+line.x(),origin()+line.y(),line.color(),false);}
        for(var control:controls)control.extractRenderState(g,mx,my,delta);
        if(!folding&&nicknameDropdown!=null)nicknameDropdown.draw(g,mx,my);
        if(!folding&&ruleDropdown!=null)ruleDropdown.draw(g,mx,my);
        if(!folding&&punishmentDropdown!=null)punishmentDropdown.draw(g,mx,my);
        if(!folding&&animationDropdown!=null)animationDropdown.draw(g,mx,my);
        g.pose().popMatrix();
        if(!folding&&ease>=1)for(var control:controls){String label=control.getMessage().getString();if((label.equals("←")||label.equals("—"))&&mx>=control.getX()&&mx<control.getX()+control.getWidth()&&my>=control.getY()&&my<control.getY()+control.getHeight())menuTooltip(g,Component.literal(label.equals("←")?"Назад":"Свернуть"),mx,my);}
    }
    public static void drawMinimized(GuiGraphicsExtractor g){
        if(ModerationWorkspace.minimized==null)return;
        var mc=net.minecraft.client.Minecraft.getInstance();
        var r=MinimizedModerationLayout.of(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
        ModerationSurfaces.panel(g,r.x(),r.y(),r.width(),r.height());
        g.text(mc.font,mc.font.plainSubstrByWidth("F10 · "+ModerationWorkspace.minimized.minimizedLabel(),Math.max(1,r.width()-14)),r.x()+7,r.y()+(r.height()-mc.font.lineHeight)/2,TEXT,false);
    }
}
