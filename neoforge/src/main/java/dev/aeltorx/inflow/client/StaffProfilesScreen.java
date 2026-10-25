package dev.aeltorx.inflow.client;
import com.google.gson.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import java.util.*;

/** Server-backed directory, public profiles/comments and owner-only private notebook. */
public final class StaffProfilesScreen extends FixedModerationScreen {
    @Override protected boolean ownsWindowChrome(){return true;}
    private enum Mode { LIST,PROFILE,EDIT,NOTE,COMMENTS,COMMENT }
    private final Screen parent;private final Object connection;private final boolean self;private Mode mode;private String target="",status="",painted="",commentId="";private JsonObject profile=new JsonObject(),directory=new JsonObject();private int page,commentPage;private long generation,refreshAt;private boolean waiting;
    private final List<AbstractWidget> controls=new ArrayList<>();private EditBox input;private DynamicTexture texture;private final Identifier id=Identifier.fromNamespaceAndPath("inflow_moderation","inflow/staff_"+UUID.randomUUID());
    private boolean artPending;private long artGeneration;
    private static final int DIRECTORY_ROW=30;
    private final NavigableMap<Integer,JsonArray> directoryPages=new TreeMap<>(),directoryRefresh=new TreeMap<>();
    private List<JsonObject> directoryRows=List.of();private int directoryOffset,refreshThrough;private boolean refreshingDirectory;private double scrollRemainder;private String queuedProfile="";
    private long nextDirectoryFetchAt;
    private int directoryVisible(){return Math.max(1,(bounds().height()-100)/DIRECTORY_ROW);}
    private void rebuildDirectoryRows(){var unique=new LinkedHashMap<String,JsonObject>();for(var entries:directoryPages.values())for(var entry:entries){var row=entry.getAsJsonObject();unique.putIfAbsent(str(row,"uuid"),row);ModerationPeople.person(row);}directoryRows=List.copyOf(unique.values());directoryOffset=Math.clamp(directoryOffset,0,Math.max(0,directoryRows.size()-directoryVisible()));}
    public StaffProfilesScreen(Screen parent,boolean self){super(Component.literal("InFlow — Модераторы"));this.parent=parent;this.self=self;connection=net.minecraft.client.Minecraft.getInstance().getConnection();mode=self?Mode.PROFILE:Mode.LIST;}
    private ModerationWindowBounds bounds(){return ModerationWindowBounds.of(width,height);}
    private static String str(JsonObject p,String key){return p.has(key)?p.get(key).getAsString():"";}
    private static long num(JsonObject p,String key){return p.has(key)?p.get(key).getAsLong():0;}
    private static boolean flag(JsonObject p,String key){return p.has(key)&&p.get(key).getAsBoolean();}
    private JsonArray comments(){return profile.has("comments")?profile.getAsJsonArray("comments"):new JsonArray();}
    private boolean own(){return flag(profile,"own");}
    private boolean active(){return connection==minecraft.getConnection()&&minecraft.gui.screen()==this&&ModerationService.allowed();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float dt){}
    private void button(String text,int x,int y,int w,Runnable run){var button=addRenderableWidget(ModerationButtons.builder(Component.literal(text),b->{if((!waiting||mode==Mode.LIST||text.equals("Назад"))&&!SharedModeratorProfiles.choosing())run.run();}).bounds(x,y,Math.max(20,w),20).build());controls.add(button);int separator=text.indexOf(" · ");if(mode==Mode.LIST&&separator>0)ModerationButtons.person(button,text.substring(0,separator));}
    private void field(String value,int max,int x,int y,int width){input=addRenderableWidget(new ModerationTextField(font,width,20,Component.literal("Текст")));input.setRectangle(width,20,x,y);input.setMaxLength(max);input.setValue(value);controls.add(input);}
    private void rebuild(){setFocused(null);clearWidgets();controls.clear();input=null;initMenu();}
    @Override protected void initMenu(){controls.clear();input=null;var b=bounds();int x=b.x()+12,w=b.width()-28,y=b.y(),bottom=y+b.height();if(target.isEmpty()&&self&&minecraft.player!=null)target=minecraft.player.getUUID().toString();
        button("Назад",b.x()+8,y+8,58,this::back);button("Мой профиль",b.x()+b.width()-112,y+8,100,()->open(minecraft.player.getUUID().toString()));
        switch(mode){
            case LIST->{for(int i=directoryOffset;i<Math.min(directoryRows.size(),directoryOffset+directoryVisible());i++){var p=directoryRows.get(i);String uuid=str(p,"uuid");button(str(p,"name")+" · "+(flag(p,"online")?"в сети":"не в сети"),x,y+48+(i-directoryOffset)*DIRECTORY_ROW,w-10,()->open(uuid));}}
            case PROFILE->{int slot=w/(own()?3:1);if(own()){button("Оформление",x,bottom-78,slot-3,()->change(Mode.EDIT));button("Личная заметка",x+slot,bottom-78,slot-3,()->change(Mode.NOTE));}button("Комментарии ("+comments().size()+")",x+(own()?slot*2:0),bottom-78,slot-3,()->change(Mode.COMMENTS));button("Обновить",x,bottom-51,w,this::fetch);}
            case EDIT->{button("Загрузить аватар",x,bottom-166,w/2-3,()->choose("avatar"));button("Загрузить баннер",x+w/2,bottom-166,w/2,()->choose("banner"));button("Сбросить аватар",x,bottom-143,w/2-3,()->remove("avatar"));button("Сбросить баннер",x+w/2,bottom-143,w/2,()->remove("banner"));field(str(profile,"about"),160,x,bottom-103,w);button("Сохранить описание",x,bottom-71,w,this::save);}
            case NOTE->{field(str(profile,"privateNote"),2000,x,bottom-92,w);button("Сохранить личную заметку",x,bottom-61,w,this::save);}
            case COMMENTS->{var comments=comments();int visible=3;commentPage=Math.clamp(commentPage,0,Math.max(0,(comments.size()-1)/visible));for(int i=commentPage*visible;i<Math.min(comments.size(),(commentPage+1)*visible);i++){var c=comments.get(i).getAsJsonObject();String id=str(c,"id");button(str(c,"name")+": "+shorten(str(c,"text"),45),x,y+61+(i%visible)*42,w,()->{commentId=id;change(Mode.COMMENT);});}button("‹",x,y+37,30,()->{commentPage=Math.max(0,commentPage-1);rebuild();});button("Обновить",x+34,y+37,w-68,this::fetch);button("›",x+w-30,y+37,30,()->{commentPage=Math.min(Math.max(0,(comments.size()-1)/visible),commentPage+1);rebuild();});field("",500,x,bottom-87,w);button("Отправить комментарий",x,bottom-59,w,()->{var q=params();q.addProperty("text",input.getValue());send("profile-comment",q);});}
            case COMMENT->{var c=comment();boolean canDelete=str(c,"author").equals(minecraft.player.getUUID().toString())||flag(profile,"canManageComments");if(canDelete)button("Удалить комментарий",x,bottom-61,w,()->{var q=params();q.addProperty("comment",commentId);send("profile-comment-delete",q);});}
        }
    }
    private static String shorten(String text,int n){return text.length()>n?text.substring(0,n)+"…":text;}
    private JsonObject comment(){for(var e:comments())if(str(e.getAsJsonObject(),"id").equals(commentId))return e.getAsJsonObject();return new JsonObject();}
    private void back(){if(mode==Mode.EDIT||mode==Mode.NOTE||mode==Mode.COMMENTS)change(Mode.PROFILE);else if(mode==Mode.COMMENT)change(Mode.COMMENTS);else if(mode==Mode.PROFILE&&!self){mode=Mode.LIST;generation++;waiting=false;refreshAt=0;rebuild();}else onClose();}
    private void change(Mode mode){this.mode=mode;rebuild();}
    private void open(String uuid){if(waiting&&mode==Mode.LIST){queuedProfile=uuid;status="Открываем профиль…";return;}queuedProfile="";target=uuid;profile=new JsonObject();mode=Mode.PROFILE;generation++;waiting=false;fetch();rebuild();}
    private JsonObject params(){var q=SharedModeratorProfiles.target(target);q.addProperty("revision",num(profile,"revision"));return q;}
    private void save(){var q=params();q.addProperty("about",mode==Mode.EDIT?input.getValue():str(profile,"about"));q.addProperty("privateNote",mode==Mode.NOTE?input.getValue():str(profile,"privateNote"));send("profile-edit",q);}
    private void remove(String kind){var q=params();q.addProperty("media",kind);send("profile-image-remove",q);}
    private void choose(String kind){SharedModeratorProfiles.choose(kind,s->{if(active()){status=s;painted="";}},r->{if(active())accept(r,true);});}
    private void send(String op,JsonObject q){if(waiting)return;waiting=true;SharedModeratorProfiles.request(op,q,r->{if(!active())return;waiting=false;accept(r,true);});}
    private void fetch(){if(waiting||!active())return;if(mode==Mode.PROFILE)SharedModeratorProfiles.retryImages(target);waiting=true;long selection=generation;var q=mode==Mode.LIST?new JsonObject():SharedModeratorProfiles.target(target);q.addProperty("page",page);SharedModeratorProfiles.request(mode==Mode.LIST?"profiles-list":"profile-get",q,r->{if(!active()||selection!=generation)return;waiting=false;accept(r,false);});refreshAt=System.currentTimeMillis()+5000;}
    private void accept(JsonObject r,boolean changed){String kind=str(r,"kind");status=str(r,"message");if(kind.equals("error")){queuedProfile="";refreshingDirectory=false;nextDirectoryFetchAt=refreshAt=System.currentTimeMillis()+15000;return;}
        if(kind.equals("staff-list")){
            directory=r.getAsJsonObject("data");page=(int)num(directory,"page");int last=Math.max(0,(int)num(directory,"pages")-1);
            if(refreshingDirectory){directoryRefresh.put(page,directory.getAsJsonArray("items"));if(page<Math.min(last,refreshThrough)){page++;nextDirectoryFetchAt=0;}else{directoryPages.clear();directoryPages.putAll(directoryRefresh);directoryRefresh.clear();refreshingDirectory=false;refreshAt=System.currentTimeMillis()+15000;}}
            else{directoryPages.put(page,directory.getAsJsonArray("items"));refreshAt=System.currentTimeMillis()+15000;}
            if(!refreshingDirectory){rebuildDirectoryRows();nextDirectoryFetchAt=System.currentTimeMillis()+150;}
            status="Загружено модераторов: "+directoryRows.size()+(directoryPages.size()<(int)num(directory,"pages")?" · прокрутите вниз":"");
        }else if(kind.equals("staff-profile")){profile=r.getAsJsonObject("data");SharedModeratorProfiles.loadImages(profile);if(changed&&mode!=Mode.COMMENTS)mode=Mode.PROFILE;}
        if(!queuedProfile.isEmpty()){String requested=queuedProfile;queuedProfile="";open(requested);return;}rebuild();
    }
    @Override public void tick(){if(!active()){if(connection!=minecraft.getConnection()||!ModerationService.allowed())minecraft.gui.setScreen(null);return;}long now=System.currentTimeMillis();
        if(mode==Mode.LIST){if(waiting||now<nextDirectoryFetchAt)return;
            if(refreshingDirectory){fetch();return;}
            int last=directoryPages.isEmpty()?-1:directoryPages.lastKey(),pages=Math.max(1,(int)num(directory,"pages"));
            if(last+1<pages&&(directoryRows.size()<directoryVisible()||directoryOffset+directoryVisible()+2>=directoryRows.size())){page=last+1;fetch();}
            else if(now>refreshAt){refreshThrough=Math.max(0,last);directoryRefresh.clear();refreshingDirectory=true;page=0;fetch();}
            return;
        }
        if(now>refreshAt&&(mode==Mode.PROFILE||mode==Mode.COMMENTS&&input!=null&&input.getValue().isBlank()))fetch();
    }
    @Override protected boolean scrollMenu(double x,double y,double horizontal,double vertical){var b=bounds();if(mode==Mode.LIST&&x>=b.x()+10&&x<b.x()+b.width()-10&&y>=b.y()+45&&y<b.y()+b.height()-45){scrollRemainder-=vertical;int steps=(int)scrollRemainder;scrollRemainder-=steps;int next=Math.clamp(directoryOffset+steps,0,Math.max(0,directoryRows.size()-directoryVisible()));if(next!=directoryOffset){directoryOffset=next;rebuild();}return true;}return super.scrollMenu(x,y,horizontal,vertical);}
    @Override protected boolean clickMenu(net.minecraft.client.input.MouseButtonEvent event,boolean twice){var b=bounds();if(mode==Mode.LIST&&directoryRows.size()>directoryVisible()&&event.x()>=b.x()+b.width()-19&&event.x()<b.x()+b.width()-9&&event.y()>=b.y()+48&&event.y()<b.y()+48+directoryVisible()*DIRECTORY_ROW){if(event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT){directoryOffset=(int)Math.round(Math.clamp((event.y()-b.y()-48)/(directoryVisible()*DIRECTORY_ROW),0,1)*(directoryRows.size()-directoryVisible()));rebuild();}return true;}boolean handled=super.clickMenu(event,twice);if(getFocused()!=null&&!children().contains(getFocused()))setFocused(null);return handled;}
    @Override public void onClose(){if(connection!=minecraft.getConnection()||!ModerationService.allowed())minecraft.gui.setScreen(null);else{if(parent instanceof ModerationPanelScreen panel)panel.revealAfterWelcome();minecraft.gui.setScreen(parent);}}
    @Override protected void drawMenu(GuiGraphicsExtractor g,int mx,int my,float dt){var b=bounds();var lines=new ArrayList<PanelPainter.Line>();int muted=0xffa4b5cb;String title="InFlow / Модераторы";StaffProfilePainter.Profile model=null;
        if(mode==Mode.PROFILE||mode==Mode.EDIT){title=mode==Mode.EDIT?"InFlow / Оформление":"InFlow / Профиль";if(!profile.isEmpty())model=new StaffProfilePainter.Profile(str(profile,"name"),mode==Mode.EDIT?"":str(profile,"about"),flag(profile,"online"),num(profile,"activeChecks"),num(profile,"openCases"),SharedModeratorProfiles.image(profile,"avatar"),SharedModeratorProfiles.image(profile,"banner"));}
        if(mode==Mode.NOTE){title="InFlow / Личная заметка";lines.add(new PanelPainter.Line(14,75,"Только тебе. Другим модераторам не передаётся.",muted));int y=98;for(String line:StaffProfilePainter.lines(str(profile,"privateNote"),Math.max(1,(b.width()-30)/6))){if(y>b.height()-120)break;lines.add(new PanelPainter.Line(14,y,line,0xffe5eef9));y+=13;}}
        if(mode==Mode.EDIT)lines.add(new PanelPainter.Line(14,b.height()-119,"О себе · видно другим модераторам",muted));
        if(mode==Mode.COMMENTS){title="InFlow / Комментарии";if(comments().isEmpty())lines.add(new PanelPainter.Line(14,87,"Пока нет комментариев коллег",muted));}
        if(mode==Mode.COMMENT){title="InFlow / Комментарий";var c=comment();lines.add(new PanelPainter.Line(14,75,str(c,"name")+" · "+java.time.Instant.ofEpochMilli(num(c,"time")).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm")),muted));int y=95;for(String line:StaffProfilePainter.lines(str(c,"text"),Math.max(1,(b.width()-30)/6))){if(y>b.height()-90)break;lines.add(new PanelPainter.Line(14,y,line,0xffe5eef9));y+=13;}}
        lines.add(new PanelPainter.Line(14,b.height()-29,waiting?"Запрос серверу…":status,0xff8ec9ff));String key=b+profile.toString()+SharedModeratorProfiles.revision()+title;
        if(mode==Mode.LIST&&directory.has("items"))for(var person:directory.getAsJsonArray("items"))ModerationPeople.person(person.getAsJsonObject());
        if(!key.equals(painted)&&!artPending){artPending=true;long generation=artGeneration;var snapshot=model;String heading=title;ModerationRaster.paint(()->StaffProfilePainter.paint(b.width(),b.height(),heading,List.of(),List.of(),snapshot),image->{artPending=false;if(image==null||generation!=artGeneration)return;if(texture==null){texture=new DynamicTexture(()->"InFlow staff profile",new NativeImage(image.getWidth(),image.getHeight(),false));minecraft.getTextureManager().register(id,texture);}ModerationRaster.upload(texture,image);painted=key;});}
        if(texture!=null){int tw=b.width()*3,th=b.height()*3;g.blit(RenderPipelines.GUI_TEXTURED,id,b.x(),b.y(),0,0,b.width(),b.height(),tw,th,tw,th,0xffffffff);}else ModerationSurfaces.panel(g,b.x(),b.y(),b.width(),b.height());
        if(mode==Mode.LIST){if(directoryRows.isEmpty())g.text(font,waiting?"Загружаем список…":"Модераторы пока не найдены",b.x()+14,b.y()+56,muted,false);if(directoryRows.size()>directoryVisible()){int track=directoryVisible()*DIRECTORY_ROW,thumb=Math.max(16,track*directoryVisible()/directoryRows.size()),top=b.y()+48+(track-thumb)*directoryOffset/Math.max(1,directoryRows.size()-directoryVisible());g.fill(b.x()+b.width()-15,b.y()+48,b.x()+b.width()-13,b.y()+48+track,ModerationTheme.BORDER.getRGB());g.fill(b.x()+b.width()-16,top,b.x()+b.width()-12,top+thumb,ModerationTheme.ACCENT.getRGB());}}
        for(var line:lines){int available=Math.max(1,b.width()-line.x()-12);String value=line.text();if(font.width(value)>available)value=font.plainSubstrByWidth(value,Math.max(1,available-font.width("…")))+"…";g.text(font,value,b.x()+line.x(),b.y()+line.y(),line.color(),false);}renderWidgets(g,mx,my,dt);
    }
    @Override public void removed(){artGeneration++;if(texture!=null)minecraft.getTextureManager().release(id);texture=null;painted="";}
}
