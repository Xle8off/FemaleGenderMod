package com.wildfire.client.screens;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.*;import java.net.*;import java.net.http.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.security.*;import java.time.Duration;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import java.nio.file.attribute.*;

/** One explicitly launched local browser. No network media distribution and no system-browser discovery. */
public final class BrowserRuntime {
 public static volatile int WIDTH=1280,HEIGHT=720;
 public static net.springplus.screens.ScreenViewport viewport(){var mc=Minecraft.getInstance();return ScreenQuality.scale(ScreenClient.screens(mc.level).stream().filter(s->s.id().equals(selected)).findFirst().map(s->net.springplus.screens.ScreenViewport.of(s.geometry().width(),s.geometry().height())).orElse(new net.springplus.screens.ScreenViewport(1280,720)));}
 private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("appearance_sync","cinema/local_browser");
 private static final ScheduledExecutorService WORK=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"SpringPlus-browser-host");t.setDaemon(true);return t;});
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 private record Endpoint(URI root,String token,int ticket){}
 private record Frame(int ticket,byte[] pixels,int width,int height){}
 private static final ArrayBlockingQueue<byte[]> buffers=new ArrayBlockingQueue<>(3);
 private static void recycle(Frame frame){if(frame!=null)buffers.offer(frame.pixels);}
 private static final AtomicReference<Frame> pending=new AtomicReference<>();
 private static volatile Endpoint endpoint;private static volatile Process process;
 private static volatile InputStream videoInput;
 private static volatile boolean fullscreen;
 private static volatile String streamFailure="";
 private static volatile double receiveMs,uploadMs,paintMs;
 private static volatile long received,uploaded;
 private static long metricsAt,metricsReceived,metricsUploaded;private static volatile String metrics="";
 private static volatile String status="Движок запускается только по нажатию кнопки";
 private static volatile boolean busy,muted=true;
 private static volatile boolean controlsWanted;
 private static volatile boolean browserReady,browserCanControl,sharedPlaying,sharedVideo,p2p=true,sharing=true;
 private static volatile String pageUrl="";
 public static boolean browserReady(){return browserReady;}
 public static boolean browserCanControl(){return browserCanControl;}
 public static boolean sharedPlaying(){return sharedPlaying;}
 public static boolean sharedVideo(){return sharedVideo;}
 public static boolean p2p(){return p2p;}
 public static boolean sharing(){return sharing;}
 public static String pageUrl(){return pageUrl;}
 public static void controls(boolean value){controlsWanted=value;var body=new JsonObject();body.addProperty("action","cinemaControls");body.addProperty("value",value);control(body);}
 private static volatile int generation;private static volatile long wanted;
 private static long lastFrame,lastStatus;private static Object world;private static UUID selected;
 private static BrowserTexture texture;
 private static final class BrowserTexture extends DynamicTexture{
  BrowserTexture(NativeImage image){super(()->"SpringPlus cinema browser",image);sampler=com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.LINEAR);}
 }
 static{WORK.schedule(BrowserRuntime::pump,100,TimeUnit.MILLISECONDS);Runtime.getRuntime().addShutdownHook(new Thread(BrowserRuntime::stop,"SpringPlus-browser-shutdown"));}
 private static void pump(){long started=System.nanoTime();try{poll();}finally{WORK.schedule(BrowserRuntime::pump,Math.max(1_000_000L,16_666_667L-(System.nanoTime()-started)),TimeUnit.NANOSECONDS);}}
 private BrowserRuntime(){}
 public static String status(){return status;}
 public static boolean fullscreen(){return fullscreen;}
 public static String metrics(){return metrics;}
 public static boolean busy(){return busy;}
 public static boolean running(){return process!=null&&process.isAlive();}
 public static boolean muted(){return muted;}
 public static UUID selected(){return selected;}
 public static void select(UUID id){selected=id;}
 public static void wantFrame(){wanted=System.nanoTime();}
 public static synchronized void applyQuality(Path game,String url){
  if(busy)return;Process previous=process;stop();final int ticket=generation;busy=true;
  WORK.execute(()->{try{if(previous!=null&&!previous.waitFor(4,TimeUnit.SECONDS))previous.destroyForcibly();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
   Minecraft.getInstance().execute(()->{if(ticket!=generation)return;busy=false;if(Minecraft.getInstance().level!=null)start(game,url);});
  });
 }
 public static Identifier texture(){uploadPending();return texture==null?null:TEXTURE;}
 public static synchronized void start(Path game,String url){
  if(!ScreenQuality.enabled()){status="Экраны отключены в настройках клиента";return;}
  URI checked;try{checked=BrowserEngine.webUri(url);}catch(RuntimeException e){status=e.getMessage();return;}
  if(busy)return;var viewport=viewport();if(running()){if(WIDTH==viewport.width()&&HEIGHT==viewport.height()){navigate(checked.toString());return;}stop();}
  WIDTH=viewport.width();HEIGHT=viewport.height();buffers.clear();Minecraft.getInstance().execute(BrowserRuntime::release);
  final int ticket=++generation;busy=true;status="Подготовка движка…";streamFailure="";muted=true;lastFrame=lastStatus=0;
  WORK.execute(()->{
   try{
    Path executable=BrowserEngine.install(game,s->{if(ticket==generation)status=s;},()->ticket!=generation);
    if(ticket!=generation)return;
    Path root=BrowserEngine.directory(game),profile=game.toAbsolutePath().normalize().resolve("config/springplus/browser-profiles/cinema");Files.createDirectories(profile);privateAccess(profile,true);
    byte[] host;try(var in=BrowserRuntime.class.getResourceAsStream("/assets/female_gender_mod/browser/host.cjs")){if(in==null)throw new IOException("Host resource missing");host=in.readAllBytes();}
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(host));Path script=root.resolve("host-"+hash.substring(0,16)+".cjs");
    if(!Files.exists(script))Files.write(script,host,StandardOpenOption.CREATE_NEW);
    byte[] random=new byte[32];new SecureRandom().nextBytes(random);String token=HexFormat.of().formatHex(random);
    Path handoff=Files.createTempFile(root,"launch-",".json"),ready=Files.createTempFile(root,"ready-",".port");Files.delete(ready);
    String launchUrl=checked.toString(),fresh=ScreenClient.mediaUrl();int fragment=launchUrl.indexOf('#');if(fragment>0&&launchUrl.substring(0,fragment).endsWith("/player")&&fresh.startsWith(launchUrl.substring(0,fragment)+"#"))launchUrl=fresh;
    String localTest=BrowserEngine.localTestUrl();if(!localTest.isEmpty())launchUrl=localTest;
    var config=new JsonObject();config.addProperty("token",token);config.addProperty("profile",profile.toString());config.addProperty("readyPath",ready.toString());config.addProperty("url",launchUrl);config.addProperty("parentPid",ProcessHandle.current().pid());config.addProperty("localTest",!localTest.isEmpty());
    try{
     config.addProperty("width",WIDTH);config.addProperty("height",HEIGHT);config.addProperty("fps",ScreenQuality.fps());config.addProperty("volume",volume);privateAccess(handoff,false);Files.writeString(handoff,config.toString(),StandardCharsets.UTF_8);
     var builder=new ProcessBuilder(executable.toString(),script.toString(),"--springplus-config="+handoff).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
     builder.environment().remove("ELECTRON_RUN_AS_NODE");builder.environment().remove("NODE_OPTIONS");
     status="Запускаем собственный Chromium…";Process child=builder.start();
     synchronized(BrowserRuntime.class){if(ticket!=generation){child.destroy();return;}process=child;}
     long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
     while(!Files.isRegularFile(ready)&&child.isAlive()&&ticket==generation&&System.nanoTime()<deadline)Thread.sleep(50);
     if(ticket!=generation){child.destroy();return;}
     if(!Files.isRegularFile(ready)||Files.size(ready)>8)throw new IOException("Browser startup timeout");
     int port=Integer.parseInt(Files.readString(ready).trim());if(port<1||port>65535)throw new IOException("Browser port");
     Endpoint active=new Endpoint(URI.create("http://127.0.0.1:"+port),token,ticket);endpoint=active;status="Инициализация страницы и медиакомпонентов…";
     volume(volume);Thread.ofVirtual().name("SpringPlus-browser-frames").start(()->receiveFrames(active));
    }finally{Files.deleteIfExists(handoff);Files.deleteIfExists(ready);}
   }catch(Exception e){if(ticket==generation){status="Не удалось запустить движок: "+e.getClass().getSimpleName();Process child=process;process=null;if(child!=null)child.destroy();}}
   finally{if(ticket==generation)busy=false;}
  });
 }
 private static HttpRequest.Builder request(Endpoint e,String route){return HttpRequest.newBuilder(e.root.resolve(route)).timeout(Duration.ofSeconds(3)).header("X-SpringPlus-Token",e.token);}
 private static void privateAccess(Path path,boolean directory)throws IOException{
  var view=Files.getFileAttributeView(path,AclFileAttributeView.class);if(view==null)throw new IOException("Private NTFS ACL required");
  var entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(view.getOwner()).setPermissions(EnumSet.allOf(AclEntryPermission.class));
  if(directory)entry.setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT);
  view.setAcl(List.of(entry.build()));
 }
 private static byte[] bounded(InputStream in,int max)throws IOException{try(in){byte[] bytes=in.readNBytes(max+1);if(bytes.length>max)throw new IOException("IPC response limit");return bytes;}}
 private static void receiveFrames(Endpoint e){
  final int frameWidth=WIDTH,frameHeight=HEIGHT,frameBytes=frameWidth*frameHeight*4;
  try{
   var response=HTTP.send(request(e,"/frames").GET().build(),HttpResponse.BodyHandlers.ofInputStream());
   try(var in=new DataInputStream(response.body())){
    if(response.statusCode()!=200||!response.headers().firstValue("Content-Type").orElse("").equals("application/x-springplus-frame-stream"))throw new IOException("Frame stream rejected");
    synchronized(BrowserRuntime.class){if(e.ticket!=generation)return;videoInput=in;}
    while(e.ticket==generation){
     long sequence=in.readLong();int length=in.readInt();
     if(length!=frameBytes)throw new IOException("Frame size mismatch");
     byte[] pixels=buffers.poll();if(pixels==null||pixels.length!=frameBytes)pixels=new byte[frameBytes];boolean handedOff=false;long began=System.nanoTime();
     try{
      in.readFully(pixels);if(e.ticket!=generation)return;
      if(sequence<=lastFrame)continue;
      BrowserPixels.bgraToRgba(pixels);lastFrame=sequence;
      recycle(pending.getAndSet(new Frame(e.ticket,pixels,frameWidth,frameHeight)));handedOff=true;received++;
      receiveMs=receiveMs*.9+(System.nanoTime()-began)/1_000_000.0*.1;
     }finally{if(!handedOff)buffers.offer(pixels);}
    }
   }
  }catch(Exception error){if(e.ticket==generation){streamFailure="Поток браузера: "+error.getClass().getSimpleName();status=streamFailure;}}
  finally{synchronized(BrowserRuntime.class){if(e.ticket==generation)videoInput=null;}}
 }
 private static void poll(){
  Endpoint e=endpoint;if(e==null||e.ticket!=generation)return;
  String stage="соединение";
  try{
   if(process==null||!process.isAlive()){endpoint=null;status="Процесс браузера завершился";return;}
   long now=System.nanoTime();
   if(now-lastStatus>250_000_000L){
    String localTest=BrowserEngine.localTestUrl();if(!localTest.isEmpty())renewMedia(localTest);
    lastStatus=now;var response=HTTP.send(request(e,"/status").header("X-SpringPlus-Viewing",now-wanted<3_000_000_000L?"1":"0").header("X-SpringPlus-Audio-Gain",Float.toString(ScreenAudio.gain())).header("X-SpringPlus-Controls",controlsWanted?"1":"0").GET().build(),HttpResponse.BodyHandlers.ofInputStream());
    if(response.statusCode()!=200){bounded(response.body(),8192);throw new IOException("Host authentication");}
    var state=JsonParser.parseString(new String(bounded(response.body(),8192),StandardCharsets.UTF_8)).getAsJsonObject();
    if(e.ticket!=generation)return;
    String phase=state.get("status").getAsString(),error=state.get("error").getAsString();
    fullscreen=state.has("fullscreen")&&state.get("fullscreen").getAsBoolean();paintMs=state.has("paintMs")?state.get("paintMs").getAsDouble():0;
    if(metricsAt!=0){double seconds=(now-metricsAt)/1_000_000_000.0;metrics=String.format(Locale.ROOT,"Кадры: %.0f/%.0f FPS · Chromium %.1f · приём %.1f · загрузка %.1f мс",(received-metricsReceived)/seconds,(uploaded-metricsUploaded)/seconds,paintMs,receiveMs,uploadMs);}
    metricsAt=now;metricsReceived=received;metricsUploaded=uploaded;
    status=!streamFailure.isEmpty()?streamFailure:!error.isEmpty()?error:phase.equals("ready")?"Общий браузер готов":phase.equals("components")?"Подготовка Widevine…":"Загрузка браузера…";
    if(state.has("cinema")){var c=state.getAsJsonObject("cinema");browserReady=c.has("ready")&&c.get("ready").getAsBoolean();browserCanControl=c.has("canControl")&&c.get("canControl").getAsBoolean();sharedPlaying=c.has("playing")&&c.get("playing").getAsBoolean();sharedVideo=c.has("hasVideo")&&c.get("hasVideo").getAsBoolean();p2p=!c.has("p2p")||c.get("p2p").getAsBoolean();sharing=c.has("share")&&c.get("share").getAsBoolean();pageUrl=c.has("url")?c.get("url").getAsString():"";if(error.isEmpty()&&streamFailure.isEmpty()&&c.has("status")&&!c.get("status").getAsString().isBlank())status=c.get("status").getAsString();}
   }
  }catch(Exception error){if(e.ticket==generation)status="Локальный браузер · "+stage+": "+error.getClass().getSimpleName();}
 }
 public static void tick(Object currentWorld){
  if(!ScreenQuality.enabled()){if(running()||busy)stop();if(texture!=null)release();ScreenAudio.tick();world=currentWorld;return;}
  ScreenAudio.tick();
  if(world!=currentWorld){com.wildfire.client.music.MusicPriority.reset();stop();world=currentWorld;selected=null;release();}
  if(!running()&&!busy&&texture!=null)release();
 }
 // Called by GUI/world rendering, not the 20 Hz game tick. Upload only the latest frame.
 private static void uploadPending(){
  var frame=pending.getAndSet(null);if(frame==null)return;
  try{
   if(frame.ticket!=generation||!running())return;
   long began=System.nanoTime();
   if(texture!=null&&(texture.getPixels().getWidth()!=frame.width||texture.getPixels().getHeight()!=frame.height)){Minecraft.getInstance().getTextureManager().release(TEXTURE);texture=null;}
   if(texture==null){texture=new BrowserTexture(new NativeImage(frame.width,frame.height,false));Minecraft.getInstance().getTextureManager().register(TEXTURE,texture);}
   texture.getPixels().getPixelBytes().put(frame.pixels);texture.upload();
   uploaded++;uploadMs=uploadMs*.9+(System.nanoTime()-began)/1_000_000.0*.1;
  }finally{recycle(frame);}
 }
 private static void release(){if(texture!=null){Minecraft.getInstance().getTextureManager().release(TEXTURE);texture=null;}recycle(pending.getAndSet(null));}
 public static void navigate(String url){try{var body=new JsonObject();body.addProperty("action","navigate");body.addProperty("url",BrowserEngine.webUri(url).toString());control(body);}catch(RuntimeException e){status=e.getMessage();}}
 private static volatile float volume=com.wildfire.client.music.AudioPreferences.screenVolume();
 public static float volume(){return volume;}
 public static void volume(float value){volume=Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;com.wildfire.client.music.AudioPreferences.screenVolume(volume);muted=volume==0;var body=new JsonObject();body.addProperty("action","volume");body.addProperty("value",volume);control(body);}
 public static void mute(){volume(muted?1:0);}
 public static void input(JsonObject event){var body=new JsonObject();body.addProperty("action","input");body.add("event",event);control(body);}
 public static void action(String action){var body=new JsonObject();body.addProperty("action",action);control(body);}
 public static void browserCommand(String operation){browserCommand(operation,null);}
 public static void browserCommand(String operation,Object value){var command=new JsonObject();command.addProperty("operation",operation);if(value instanceof String s)command.addProperty("value",s);else if(value instanceof Boolean b)command.addProperty("value",b);else if(value instanceof Number n)command.addProperty("value",n);var body=new JsonObject();body.addProperty("action","cinemaCommand");body.add("command",command);control(body);}
 public static void renewMedia(String url){var body=new JsonObject();body.addProperty("action","cinemaTicket");body.addProperty("url",url);control(body);}
 private static void control(JsonObject body){
  Endpoint e=endpoint;if(e==null)return;
  // Bounded queue: mouse movement is sent by UI clicks/drags, never every render frame.
  if(QUEUED.incrementAndGet()>32){QUEUED.decrementAndGet();return;}
  WORK.execute(()->{try{if(e.ticket!=generation)return;var response=HTTP.send(request(e,"/control").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofInputStream());bounded(response.body(),8192);}catch(Exception ignored){}finally{QUEUED.decrementAndGet();}});
 }
 private static final AtomicInteger QUEUED=new AtomicInteger();
 public static synchronized void stop(){
  browserReady=browserCanControl=sharedPlaying=sharedVideo=false;pageUrl="";
  ++generation;busy=false;Endpoint e=endpoint;endpoint=null;Process child=process;process=null;fullscreen=false;
  InputStream stream=videoInput;videoInput=null;if(stream!=null)try{stream.close();}catch(IOException ignored){}
  streamFailure="";metrics="";metricsAt=0;receiveMs=uploadMs=paintMs=0;
  recycle(pending.getAndSet(null));status="Браузер остановлен";
  if(child==null)return;
  Thread.ofVirtual().name("SpringPlus-browser-stop").start(()->{
   try{if(e!=null){var body=new JsonObject();body.addProperty("action","stop");var response=HTTP.send(request(e,"/control").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofInputStream());bounded(response.body(),8192);}if(!child.waitFor(3,TimeUnit.SECONDS))child.destroy();}
   catch(Exception ignored){child.destroy();}
  });
 }
}
