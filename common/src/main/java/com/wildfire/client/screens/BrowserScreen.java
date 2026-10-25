package com.wildfire.client.screens;
import com.google.gson.*;
import com.wildfire.gui.*;
import com.wildfire.client.gui.WildfireSlider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Shared screen controls; the server-issued player URL is never an editable address. */
public final class BrowserScreen extends Screen {
 private final Screen parent;
 private Button launch,choose,pageBack,pageReload,pageGo,pageClose,play,seekBack,seekNext,p2p,share;private WildfireSlider volume;private ModSearchBox address;private boolean focus,autoStarted;private String menuError="";
 private int left,top,w,h,vx,vy,vw,vh;private long lastMove;
 private Button qualityHeight,qualityFps,qualityApply;
 private Button qualityEnabled;
 public BrowserScreen(Screen parent){super(Component.literal("Управление общим экраном"));this.parent=parent;}
 private Button button(String text,int x,int y,int width,Runnable action){return addRenderableWidget(FlatButtons.builder(Component.literal(text),b->action.run()).bounds(x,y,width,20).build());}
 @Override protected void init(){
  BrowserRuntime.controls(true);
  w=Math.min(840,width-12);h=height-12;left=(width-w)/2;top=6;int pitch=(w-16)/4;
  button("Назад",left+8,top+6,pitch-3,this::onClose);
  launch=button("Подключить",left+8+pitch,top+6,pitch-3,this::start);
  choose=button("Другой экран",left+8+2*pitch,top+6,pitch-3,this::nextScreen);
  volume=addRenderableWidget(new WildfireSlider.Builder().position(left+8+3*pitch,top+6).size(pitch-3,20).range(0,1).current(BrowserRuntime.volume()).message(v->Component.literal("Громкость: "+Math.round(v*100)+"%")).update(BrowserRuntime::volume).save(v->{}).build(System.currentTimeMillis()));
  pageBack=button("<",left+8,top+50,22,()->BrowserRuntime.browserCommand("back"));
  pageReload=button("Обновить",left+33,top+50,60,()->BrowserRuntime.browserCommand("reload"));
  address=new ModSearchBox(font,left+96,top+50,Math.max(40,w-170),20,Component.literal("Адрес сайта"));address.setMaxLength(2048);address.setHint(Component.literal("https:// — адрес сайта"));address.setValue(BrowserRuntime.pageUrl());addRenderableWidget(address);
  pageGo=button("Открыть",left+w-71,top+50,63,this::openPage);
  pageClose=addRenderableWidget(new Button(left+w-30,top+28,22,20,Component.literal("Закрыть страницу"),b->BrowserRuntime.browserCommand("close"),message->message.get()){
   @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
    FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,UiTheme.ACCENT);
    int x=getX()+(getWidth()-7)/2,y=getY()+(getHeight()-7)/2,color=active?UiTheme.TEXT:UiTheme.MUTED;
    for(int i=0;i<7;i++){g.fill(x+i,y+i,x+i+1,y+i+1,color);g.fill(x+6-i,y+i,x+7-i,y+i+1,color);}
   }
  });
  pageClose.setTooltip(Tooltip.create(Component.literal("Закрыть страницу")));
  int qualityWidth=Math.max(24,(w-49)/4);
  qualityEnabled=button(ScreenQuality.enabled()?"Экраны: вкл":"Экраны: выкл",left+8+3*qualityWidth,top+28,qualityWidth-3,()->{ScreenQuality.toggleEnabled();if(!ScreenQuality.enabled())BrowserRuntime.stop();qualityEnabled.setMessage(Component.literal(ScreenQuality.enabled()?"Экраны: вкл":"Экраны: выкл"));});
  qualityHeight=button("Качество: "+ScreenQuality.height()+"p",left+8,top+28,qualityWidth-3,()->{ScreenQuality.cycleHeight();qualityHeight.setMessage(Component.literal("Качество: "+ScreenQuality.height()+"p"));});
  qualityFps=button("Кадры: "+ScreenQuality.fps()+" FPS",left+8+qualityWidth,top+28,qualityWidth-3,()->{ScreenQuality.cycleFps();qualityFps.setMessage(Component.literal("Кадры: "+ScreenQuality.fps()+" FPS"));});
  qualityApply=button("Применить",left+8+2*qualityWidth,top+28,qualityWidth-3,()->{if(!mayUseSelected())return;BrowserRuntime.applyQuality(minecraft.gameDirectory.toPath(),ScreenClient.mediaUrl(BrowserRuntime.selected()));});
  qualityHeight.setTooltip(Tooltip.create(Component.literal("Локальное разрешение рендера: 920p / 720p / 480p. Не меняет качество у других игроков.")));
  qualityFps.setTooltip(Tooltip.create(Component.literal("Локальная частота захвата: 60 / 30 / 24 FPS. Ниже — меньше нагрузка.")));
  qualityApply.setTooltip(Tooltip.create(Component.literal("Переподключить только ваш просмотр с выбранным качеством. Общий ролик не останавливается.")));
  int tools=(w-16)/5;play=button("▶",left+8,top+76,tools-3,()->BrowserRuntime.browserCommand("toggle"));seekBack=button("−10 с",left+8+tools,top+76,tools-3,()->BrowserRuntime.browserCommand("seek",-10));seekNext=button("+10 с",left+8+2*tools,top+76,tools-3,()->BrowserRuntime.browserCommand("seek",10));p2p=button("P2P: вкл",left+8+3*tools,top+76,tools-3,()->BrowserRuntime.browserCommand("p2p",!BrowserRuntime.p2p()));share=button("Раздача",left+8+4*tools,top+76,tools-3,()->BrowserRuntime.browserCommand("share",!BrowserRuntime.sharing()));
  int availableW=w-16,availableH=Math.max(1,h-138);vw=Math.max(1,Math.min(availableW,availableH*16/9));vh=Math.max(1,vw*9/16);
  var viewport=BrowserRuntime.viewport();vw=Math.max(1,Math.min(availableW,availableH*viewport.width()/viewport.height()));vh=Math.max(1,vw*viewport.height()/viewport.width());
  vx=left+(w-vw)/2;vy=top+106+(availableH-vh)/2;
  if(!autoStarted){autoStarted=true;start();}
 }
 private void openPage(){try{String url=address.getValue().trim();if(!url.contains("://"))url="https://"+url;BrowserEngine.webUri(url);if(!url.startsWith("https://"))throw new IllegalArgumentException("Для сайта нужна HTTPS-ссылка");menuError="";BrowserRuntime.browserCommand("navigate",url);}catch(RuntimeException e){menuError="Проверьте HTTPS-адрес сайта";}}
 private void start(){
  menuError="";if(!mayUseSelected()){menuError="Сервер не выдал доступ к общему проигрывателю. Откройте экран заново.";return;}
  String url=ScreenClient.mediaUrl(BrowserRuntime.selected());
  try{BrowserEngine.webUri(url);}catch(RuntimeException invalid){menuError="Сервер не выдал адрес общего проигрывателя.";return;}
  if(!BrowserEngine.supported())return;
  if(BrowserEngine.installed(minecraft.gameDirectory.toPath())){BrowserRuntime.start(minecraft.gameDirectory.toPath(),url);return;}
  minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes&&mayUseSelected())BrowserRuntime.start(minecraft.gameDirectory.toPath(),ScreenClient.mediaUrl(BrowserRuntime.selected()));},Component.literal("Скачать движок игровых экранов?"),Component.literal("Windows x64 · около 151 МБ. Один раз, отдельно от Edge/Chrome. Затем общий проигрыватель будет открываться автоматически.")));
 }
 private boolean mayUseSelected(){
  var id=BrowserRuntime.selected();if(minecraft.player==null||id==null||ScreenClient.mediaUrl(id).isEmpty())return false;
  return ScreenClient.screens(minecraft.level).stream().anyMatch(s->s.id().equals(id)&&(s.canUse()||ScreenClient.mediaGranted(id))&&s.geometry().distanceSquared(minecraft.player.getX(),minecraft.player.getY(),minecraft.player.getZ())<=ScreenInteraction.RANGE*ScreenInteraction.RANGE);
 }
 private void nextScreen(){
  var screens=ScreenInteraction.nearby();if(screens.isEmpty()){BrowserRuntime.select(null);return;}
  int index=-1;for(int i=0;i<screens.size();i++)if(screens.get(i).id().equals(BrowserRuntime.selected()))index=i;
  ScreenClient.requestOpen(screens.get((index+1)%screens.size()).id(),approved->{if(minecraft.gui.screen()==this&&ScreenInteraction.nearby().stream().anyMatch(s->s.id().equals(approved))){if(ScreenClient.mediaUrl(approved).isEmpty()){menuError="Общий проигрыватель недоступен на сервере.";return;}BrowserRuntime.select(approved);start();}});
 }
 @Override public void tick(){
  launch.active=BrowserEngine.supported()&&!BrowserRuntime.busy()&&mayUseSelected();
  choose.active=!ScreenInteraction.nearby().isEmpty();
  volume.active=BrowserRuntime.running();
  qualityApply.active=ScreenQuality.enabled()&&!BrowserRuntime.busy()&&mayUseSelected();
  launch.setMessage(Component.literal(BrowserRuntime.running()?"Переподключить":"Подключить"));
  boolean control=BrowserRuntime.browserReady()&&BrowserRuntime.browserCanControl();pageBack.active=pageReload.active=pageGo.active=pageClose.active=control;address.setEditable(control);
  play.active=seekBack.active=seekNext.active=control&&BrowserRuntime.sharedVideo();play.setMessage(Component.literal(BrowserRuntime.sharedPlaying()?"Пауза":"Играть"));
  p2p.active=BrowserRuntime.browserReady();share.active=p2p.active&&BrowserRuntime.p2p();p2p.setMessage(Component.literal(BrowserRuntime.p2p()?"P2P: вкл":"P2P: выкл"));share.setMessage(Component.literal(BrowserRuntime.sharing()?"Раздача: вкл":"Раздача: выкл"));
  if(!address.isFocused()&&!BrowserRuntime.pageUrl().equals(address.getValue()))address.setValue(BrowserRuntime.pageUrl());
 }
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
  extractTransparentBackground(g);RoundedPanels.fill(g,left,top,left+w,top+h,UiTheme.BACKGROUND,7);
  RoundedPanels.fill(g,left+7,top+30,left+w-7,top+100,UiTheme.SURFACE,4);
  g.fill(vx,vy,vx+vw,vy+vh,0xFF11151C);
  if(BrowserRuntime.running()){BrowserRuntime.wantFrame();var texture=BrowserRuntime.texture();if(texture!=null)g.blit(RenderPipelines.GUI_TEXTURED,texture,vx,vy,0f,0f,vw,vh,BrowserRuntime.WIDTH,BrowserRuntime.HEIGHT,BrowserRuntime.WIDTH,BrowserRuntime.HEIGHT);}
  super.extractRenderState(g,mx,my,delta);
  String status=!menuError.isEmpty()?menuError:BrowserEngine.supported()?BrowserRuntime.status():"Пока поддерживается только Windows x64";
  g.text(font,font.plainSubstrByWidth(status,w-16),left+8,top+h-24,UiTheme.MUTED,false);
  String metrics=BrowserRuntime.metrics();g.text(font,font.plainSubstrByWidth(metrics.isEmpty()?"Общий проигрыватель · синхронизация с сервером":metrics,w-16),left+8,top+h-13,UiTheme.MUTED,false);
 }
 private boolean inside(double x,double y){return BrowserRuntime.running()&&x>=vx&&y>=vy&&x<vx+vw&&y<vy+vh;}
 private void mouse(String type,double x,double y,int button,double dx,double dy){
  var event=new JsonObject();event.addProperty("type",type);event.addProperty("x",(int)((x-vx)*BrowserRuntime.WIDTH/vw));event.addProperty("y",(int)((y-vy)*BrowserRuntime.HEIGHT/vh));
  if(type.equals("mouseDown")||type.equals("mouseUp")){event.addProperty("button",button==1?"right":button==2?"middle":"left");event.addProperty("clickCount",1);}
  if(type.equals("mouseWheel")){event.addProperty("deltaX",dx);event.addProperty("deltaY",dy);}
  BrowserRuntime.input(event);
 }
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){focus=inside(e.x(),e.y());if(focus){setFocused(null);if(address!=null)address.setFocused(false);mouse("mouseDown",e.x(),e.y(),e.button(),0,0);return true;}return super.mouseClicked(e,twice);}
 @Override public boolean mouseReleased(MouseButtonEvent e){if(focus){mouse("mouseUp",e.x(),e.y(),e.button(),0,0);return true;}return super.mouseReleased(e);}
 @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(focus){mouse("mouseMove",e.x(),e.y(),e.button(),0,0);return true;}return super.mouseDragged(e,dx,dy);}
 @Override public void mouseMoved(double x,double y){if(inside(x,y)&&System.nanoTime()-lastMove>67_000_000L){lastMove=System.nanoTime();mouse("mouseMove",x,y,0,0,0);}super.mouseMoved(x,y);}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(inside(x,y)){mouse("mouseWheel",x,y,0,dx*80,dy*80);return true;}return super.mouseScrolled(x,y,dx,dy);}
 private boolean key(KeyEvent event,String type){
  if(!focus||event.key()==256)return false;
  String key=switch(event.key()){case 257,335->"Return";case 258->"Tab";case 259->"Backspace";case 261->"Delete";case 262->"Right";case 263->"Left";case 264->"Down";case 265->"Up";case 266->"PageUp";case 267->"PageDown";case 268->"Home";case 269->"End";case 340,344->"Shift";case 341,345->"Control";case 342,346->"Alt";default->event.key()>=32&&event.key()<=126?Character.toString((char)event.key()):"";};
  if(key.isEmpty())return false;
  var data=new JsonObject();data.addProperty("type",type);data.addProperty("keyCode",key);var mods=new JsonArray();if((event.modifiers()&1)!=0)mods.add("shift");if((event.modifiers()&2)!=0)mods.add("control");if((event.modifiers()&4)!=0)mods.add("alt");data.add("modifiers",mods);BrowserRuntime.input(data);return true;
 }
 // HTML fullscreen belongs to the browser texture, not this menu. Escape closes only the menu.
 @Override public boolean keyPressed(KeyEvent e){if(address!=null&&address.isFocused()&&(e.key()==257||e.key()==335)){openPage();return true;}return key(e,"keyDown")||super.keyPressed(e);}
 @Override public boolean keyReleased(KeyEvent e){return key(e,"keyUp")||super.keyReleased(e);}
 @Override public boolean charTyped(CharacterEvent e){if(focus){var data=new JsonObject();data.addProperty("type","char");data.addProperty("keyCode",e.codepointAsString());BrowserRuntime.input(data);return true;}return super.charTyped(e);}
 @Override public void removed(){BrowserRuntime.controls(false);focus=false;for(String key:List.of("Control","Shift","Alt")){var event=new JsonObject();event.addProperty("type","keyUp");event.addProperty("keyCode",key);BrowserRuntime.input(event);}}
 @Override public void onClose(){com.wildfire.client.music.AudioPreferences.save();minecraft.gui.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
