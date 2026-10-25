package com.wildfire.gui;
import com.wildfire.client.music.SpringPlusSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
/** Centered appearance upload dialog; decoding and HTTPS stay off the render thread. */
public final class PlayerAssetsScreen extends Screen {
 private static final int LIMIT=8*1024*1024;
 private static final java.util.concurrent.atomic.AtomicBoolean WORKING=new java.util.concurrent.atomic.AtomicBoolean();
 private static final ExecutorService WORK=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"SpringPlus-player-assets");t.setDaemon(true);return t;});
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 private final Screen parent;private final Consumer<String> request;
 private final Object connection=Minecraft.getInstance().getConnection();
 private String kind="skin",status="Выберите PNG скина или PNG/GIF плаща",filename="";
 private byte[] selected;private boolean busy,confirmRemove,disposed;
 private boolean loadingCurrent,initialLoaded;
 private record Asset(DynamicTexture texture,Identifier id,int width,int height,CapeGifPreview animation,long started,int frame){}
 private final java.util.Map<String,Asset> previews=new java.util.HashMap<>();
 private float rotation=180;private boolean rotating;
 private String confirmKind="";
 private boolean embedded;private int stageX,stageY,stageW,stageH;
 private final java.util.List<Button> embeddedButtons=new java.util.ArrayList<>();
 public void mount(int x,int y,int width,int height,Consumer<Button> add){
  embedded=true;disposed=false;
  left=x;top=y;w=width;h=height;embeddedButtons.clear();
  skin=FlatButtons.builder(Component.literal("Загрузить скин"),b->{selectKind("skin");choose();}).bounds(x,y+30,width,22).build();
  cape=FlatButtons.builder(Component.literal("Загрузить плащ"),b->{selectKind("cape");choose();}).bounds(x,y+58,width,22).build();
  choose=FlatButtons.builder(Component.literal("Снять скин"),b->removeAsset("skin")).bounds(x,y+92,width,20).build();
  remove=FlatButtons.builder(Component.literal("Снять плащ"),b->removeAsset("cape")).bounds(x,y+116,width,20).build();
  install=FlatButtons.builder(Component.literal("Установить выбранное"),b->submit(false)).bounds(x,y+150,width,22).build();
  for(var button:java.util.List.of(skin,cape,choose,remove,install)){embeddedButtons.add(button);add.accept(button);}
  update();if(!initialLoaded){initialLoaded=true;loadCurrent();}
 }
 public void visible(boolean value){for(var button:embeddedButtons)button.visible=value;}
 public void renderEmbedded(GuiGraphicsExtractor g,int x,int y,int width,int height,int mx,int my,float delta){
  stageX=x;stageY=y;stageW=width;stageH=height;extractRenderState(g,mx,my,delta);
 }
 private CapeGifPreview animation;private long animationStarted;private int shownFrame=-1;
 private int left,top,w,h,iw,ih;private Button choose,install,remove,skin,cape;
 private DynamicTexture texture;private Identifier textureId=newTextureId();
 private static Identifier newTextureId(){return Identifier.fromNamespaceAndPath("appearance_sync","asset_preview/"+java.util.UUID.randomUUID().toString().replace("-",""));}
 private PreviewMannequin mannequin;
 private static final class PreviewMannequin extends net.minecraft.client.entity.ClientMannequin {
  PreviewMannequin(net.minecraft.client.Minecraft client){super(client.level,client.playerSkinRenderCache());setId(1);}
  void previewSkin(net.minecraft.world.entity.player.PlayerSkin skin){setSkin(skin);}
 }
 public PlayerAssetsScreen(Screen parent,Consumer<String> request){super(Component.literal("Скины и плащи"));this.parent=parent;this.request=request;}
 @Override protected void init(){
  w=Math.min(360,width-24);h=Math.min(300,height-24);left=(width-w)/2;top=(height-h)/2;
  addRenderableWidget(new Button(left+w-29,top+7,21,20,Component.literal("Закрыть"),b->onClose(),message->message.get()){
   @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
    FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,UiTheme.ACCENT);
    int x=getX()+(getWidth()-7)/2,y=getY()+(getHeight()-7)/2;
    for(int i=0;i<7;i++){g.fill(x+i,y+i,x+i+1,y+i+1,UiTheme.TEXT);g.fill(x+6-i,y+i,x+7-i,y+i+1,UiTheme.TEXT);}
   }
  });
  skin=addRenderableWidget(FlatButtons.builder(Component.literal("Загрузить скин"),b->{selectKind("skin");choose();}).bounds(left+12,top+h-108,(w-30)/2,20).build());
  cape=addRenderableWidget(FlatButtons.builder(Component.literal("Загрузить плащ"),b->{selectKind("cape");choose();}).bounds(left+18+(w-30)/2,top+h-108,(w-30)/2,20).build());
  choose=addRenderableWidget(FlatButtons.builder(Component.literal("Снять скин"),b->removeAsset("skin")).bounds(left+12,top+h-82,(w-30)/2,20).build());
  install=addRenderableWidget(FlatButtons.builder(Component.literal("Установить"),b->submit(false)).bounds(left+12,top+h-34,(w-30)/2,20).build());
  remove=addRenderableWidget(FlatButtons.builder(Component.literal("Снять плащ"),b->removeAsset("cape")).bounds(left+18+(w-30)/2,top+h-82,(w-30)/2,20).build());
  install.setWidth(w-24);
  update();
  if(!initialLoaded){initialLoaded=true;loadCurrent();}
 }
 private void update(){if(choose==null)return;choose.active=!busy;install.active=!busy&&selected!=null;remove.active=!busy;skin.active=!busy;cape.active=!busy;}
 private void removeAsset(String asset){if(!confirmKind.equals(asset)){confirmKind=asset;status=asset.equals("skin")?"Нажмите «Снять скин» ещё раз":"Нажмите «Снять плащ» ещё раз";return;}selectKind(asset);submit(true);}
 private void remember(){if(texture!=null)previews.put(kind,new Asset(texture,textureId,iw,ih,animation,animationStarted,shownFrame));}
 private void release(){previews.remove(kind);animation=null;shownFrame=-1;if(texture!=null){minecraft.getTextureManager().release(textureId);texture=null;}}
 private void selectKind(String next){remember();kind=next;selected=null;filename="";var saved=previews.get(next);
  texture=saved==null?null:saved.texture();textureId=saved==null?newTextureId():saved.id();
  animation=saved==null?null:saved.animation();iw=saved==null?0:saved.width();ih=saved==null?0:saved.height();
  animationStarted=saved==null?0:saved.started();shownFrame=saved==null?-1:saved.frame();update();}
 private void loadCurrent(){
  String asset=kind;busy=true;loadingCurrent=true;status="Загружаем установленное с сайта…";update();
  var grant=PlayerAssetUpload.request(asset,request);
  WORK.execute(()->{try{
   String token=grant.get(20,TimeUnit.SECONDS);
   if(Minecraft.getInstance().getConnection()!=connection||disposed)return;
   var response=HTTP.send(HttpRequest.newBuilder(URI.create("https://skin.springplus.net/api/player-assets/"+token))
    .timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
   byte[] bytes;try(var in=response.body()){bytes=in.readNBytes(LIMIT+1);}
   if(response.statusCode()==404){Minecraft.getInstance().execute(()->currentLoaded(asset));return;}
   if(response.statusCode()!=200||bytes.length>LIMIT)throw new IOException("Не удалось получить установленный файл");
   CapeGifPreview loop=bytes.length>=6&&new String(bytes,0,6,java.nio.charset.StandardCharsets.US_ASCII).startsWith("GIF")?CapeGifPreview.decode(bytes):null;
   int x,y;
   try(var in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
    var readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw new IOException("Неверный формат файла");
    var reader=readers.next();try{reader.setInput(in,true,true);x=loop==null?reader.getWidth(0):loop.width();y=loop==null?reader.getHeight(0):loop.height();}finally{reader.dispose();}
   }
   if(x<64||x>2048||Integer.bitCount(x)!=1||y!=(asset.equals("skin")?x:x/2))throw new IOException("Неверный размер файла");
   int imageWidth=x,imageHeight=y;
   Minecraft.getInstance().execute(()->{if(disposed||Minecraft.getInstance().getConnection()!=connection)return;
    try{release();texture=new DynamicTexture(()->"Installed player asset",loop==null?NativeImage.read(bytes):new NativeImage(imageWidth,imageHeight,true));
     minecraft.getTextureManager().register(textureId,texture);iw=imageWidth;ih=imageHeight;
     animation=loop;animationStarted=System.nanoTime();shownFrame=-1;
     if(loop==null)texture.upload();else animate();
     status=(asset.equals("skin")?"Текущий скин с сайта":"Текущий плащ с сайта")+(loop==null?"":" · GIF loop");
    }catch(Exception error){status="Не удалось открыть установленный файл";}finally{currentLoaded(asset);}
   });
  }catch(Exception error){fail(error.getCause()!=null?error.getCause().getMessage():error.getMessage());}});
 }
 private void currentLoaded(String asset){if(disposed)return;remember();busy=false;loadingCurrent=false;
  if(asset.equals("skin")){selectKind("cape");loadCurrent();}else{selectKind("skin");status="Перетащите персонажа, чтобы повернуть";update();}}
 private void choose(){
  if(!WORKING.compareAndSet(false,true))return;busy=true;update();
  String asset=kind;
  WORK.execute(()->{try{
   String path=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(asset.equals("skin")?"Скин PNG":"Плащ PNG / GIF",null,(org.lwjgl.PointerBuffer)null,"PNG / GIF",false);
   if(path==null){Minecraft.getInstance().execute(()->{busy=false;update();});return;}
   Path file=Path.of(path);if(!Files.isRegularFile(file)||Files.size(file)>LIMIT)throw new IOException("Максимум 8 МБ");
   byte[] bytes;try(var in=Files.newInputStream(file)){bytes=in.readNBytes(LIMIT+1);}if(bytes.length>LIMIT)throw new IOException("Максимум 8 МБ");
   byte[] preview;int x,y;CapeGifPreview gif=null;
   try(var in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
    var readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw new IOException("Нужен PNG или GIF");
    var reader=readers.next();try{
     reader.setInput(in,true,true);String format=reader.getFormatName();
     if(format.equalsIgnoreCase("gif")&&asset.equals("cape"))gif=CapeGifPreview.decode(bytes);
     if(!format.equalsIgnoreCase("png")&&!(asset.equals("cape")&&format.equalsIgnoreCase("gif")))throw new IOException("Неверный формат");
     x=gif==null?reader.getWidth(0):gif.width();y=gif==null?reader.getHeight(0):gif.height();
     if(x<64||x>2048||Integer.bitCount(x)!=1||y!=(asset.equals("skin")?x:x/2))throw new IOException(asset.equals("skin")?"Нужен квадратный скин 64–2048 px":"Нужен плащ 2:1, ширина 64–2048 px");
     var output=new ByteArrayOutputStream();ImageIO.write(reader.read(0),"png",output);preview=output.toByteArray();
    }finally{reader.dispose();}
   }
   int imageWidth=x,imageHeight=y;CapeGifPreview loop=gif;
   Minecraft.getInstance().execute(()->{if(disposed||Minecraft.getInstance().getConnection()!=connection)return;
    try{release();texture=new DynamicTexture(()->"Player asset preview",loop==null?NativeImage.read(preview):new NativeImage(imageWidth,imageHeight,true));minecraft.getTextureManager().register(textureId,texture);texture.upload();
     iw=imageWidth;ih=imageHeight;animation=loop;animationStarted=System.nanoTime();shownFrame=-1;animate();
     selected=bytes;filename=file.getFileName().toString();remember();status="Предпросмотр файла · "+iw+" × "+ih+(loop==null?"":" · GIF loop");
    }catch(Exception error){status="Не удалось открыть предпросмотр";}finally{busy=false;update();}
   });
  }catch(Exception error){fail(error.getMessage());}finally{WORKING.set(false);}});
 }
 private void fail(String text){Minecraft.getInstance().execute(()->{if(disposed)return;status=text==null?"Не удалось загрузить файл":text;busy=false;loadingCurrent=false;update();});}
 private void submit(boolean clearing){
  if(busy||!SpringPlusSession.enabled()||Minecraft.getInstance().getConnection()!=connection)return;
  if(!WORKING.compareAndSet(false,true))return;busy=true;status="Получаем разрешение…";update();
  byte[] bytes=selected;String asset=kind;
  var grant=PlayerAssetUpload.request(asset,request);
  WORK.execute(()->{try{
   String token=grant.get(20,TimeUnit.SECONDS);
   if(Minecraft.getInstance().getConnection()!=connection)throw new IOException("Соединение изменилось");
   var builder=HttpRequest.newBuilder(URI.create("https://skin.springplus.net/api/player-assets/"+token)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/octet-stream");
   var httpRequest=clearing?builder.DELETE().build():builder.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
   var response=HTTP.send(httpRequest,HttpResponse.BodyHandlers.ofInputStream());
   byte[] reply;try(var in=response.body()){reply=in.readNBytes(4097);}
   if(reply.length>4096)throw new IOException("Некорректный ответ сайта");
   var data=com.google.gson.JsonParser.parseString(new String(reply,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
   if(response.statusCode()!=200||!data.has("ok")||!data.get("ok").getAsBoolean()){
    String code=data.has("error")?data.get("error").getAsString():"UPLOAD_FAILED";
    throw new IOException(switch(code){case "NO_PERMISSION","NO_CAPE_PERMISSION"->"Нет права на это HD-разрешение";case "NO_GIF_PERMISSION"->"Нет права на GIF-плащи";case "INVALID_TICKET"->"Разрешение истекло. Повторите";case "RATE_LIMITED"->"Подождите несколько секунд";default->"Сайт отклонил загрузку: "+code;});
   }
   Minecraft.getInstance().execute(()->{if(disposed)return;
    if(clearing){release();previews.remove(asset);}selected=null;filename="";confirmRemove=false;confirmKind="";
    status=clearing?"Снято. Обновление отправлено в игру":"Установлено. Обновление отправлено в игру";busy=false;update();
   });
  }catch(Exception error){fail(error.getCause()!=null?error.getCause().getMessage():error.getMessage());}finally{WORKING.set(false);}});
 }
 @Override public void tick(){if(!SpringPlusSession.enabled()||Minecraft.getInstance().getConnection()!=connection)Minecraft.getInstance().gui.setScreen(null);}
 @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0x90000000);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
  animate();
  if(!embedded){FlatButtons.plate(g,left,top,w,h,true,false,false,UiTheme.ACCENT);
  g.text(font,getTitle(),left+(w-font.width(getTitle()))/2,top+12,UiTheme.TEXT,false);}
  remember();
  var animatedCape=previews.get("cape");
  if(animatedCape!=null&&animatedCape.animation()!=null&&texture!=animatedCape.texture()){
   int frame=animatedCape.animation().frameAt((System.nanoTime()-animatedCape.started())/1_000_000);
   if(frame!=animatedCape.frame()){
    var image=animatedCape.texture().getPixels();if(image!=null){int[] pixels=animatedCape.animation().frames().get(frame).argb();
     for(int y=0;y<animatedCape.height();y++)for(int x=0;x<animatedCape.width();x++)image.setPixel(x,y,pixels[y*animatedCape.width()+x]);animatedCape.texture().upload();}
    previews.put("cape",new Asset(animatedCape.texture(),animatedCape.id(),animatedCape.width(),animatedCape.height(),animatedCape.animation(),animatedCape.started(),frame));
   }
  }
  int pw=Math.min(132,w-24),ph=Math.min(138,Math.max(1,h-158)),px=left+(w-pw)/2,py=top+36;
  if(embedded){px=stageX;py=stageY;pw=stageW;ph=stageH;}
  RoundedPanels.fill(g,px,py,px+pw,py+ph,0xFF11151D,4);
  if(minecraft.level!=null&&minecraft.player!=null){
   var original=minecraft.player.getSkin();
   var model=original.model();
   var skinPreview=previews.get("skin");var capePreview=previews.get("cape");
   var pixels=skinPreview==null?null:skinPreview.texture().getPixels();
   if(pixels!=null){int scale=Math.max(1,skinPreview.width()/64);
    boolean slim=true;for(int y=20*scale;y<32*scale;y++)for(int x=54*scale;x<55*scale;x++)if((pixels.getPixel(x,y)>>>24)!=0)slim=false;
    model=slim?net.minecraft.world.entity.player.PlayerModelType.SLIM:net.minecraft.world.entity.player.PlayerModelType.WIDE;
   }
   var body=skinPreview==null?original.body():new net.minecraft.core.ClientAsset.ResourceTexture(skinPreview.id(),skinPreview.id());
   var cloak=capePreview==null?null:new net.minecraft.core.ClientAsset.ResourceTexture(capePreview.id(),capePreview.id());
   // Extract the real player's full render state, exactly as the clothing preview does.
   // Override only the skin assets of this detached GUI state; never mutate the world player.
   var state=net.minecraft.client.gui.screens.inventory.InventoryScreen.extractRenderState(minecraft.player);
   if(state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar){
    avatar.skin=new net.minecraft.world.entity.player.PlayerSkin(body,cloak,cloak,model,false);
    // A wardrobe preview must not inherit the direction in which the player happened
    // to be looking in the world. Start square to the camera; dragging still rotates it.
    avatar.bodyRot=0f;avatar.yRot=0f;avatar.xRot=0f;
   }
   g.entity(state,embedded?Math.min(86,Math.max(20,(ph-12)/2.1f)):Math.min(45,Math.max(20,ph/3f)),new org.joml.Vector3f(0,.9f,0),
    new org.joml.Quaternionf().rotationZ((float)Math.PI).rotateY((float)Math.toRadians(rotation)),null,px,py,px+pw,py+ph);
  }else g.text(font,"—",px+(pw-font.width("—"))/2,py+ph/2,UiTheme.MUTED,false);
  if(embedded&&install.visible){
   int lineY=top+180;for(var line:font.split(Component.literal(status),Math.max(1,w))) {if(lineY+9>top+h)break;g.text(font,line,left,lineY,UiTheme.TEXT,false);lineY+=10;}
  }else if(!embedded){
   g.text(font,font.plainSubstrByWidth(status,w-24),left+12,top+h-55,UiTheme.TEXT,false);
   if(!filename.isEmpty())g.text(font,font.plainSubstrByWidth(filename,w-24),left+12,top+h-96,UiTheme.MUTED,false);
   super.extractRenderState(g,mx,my,delta);
  }
 }
 @Override public void onClose(){if(!busy||loadingCurrent)Minecraft.getInstance().gui.setScreen(parent);}
 @Override public void removed(){disposed=true;if(loadingCurrent)PlayerAssetUpload.cancel();remember();
  for(var asset:previews.values())minecraft.getTextureManager().release(asset.id());previews.clear();texture=null;animation=null;}
 @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick){
  int previewWidth=Math.min(132,w-24),previewHeight=Math.min(138,Math.max(1,h-158));
  if(embedded){if(event.button()==0&&event.x()>=stageX&&event.x()<stageX+stageW&&event.y()>=stageY&&event.y()<stageY+stageH){rotating=true;return true;}return false;}
  if(event.button()==0&&event.x()>=left+(w-previewWidth)/2&&event.x()<left+(w+previewWidth)/2&&event.y()>=top+36&&event.y()<top+36+previewHeight){rotating=true;return true;}
  return super.mouseClicked(event,doubleClick);
 }
 @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,double dx,double dy){
  if(rotating){rotation=(rotation+(float)dx*2)%360;return true;}return super.mouseDragged(event,dx,dy);
 }
 @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){if(rotating){rotating=false;return true;}return super.mouseReleased(event);}
 @Override public boolean isPauseScreen(){return false;}
 private void animate(){
  if(animation==null||texture==null)return;
  int frame=animation.frameAt((System.nanoTime()-animationStarted)/1_000_000);
  if(frame==shownFrame)return;
  var image=texture.getPixels();if(image==null)return;
  int[] pixels=animation.frames().get(frame).argb();
  for(int y=0;y<ih;y++)for(int x=0;x<iw;x++)image.setPixel(x,y,pixels[y*iw+x]);
  texture.upload();shownFrame=frame;
 }
}
