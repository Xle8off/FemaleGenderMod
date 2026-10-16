package com.wildfire.gui;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Flow;

/** Trusted static catalog: bounded background transfers, immutable hashes and a disk cache. */
public final class WebStickers {
    public record Sticker(String id, String pack, String label, String sha256, int width, int height, int bytes, List<Integer> durations,String author) {
        public Sticker(String id,String pack,String label,String sha256,int width,int height,int bytes,List<Integer> durations){this(id,pack,label,sha256,width,height,bytes,durations,"");}
        public Sticker(String id,String pack,String label,String sha256,int width,int height,int bytes){this(id,pack,label,sha256,width,height,bytes,List.of());}
        public int frames(){return Math.max(1,durations.size());}
        public String packId(){int slash=id.indexOf('/');return slash>0?id.substring(0,slash):"";}
        public String caption(){return pack+(author.isBlank()?"":" · Автор: "+(author.equals("System")?"Сервер":author));}
        public int frame(long millis){
            if(durations.isEmpty())return 0;
            long position=Math.floorMod(millis,durations.stream().mapToInt(Integer::intValue).sum());
            for(int i=0;i<durations.size();i++){position-=durations.get(i);if(position<0)return i;}return 0;
        }
        public String message() { return "[Стикер:" + id + "]"; }
    }
    private static final String BASE = "https://skin.springplus.net/api/stickers";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ExecutorService WORK = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(128), task -> { var thread = new Thread(task,"SpringPlus sticker loader"); thread.setDaemon(true); return thread; });
    private static final AtomicBoolean fetching = new AtomicBoolean();
    private static final Set<String> loading = ConcurrentHashMap.newKeySet();
    // Bound decoded native memory and GPU work: at most four pending, one upload per tick.
    private static final Semaphore uploadSlots = new Semaphore(4);
    private static final Queue<Runnable> pendingUploads = new ConcurrentLinkedQueue<>();
    public static void tick() {
        Runnable upload = pendingUploads.poll();
        if (upload != null) upload.run();
    }
    private static final Map<String, Long> retryAt = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> textures = new LinkedHashMap<>(); // UI thread only
    private static final StickerTextureBudget textureBudget=new StickerTextureBudget();
    private static volatile List<Sticker> catalog = List.of();
    private static volatile long nextCheck;
    private static volatile String status = "Загружаем наборы…";
    private WebStickers() {}
    public static List<Sticker> entries() { return catalog; }
    public record WorldSprite(Identifier texture,float v0,float v1,int width,int height) {}
    /** Same bounded asynchronous thumbnail cache as the picker; no world-render decoding. */
    public static WorldSprite worldSprite(Sticker sticker){
        int edge=sticker.frames()>1?96:128;
        edge=Math.min(edge,Math.max(sticker.width(),sticker.height()));
        String key=sticker.sha256()+"_"+edge;Identifier texture=textures.get(key);
        if(texture==null){request(sticker,edge);return null;}
        textureBudget.touch(key);int frame=sticker.frame(System.nanoTime()/1_000_000);
        return new WorldSprite(texture,frame/(float)sticker.frames(),(frame+1)/(float)sticker.frames(),sticker.width(),sticker.height());
    }
    public static String status() { return status; }
    public static void refresh() {
        if (!com.wildfire.client.music.SpringPlusSession.enabled() || System.nanoTime() < nextCheck || !fetching.compareAndSet(false,true)) return;
        nextCheck = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
        try { WORK.execute(() -> {
            Path cache = cacheRoot().resolve("catalog.json");
            try {
                // A corrupt/obsolete local catalog must not block downloading the fresh one.
                try{
                    if (catalog.isEmpty() && Files.isRegularFile(cache) && Files.size(cache) <= 2097152)
                        publish(parse(Files.readString(cache)));
                }catch(Exception ignored){}
                byte[] bytes = get("/catalog",2097152);
                var fresh = parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
                Files.createDirectories(cache.getParent());
                atomicWrite(cache,bytes);
                publish(fresh); status = fresh.isEmpty() ? "Наборы пока не добавлены" : "";
            } catch (Exception error) {
                status = catalog.isEmpty() ? "Не удалось загрузить наборы. Повторим позже." : "Показан сохранённый набор";
                nextCheck = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            } finally { fetching.set(false); }
        }); }catch(RejectedExecutionException busy){
            fetching.set(false);
            nextCheck=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        }
    }
    private static void publish(List<Sticker> entries) {
        Minecraft.getInstance().schedule(() -> catalog = entries);
    }
    static List<Sticker> parse(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Catalog version");
        var entries = root.getAsJsonArray("stickers");
        if (entries.size() > 4096) throw new IllegalArgumentException("Catalog limit");
        var result = new ArrayList<Sticker>(); var ids = new HashSet<String>(); var packs = new HashSet<String>();
        for (var raw : entries) {
            var entry = raw.getAsJsonObject();
            var durations=new ArrayList<Integer>();
            if(entry.has("durations")){
                for(var duration:entry.getAsJsonArray("durations")) {
                    int value=duration.getAsInt();
                    if(!duration.getAsString().equals(Integer.toString(value))||value<40||value>5000)throw new IllegalArgumentException("Frame duration");
                    durations.add(value);
                }
                if(durations.size()<2||durations.size()>32||durations.stream().mapToInt(Integer::intValue).sum()>30000)throw new IllegalArgumentException("Animation limit");
            }
            var sticker = new Sticker(entry.get("id").getAsString(),entry.get("pack").getAsString(),entry.get("label").getAsString(),
                entry.get("sha256").getAsString(),entry.get("width").getAsInt(),entry.get("height").getAsInt(),entry.get("bytes").getAsInt(),List.copyOf(durations),entry.has("author")?entry.get("author").getAsString():"");
            if(sticker.author().length()>16||sticker.author().codePoints().anyMatch(c->Character.isISOControl(c)||c==0xA7))throw new IllegalArgumentException("Invalid pack author");
            if(!durations.isEmpty()&&(sticker.width()>256||sticker.height()>256))throw new IllegalArgumentException("Animation dimensions");
            if (!sticker.id().matches("[a-z0-9_]{1,32}/[a-z0-9_-]{1,64}") || !ids.add(sticker.id())
                || !sticker.sha256().matches("[a-f0-9]{64}") || sticker.pack().isBlank() || sticker.pack().length()>48
                || sticker.label().isBlank() || sticker.label().length()>80 || sticker.pack().codePoints().anyMatch(Character::isISOControl)
                || sticker.label().codePoints().anyMatch(Character::isISOControl)
                || sticker.width()<1 || sticker.height()<1 || sticker.width()>512 || sticker.height()>512
                || sticker.bytes()<24 || sticker.bytes()>5*1024*1024) throw new IllegalArgumentException("Invalid sticker");
            result.add(sticker);
            packs.add(sticker.id().split("/")[0]);
        }
        if (packs.size()>128) throw new IllegalArgumentException("Pack limit");
        return List.copyOf(result);
    }
    public static Sticker message(String body) {
        if (!isMessage(body)) return null;
        // Receiving a sticker must not require opening the picker first.
        // refresh is throttled and fetches asynchronously, including catalog updates.
        refresh();
        String id=body.substring(8,body.length()-1);
        return catalog.stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
    }
    public static boolean isMessage(String body){
        return body!=null&&body.length()<=110&&body.startsWith("[Стикер:")&&body.endsWith("]")
            &&body.substring(8,body.length()-1).matches("[a-z0-9_]{1,32}/[a-z0-9_-]{1,64}");
    }
    public static String missingLabel(){
        return fetching.get()||catalog.isEmpty()?"Загрузка стикера…":"Стикер недоступен";
    }
    static void validate(byte[] png, Sticker sticker) throws Exception {
        if (png.length != sticker.bytes() || png.length<24 || png.length>5*1024*1024
            || ByteBuffer.wrap(png).getLong()!=0x89504e470d0a1a0aL
            || ByteBuffer.wrap(png).getInt(12)!=0x49484452
            || ByteBuffer.wrap(png).getInt(16)!=sticker.width() || ByteBuffer.wrap(png).getInt(20)!=sticker.height()*sticker.frames()
            || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)).equals(sticker.sha256()))
            throw new IllegalArgumentException("Invalid sticker image");
    }
    public static void draw(GuiGraphicsExtractor graphics, Sticker sticker, int x, int y, int size) {
        draw(graphics,sticker,x,y,size,1);
    }
    public static void draw(GuiGraphicsExtractor graphics, Sticker sticker, int x, int y, int size,float opacity) {
        int edge=Math.clamp((int)Math.round(size*Minecraft.getInstance().getWindow().getGuiScale()),1,512);
        edge=Math.min(Math.max(sticker.width(),sticker.height()),((edge+15)/16)*16);
        if(sticker.frames()>1)edge=Math.min(edge,96);
        drawResolution(graphics,sticker,x,y,size,edge,opacity);
    }
    /** Zoom uses source resolution, not the lightweight animated picker thumbnail. */
    public static void drawPreview(GuiGraphicsExtractor graphics,Sticker sticker,int x,int y,int size){
        int edge=Math.min(sticker.frames()>1?256:512,Math.max(sticker.width(),sticker.height()));
        drawResolution(graphics,sticker,x,y,size,edge);
    }
    private static void drawResolution(GuiGraphicsExtractor graphics,Sticker sticker,int x,int y,int size,int edge){
        drawResolution(graphics,sticker,x,y,size,edge,1);
    }
    private static void drawResolution(GuiGraphicsExtractor graphics,Sticker sticker,int x,int y,int size,int edge,float opacity){
        String key=sticker.sha256()+"_"+edge;
        Identifier texture=textures.get(key);
        if (texture==null) {
            request(sticker,edge);
            // Keep the loaded thumbnail visible while the full-resolution texture is prepared.
            int best=0;String prefix=sticker.sha256()+"_";
            for(var candidate:textures.entrySet())if(candidate.getKey().startsWith(prefix)){
                int candidateEdge=Integer.parseInt(candidate.getKey().substring(prefix.length()));
                if(candidateEdge>best){best=candidateEdge;key=candidate.getKey();texture=candidate.getValue();}
            }
            if(texture==null){graphics.fill(x+size/2-2,y+size/2-2,x+size/2+2,y+size/2+2,ChatMotion.color(UiTheme.MUTED,opacity));return;}
            edge=best;
        }
        double scale=(double)size/Math.max(sticker.width(),sticker.height());
        int w=Math.max(1,(int)Math.round(sticker.width()*scale)),h=Math.max(1,(int)Math.round(sticker.height()*scale));
        var dimensions=StickerResampler.dimensions(sticker.width(),sticker.height(),edge);
        textures.remove(key);textures.put(key,texture);
        textureBudget.touch(key);
        graphics.blit(RenderPipelines.GUI_TEXTURED,texture,x+(size-w)/2,y+(size-h)/2,0f,(float)(sticker.frame(System.nanoTime()/1000000)*dimensions[1]),w,h,
            dimensions[0],dimensions[1],dimensions[0],dimensions[1]*sticker.frames(),ChatMotion.color(0xFFFFFFFF,opacity));
    }
    private static void request(Sticker sticker,int edge) {
        String key=sticker.sha256()+"_"+edge;
        if (!com.wildfire.client.music.SpringPlusSession.enabled() || System.nanoTime()<retryAt.getOrDefault(key,0L)
            || !loading.add(key)) return;
        if (!uploadSlots.tryAcquire()) { loading.remove(key); return; }
        try { WORK.execute(() -> {
            try {
                Path file=cacheRoot().resolve(sticker.sha256()+".png"); byte[] bytes=null;
                if (Files.isRegularFile(file) && Files.size(file)<=5*1024*1024) {
                    try { bytes=Files.readAllBytes(file); validate(bytes,sticker); } catch(Exception badCache) { bytes=null; }
                }
                if (bytes==null) {
                    bytes=get("/image/"+sticker.sha256()+".png",5*1024*1024); validate(bytes,sticker);
                    Files.createDirectories(file.getParent()); atomicWrite(file,bytes); prune(file.getParent());
                }
                NativeImage image;
                try(NativeImage original=NativeImage.read(bytes)){
                    var dimensions=StickerResampler.dimensions(sticker.width(),sticker.height(),edge);
                    int[] source=original.getPixels();
                    image=new NativeImage(dimensions[0],dimensions[1]*sticker.frames(),false);
                    for(int frame=0;frame<sticker.frames();frame++){
                        int stride=sticker.width()*sticker.height();
                        int[] pixels=StickerResampler.resize(Arrays.copyOfRange(source,frame*stride,(frame+1)*stride),sticker.width(),sticker.height(),dimensions[0],dimensions[1]);
                        for(int y=0;y<dimensions[1];y++)for(int x=0;x<dimensions[0];x++)image.setPixel(x,y+frame*dimensions[1],pixels[y*dimensions[0]+x]);
                    }
                }
                pendingUploads.add(() -> {
                    try {
                        if (!com.wildfire.client.music.SpringPlusSession.enabled()) { image.close(); return; }
                        if (!textures.containsKey(key)) {
                            Identifier id=Identifier.fromNamespaceAndPath("appearance_sync","web_sticker/"+key);
                            var texture=new SmoothTexture(sticker,image);
                            // DynamicTexture's constructor already uploads the image.
                            Minecraft.getInstance().getTextureManager().register(id,texture); textures.put(key,id);
                            for(String oldest:textureBudget.put(key,4L*image.getWidth()*image.getHeight())){
                                Identifier expired=textures.remove(oldest);
                                if(expired!=null)Minecraft.getInstance().getTextureManager().release(expired);
                            }
                        } else image.close();
                    } catch (Exception failedUpload) {
                        image.close(); retryAt.put(key,System.nanoTime()+TimeUnit.SECONDS.toNanos(30));
                    } finally { loading.remove(key); uploadSlots.release(); }
                });
            } catch (Exception error) {
                retryAt.put(key,System.nanoTime()+TimeUnit.SECONDS.toNanos(30)); loading.remove(key); uploadSlots.release();
            }
        }); } catch(RejectedExecutionException full) { loading.remove(key); uploadSlots.release(); }
    }
    private static final class SmoothTexture extends DynamicTexture {
        SmoothTexture(Sticker sticker,NativeImage image){
            super(() -> "SpringPlus sticker "+sticker.id(),image);
            sampler=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }
    private static Path cacheRoot() { return Minecraft.getInstance().gameDirectory.toPath().resolve("cache/springplus-stickers"); }
    private static synchronized void prune(Path directory) throws Exception {
        try(var files=Files.list(directory)) {
            var cached=files.filter(file -> file.getFileName().toString().matches("[a-f0-9]{64}\\.png"))
                .sorted(Comparator.comparingLong(file -> { try{return Files.getLastModifiedTime(file).toMillis();}catch(Exception ignored){return 0L;} })).toList();
            for(int i=0;i<cached.size()-128;i++) Files.deleteIfExists(cached.get(i));
        }
    }
    private static void atomicWrite(Path file, byte[] bytes) throws Exception {
        Path temporary=Files.createTempFile(file.getParent(),".sticker-",".part");
        try { Files.write(temporary,bytes); Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temporary); }
    }
    private static byte[] get(String suffix,int limit) throws Exception {
        String resource=suffix.equals("/catalog")?suffix+"?refresh="+(System.currentTimeMillis()/60000):suffix;
        var request=HttpRequest.newBuilder(URI.create(BASE+resource)).timeout(Duration.ofSeconds(15)).GET().build();
        var response=HTTP.send(request,info -> new LimitedBody(limit));
        if(response.statusCode()!=200)throw new java.io.IOException("Sticker HTTP "+response.statusCode());
        return response.body();
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
        private final int maximum; private long count; private Flow.Subscription subscription;
        LimitedBody(int maximum){this.maximum=maximum;}
        public CompletionStage<byte[]> getBody(){return delegate.getBody();}
        public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;delegate.onSubscribe(subscription);}
        public void onNext(List<ByteBuffer> chunks){
            for(var chunk:chunks)count+=chunk.remaining();
            if(count>maximum){subscription.cancel();delegate.onError(new java.io.IOException("Sticker body too large"));}
            else delegate.onNext(chunks);
        }
        public void onError(Throwable error){delegate.onError(error);}
        public void onComplete(){delegate.onComplete();}
    }
}
