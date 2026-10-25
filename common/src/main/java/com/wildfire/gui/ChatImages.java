package com.wildfire.gui;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One-use upload credentials; no server secret or arbitrary download URL enters the mod. */
public final class ChatImages {
    private static final String BASE="https://skin.springplus.net/api/chat-images/";
    private static final int LIMIT=2*1024*1024;
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"SpringPlus-chat-images");t.setDaemon(true);return t;});
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private record Texture(Identifier id,int width,int height){}
    private static final Map<String,Texture> textures=new LinkedHashMap<>();
    private static final Set<String> loading=new HashSet<>();
    private static final Map<String,Long> retry=new HashMap<>();
    private static CompletableFuture<String> pendingTicket;
    private static final java.util.concurrent.atomic.AtomicBoolean choosing=new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean uploading=new java.util.concurrent.atomic.AtomicBoolean();
    private interface WindowsClipboard extends com.sun.jna.Library {boolean IsClipboardFormatAvailable(int format);}
    public static boolean hasClipboardImage(){
        try{
            if(System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")){
                var api=com.sun.jna.Native.load("user32",WindowsClipboard.class);
                return api.IsClipboardFormatAvailable(8)||api.IsClipboardFormatAvailable(17)||api.IsClipboardFormatAvailable(2);
            }
            return Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(DataFlavor.imageFlavor);
        }catch(Throwable unavailable){return false;}
    }
    public static String imageId(String body){
        String prefix=BASE;
        if(body==null||!body.startsWith(prefix)||!body.endsWith(".png"))return null;
        String id=body.substring(prefix.length(),body.length()-4);return id.matches("[a-f0-9]{64}")?id:null;
    }
    public static void ticket(JsonObject data){
        if(pendingTicket==null)return;
        if(data.has("token")&&data.get("token").getAsString().matches("[a-f0-9]{64}"))pendingTicket.complete(data.get("token").getAsString());
        else pendingTicket.completeExceptionally(new IOException("Загрузка изображений недоступна. Обновите SkinSystem и сайт."));
    }
    private static byte[] png(BufferedImage image)throws IOException{
        int w=image.getWidth(),h=image.getHeight();if(w<1||h<1||(long)w*h>16777216L)throw new IOException("Слишком большое изображение");
        double scale=Math.min(1,2048d/Math.max(w,h));
        var resized=new BufferedImage(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),BufferedImage.TYPE_INT_ARGB);
        var g=resized.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(image,0,0,resized.getWidth(),resized.getHeight(),null);g.dispose();
        var out=new ByteArrayOutputStream();ImageIO.write(resized,"png",out);if(out.size()>LIMIT)throw new IOException("Изображение после сжатия больше 2 МБ");return out.toByteArray();
    }
    private static BufferedImage read(byte[] bytes)throws IOException{
        if(bytes.length>8*1024*1024)throw new IOException("Файл больше 8 МБ");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Нужен PNG или JPG");
            var reader=readers.next();try{reader.setInput(input,true,true);String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("jpeg"))throw new IOException("Нужен PNG или JPG");
                int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||(long)w*h>16777216L)throw new IOException("Слишком большое изображение");return reader.read(0);
            }finally{reader.dispose();}
        }
    }
    private static byte[] clipboard()throws Exception{
        if(System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")){
            // Windows clipboard API works even when the Minecraft JVM enables AWT headless mode.
            var process=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-STA","-Command",
                "Add-Type -AssemblyName System.Windows.Forms; $image=[Windows.Forms.Clipboard]::GetImage(); if($null -eq $image){exit 2}; if(([long]$image.Width*$image.Height) -gt 16777216){exit 3}; $stream=[IO.MemoryStream]::new(); $image.Save($stream,[Drawing.Imaging.ImageFormat]::Png); [Console]::OpenStandardOutput().Write($stream.ToArray(),0,[int]$stream.Length); $image.Dispose(); $stream.Dispose()").redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try(var stream=process.getInputStream()){
                byte[] bytes=stream.readNBytes(8*1024*1024+1);if(!process.waitFor(5,TimeUnit.SECONDS)||process.exitValue()!=0||bytes.length>8*1024*1024)throw new IOException("В буфере нет подходящей картинки");return png(read(bytes));
            }finally{process.destroyForcibly();}
        }
        Object value=Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.imageFlavor);
        if(!(value instanceof Image image))throw new IOException("В буфере нет картинки");
        int w=image.getWidth(null),h=image.getHeight(null);if(w<1||h<1||(long)w*h>16777216L)throw new IOException("Слишком большое изображение");
        var result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);var g=result.createGraphics();g.drawImage(image,0,0,null);g.dispose();return png(result);
    }
    public static void choose(Screen parent,boolean paste,Runnable requestTicket,Consumer<String> send){
        if(!choosing.compareAndSet(false,true))return;
        WORK.execute(()->{try{
            byte[] bytes;
            if(paste)bytes=clipboard();else{
                String selected=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Изображение PNG / JPG",null,(org.lwjgl.PointerBuffer)null,"PNG / JPG",false);
                if(selected==null)return;Path file=Path.of(selected);if(Files.size(file)>8*1024*1024)throw new IOException("Файл больше 8 МБ");bytes=png(read(Files.readAllBytes(file)));
            }
            Minecraft.getInstance().execute(()->{if(Minecraft.getInstance().gui.screen()==parent)Minecraft.getInstance().gui.setScreen(new Preview(parent,bytes,requestTicket,send));});
        }catch(Exception error){Minecraft.getInstance().execute(()->{if(Minecraft.getInstance().gui.screen()==parent)Minecraft.getInstance().gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ok->Minecraft.getInstance().gui.setScreen(parent),Component.literal("Не удалось открыть изображение"),Component.literal(error.getMessage()==null?"Буфер недоступен":error.getMessage()),Component.literal("ОК"),Component.literal("Назад")));});}finally{choosing.set(false);}});
    }
    private static byte[] download(String id)throws Exception{
        var response=HTTP.send(HttpRequest.newBuilder(URI.create(BASE+id+".png")).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()){byte[] bytes=input.readNBytes(LIMIT+1);if(response.statusCode()!=200||bytes.length>LIMIT)throw new IOException("Изображение недоступно");return bytes;}
    }
    private static void register(String key,byte[] bytes)throws Exception{
        register(key,bytes,512);
    }
    private static void register(String key,byte[] bytes,int maxEdge)throws Exception{
        BufferedImage image=read(bytes);int w=image.getWidth(),h=image.getHeight();double scale=Math.min(1,(double)maxEdge/Math.max(w,h));
        var small=new BufferedImage(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),BufferedImage.TYPE_INT_ARGB);var g=small.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(image,0,0,small.getWidth(),small.getHeight(),null);g.dispose();
        var out=new ByteArrayOutputStream();ImageIO.write(small,"png",out);NativeImage nativeImage=NativeImage.read(out.toByteArray());
        Minecraft.getInstance().execute(()->{
            Identifier texture=Identifier.fromNamespaceAndPath("appearance_sync","chat_image/"+key);
            var manager=Minecraft.getInstance().getTextureManager();manager.register(texture,new DynamicTexture(()->"Chat image",nativeImage){
                {sampler=com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);}
            });
            textures.put(key,new Texture(texture,small.getWidth(),small.getHeight()));loading.remove(key);
            while(textures.size()>24||textures.values().stream().mapToLong(t->4L*t.width()*t.height()).sum()>96L*1024*1024){String oldest=textures.keySet().iterator().next();manager.release(textures.remove(oldest).id());}
        });
    }
    public static void draw(GuiGraphicsExtractor graphics,String id,int x,int y,int size){
        draw(graphics,id,x,y,size,1);
    }
    public static void draw(GuiGraphicsExtractor graphics,String id,int x,int y,int size,float opacity){
        Texture texture=textures.get(id);
        if(texture==null){graphics.text(Minecraft.getInstance().font,"Изображение…",x+4,y+size/2,ChatMotion.color(UiTheme.MUTED,opacity),false);
            if(id.matches("[a-f0-9]{64}")&&!loading.contains(id)&&System.currentTimeMillis()>retry.getOrDefault(id,0L)){
                loading.add(id);WORK.execute(()->{try{register(id,download(id));}catch(Exception e){Minecraft.getInstance().execute(()->{loading.remove(id);retry.put(id,System.currentTimeMillis()+30000);if(retry.size()>128)retry.remove(retry.keySet().iterator().next());});}});
            }return;
        }
        textures.remove(id);textures.put(id,texture);double scale=(double)size/Math.max(texture.width(),texture.height());int w=Math.max(1,(int)(texture.width()*scale)),h=Math.max(1,(int)(texture.height()*scale));
        graphics.blit(RenderPipelines.GUI_TEXTURED,texture.id(),x+(size-w)/2,y+(size-h)/2,0f,0f,w,h,texture.width(),texture.height(),texture.width(),texture.height(),ChatMotion.color(0xFFFFFFFF,opacity));
    }
    public static void view(Screen parent,String id){
        
        Minecraft.getInstance().gui.setScreen(new Screen(Component.literal("Изображение")){
            @Override protected void init(){addRenderableWidget(FlatButtons.builder(Component.literal("Назад"),b->onClose()).bounds(width/2-50,height-30,100,20).build());}
            @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0xEE151A22);int size=Math.max(1,Math.min(width-24,height-55));drawPreview(g,id,(width-size)/2,10,size);super.extractRenderState(g,mx,my,delta);}
            @Override public void tick(){if(!com.wildfire.client.music.SpringPlusSession.enabled())Minecraft.getInstance().gui.setScreen(null);}
            @Override public void onClose(){Minecraft.getInstance().gui.setScreen(parent);}
            @Override public boolean isPauseScreen(){return false;}
        });
    }
    /** Screenshot zoom keeps the original up to 2048 px; thumbnails remain lightweight. */
    public static void drawPreview(GuiGraphicsExtractor g,String id,int x,int y,int size){
        if(!id.matches("[a-f0-9]{64}"))return;
        String key=id+"_full";Texture texture=textures.get(key);
        if(texture==null){
            draw(g,id,x,y,size);
            if(!loading.contains(key)&&System.currentTimeMillis()>retry.getOrDefault(key,0L)){
                loading.add(key);WORK.execute(()->{try{register(key,download(id),2048);}catch(Exception e){Minecraft.getInstance().execute(()->{loading.remove(key);retry.put(key,System.currentTimeMillis()+30000);if(retry.size()>128)retry.remove(retry.keySet().iterator().next());});}});
            }
            return;
        }
        textures.remove(key);textures.put(key,texture);
        double scale=(double)size/Math.max(texture.width(),texture.height());int w=Math.max(1,(int)(texture.width()*scale)),h=Math.max(1,(int)(texture.height()*scale));
        g.blit(RenderPipelines.GUI_TEXTURED,texture.id(),x+(size-w)/2,y+(size-h)/2,0f,0f,w,h,texture.width(),texture.height(),texture.width(),texture.height());
    }
    /** Cropped city artwork reuses the bounded image cache and fixed-origin download path. */
    public static void drawCover(GuiGraphicsExtractor g,String id,int x,int y,int width,int height){
        if(!id.matches("[a-f0-9]{64}")||width<1||height<1)return;
        Texture texture=textures.get(id);
        g.enableScissor(x,y,x+width,y+height);
        if(texture==null)draw(g,id,x,y,Math.max(width,height));
        else {
            double scale=Math.max((double)width/texture.width(),(double)height/texture.height());
            int w=Math.max(1,(int)Math.ceil(texture.width()*scale)),h=Math.max(1,(int)Math.ceil(texture.height()*scale));
            g.blit(RenderPipelines.GUI_TEXTURED,texture.id(),x+(width-w)/2,y+(height-h)/2,0f,0f,w,h,texture.width(),texture.height(),texture.width(),texture.height());
        }
        g.disableScissor();
    }
    private static final class Preview extends Screen{
        @Override public void tick(){if(!com.wildfire.client.music.SpringPlusSession.enabled())Minecraft.getInstance().gui.setScreen(null);}
        private final Screen parent;private final byte[] bytes;private final Runnable requestTicket;private final Consumer<String> send;private final String key="preview_"+UUID.randomUUID().toString().replace("-","");
        private String error="";private boolean busy;private net.minecraft.client.gui.components.Button upload;
        Preview(Screen parent,byte[] bytes,Runnable requestTicket,Consumer<String> send){super(Component.literal("Отправить изображение?"));this.parent=parent;this.bytes=bytes;this.requestTicket=requestTicket;this.send=send;WORK.execute(()->{try{register(key,bytes);}catch(Exception ignored){}});}
        @Override protected void init(){
            upload=addRenderableWidget(FlatButtons.builder(Component.literal("Отправить"),b->upload()).bounds(width/2-104,height-35,100,20).build());
            addRenderableWidget(FlatButtons.builder(Component.literal("Отмена"),b->onClose()).bounds(width/2+4,height-35,100,20).build());upload.active=!busy;
        }
        private void upload(){
            if(busy)return;if(!uploading.compareAndSet(false,true)){error="Предыдущая загрузка ещё завершается";return;}busy=true;upload.active=false;error="Загружаем…";
            var ticket=new CompletableFuture<String>();pendingTicket=ticket;
            try{requestTicket.run();}catch(Exception e){ticket.completeExceptionally(e);}
            WORK.execute(()->{try{
                String token=ticket.get(15,TimeUnit.SECONDS);
                var response=HTTP.send(HttpRequest.newBuilder(URI.create(BASE+"upload/"+token)).timeout(Duration.ofSeconds(30)).header("Content-Type","image/png").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofInputStream());
                byte[] body;try(var input=response.body()){body=input.readNBytes(4097);}if(response.statusCode()!=201||body.length>4096)throw new IOException("Загрузка отклонена (лимит: 20 изображений в сутки)");
                String id=JsonParser.parseString(new String(body,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().get("id").getAsString();if(!id.matches("[a-f0-9]{64}"))throw new IOException("Некорректный ответ");
                Minecraft.getInstance().execute(()->{if(Minecraft.getInstance().gui.screen()!=this)return;Minecraft.getInstance().gui.setScreen(parent);send.accept(BASE+id+".png");});
            }catch(Exception e){ticket.completeExceptionally(e);Minecraft.getInstance().execute(()->{busy=false;upload.active=true;error=e.getCause()!=null?e.getCause().getMessage():"Не удалось загрузить изображение";});}finally{uploading.set(false);}});
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0xEE151A22);int size=Math.max(1,Math.min(280,Math.min(width-24,height-90)));draw(g,key,(width-size)/2,35,size);g.text(font,title,width/2-font.width(title)/2,14,UiTheme.TEXT,false);g.text(font,font.plainSubstrByWidth(error,width-20),10,height-49,UiTheme.MUTED,false);super.extractRenderState(g,mx,my,delta);}
        @Override public void onClose(){Minecraft.getInstance().gui.setScreen(parent);}
        @Override public void removed(){Texture t=textures.remove(key);if(t!=null)Minecraft.getInstance().getTextureManager().release(t.id());super.removed();}
        @Override public boolean isPauseScreen(){return false;}
    }
}
