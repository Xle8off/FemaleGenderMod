package com.wildfire.neoforge.appearance;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Loads small public skin previews only for chibis that are actually visible. */
public final class ChibiSkinClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private static final Map<UUID, Identifier> TEXTURES = new HashMap<>();
    private static final Map<UUID, Boolean> SLIM = new HashMap<>();
    private static final Map<UUID, Integer> SPARSE_SLEEVES = new HashMap<>();
    private static final Map<UUID, Long> RETRY_AFTER = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.Semaphore REQUESTS = new java.util.concurrent.Semaphore(2);
    private static volatile long blockedUntil;
    private static volatile long epoch;
    private ChibiSkinClient() {}

    public static Identifier texture(UUID player) {
        Identifier cached = TEXTURES.get(player);
        long now = System.currentTimeMillis();
        if (RETRY_AFTER.getOrDefault(player, 0L) <= now) {
            request(player, player.toString());
        }
        if (cached != null) return cached;
        var connection = Minecraft.getInstance().getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(player);
        return info == null ? null : info.getSkin().body().texturePath();
    }

    /** Nickname is resolved to the canonical server UUID by our API, not Mojang. */
    public static Identifier texture(String name) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) return null;
        UUID cacheKey = UUID.nameUUIDFromBytes(("site-avatar:" + name.toLowerCase(java.util.Locale.ROOT))
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        if (RETRY_AFTER.getOrDefault(cacheKey, 0L) <= now) {
            request(cacheKey, name);
        }
        return TEXTURES.get(cacheKey);
    }

    public static boolean slim(UUID player) {
        Boolean cached = SLIM.get(player);
        if (cached != null) return cached;
        var connection = Minecraft.getInstance().getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(player);
        return info != null && info.getSkin().model() == PlayerModelType.SLIM;
    }

    public static int sparseSleeves(UUID player) { return SPARSE_SLEEVES.getOrDefault(player, 0); }

    private static void request(UUID player, String identity) {
        long now = System.currentTimeMillis();
        if (now < blockedUntil) return;
        if (!REQUESTS.tryAcquire()) { RETRY_AFTER.put(player, now + 250); return; }
        RETRY_AFTER.put(player, now + 60_000);
        long requestEpoch = epoch;
        Thread.startVirtualThread(() -> {
            try { fetch(player, requestEpoch, identity); }
            finally { REQUESTS.release(); }
        });
    }

    private static void fetch(UUID player, long requestEpoch, String identity) {
        try {
            var request = HttpRequest.newBuilder(URI.create("https://skin.springplus.net/api/chibi-skin/" + identity))
                .timeout(Duration.ofSeconds(8)).GET().build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = response.body();
            if (requestEpoch != epoch) return;
            int status = response.statusCode();
            if (status == 429 || status >= 500) { blockedUntil = System.currentTimeMillis() + 30_000; return; }
            if (status != 200 || bytes.length > 8 * 1024 * 1024) {
                RETRY_AFTER.put(player, System.currentTimeMillis() + 120_000); return;
            }
            RETRY_AFTER.put(player, System.currentTimeMillis() + (identity.matches("[A-Za-z0-9_]{1,16}") ? 600_000 : 60_000));
            Minecraft.getInstance().schedule(() -> { if (requestEpoch == epoch) install(player, bytes); });
        } catch (Exception ignored) {
            if (requestEpoch == epoch) blockedUntil = System.currentTimeMillis() + 30_000;
        }
    }

    private static void install(UUID player, byte[] bytes) {
        try (NativeImage image = NativeImage.read(bytes)) {
            int size = image.getWidth();
            if (size < 64 || size > 2048 || size != image.getHeight()
                || Integer.bitCount(size / 64) != 1 || size % 64 != 0) return;
            int scale = size / 64;
            int[] pixels = image.getPixelsABGR();
            boolean slim = transparentStrip(pixels,size,scale,54,20) && transparentStrip(pixels,size,scale,46,52)
                && opaqueStrip(pixels,size,scale,53,20) && opaqueStrip(pixels,size,scale,45,52);
            var copy = new NativeImage(size, size, true);
            copy.copyFrom(image);
            flattenSparseLayer(copy,pixels,scale,16,16,16,32,24,16,120);
            flattenSparseLayer(copy,pixels,scale,40,16,40,32,16,16,96);
            flattenSparseLayer(copy,pixels,scale,32,48,48,48,16,16,96);
            Identifier id = Identifier.fromNamespaceAndPath("appearance_sync", "chibi_skin/" + player.toString().replace('-', '_'));
            var texture = new DynamicTexture(() -> "Chibi skin " + player, copy);
            if (TEXTURES.containsKey(player)) Minecraft.getInstance().getTextureManager().release(id);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            TEXTURES.put(player, id);
            SLIM.put(player, slim);
            int right=countOpaque(pixels,size,scale,40,32), left=countOpaque(pixels,size,scale,48,48);
            int sparseLimit=96*scale*scale;
            SPARSE_SLEEVES.put(player,(right>0&&right<sparseLimit?1:0)|(left>0&&left<sparseLimit?2:0));
        } catch (Exception ignored) { }
    }

    private static boolean transparentStrip(int[] pixels, int size, int scale, int x, int y) {
        for (int row=y*scale;row<(y+12)*scale;row++)
            for (int col=x*scale;col<(x+1)*scale;col++) if ((pixels[row*size+col]>>>24)!=0) return false;
        return true;
    }
    private static int countOpaque(int[] pixels, int size, int scale, int x, int y) {
        int count=0;
        for(int row=y*scale;row<(y+16)*scale;row++)for(int col=x*scale;col<(x+16)*scale;col++)
            if((pixels[row*size+col]>>>24)!=0)count++;
        return count;
    }
    private static void flattenSparseLayer(NativeImage target,int[] pixels,int scale,int baseX,int baseY,
                                           int outerX,int outerY,int width,int height,int limit) {
        baseX*=scale;baseY*=scale;outerX*=scale;outerY*=scale;width*=scale;height*=scale;
        int count=0;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)
            if((pixels[(outerY+y)*target.getWidth()+outerX+x]>>>24)!=0)count++;
        if(count==0||count>=limit*scale*scale)return;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int top=pixels[(outerY+y)*target.getWidth()+outerX+x],alpha=top>>>24;
            if(alpha==0)continue;
            int bottom=pixels[(baseY+y)*target.getWidth()+baseX+x];
            int result=alpha==255?top:blend(bottom,top,alpha);
            target.setPixelABGR(baseX+x,baseY+y,result);
            target.setPixelABGR(outerX+x,outerY+y,0);
        }
    }
    private static int blend(int bottom,int top,int alpha) {
        int inverse=255-alpha;
        int a=Math.min(255,alpha+((bottom>>>24)*inverse+127)/255);
        int b=(((top>>>16)&255)*alpha+((bottom>>>16)&255)*inverse+127)/255;
        int g=(((top>>>8)&255)*alpha+((bottom>>>8)&255)*inverse+127)/255;
        int r=((top&255)*alpha+(bottom&255)*inverse+127)/255;
        return a<<24|b<<16|g<<8|r;
    }
    private static boolean opaqueStrip(int[] pixels, int size, int scale, int x, int y) {
        for (int row=y*scale;row<(y+12)*scale;row++)
            for (int col=x*scale;col<(x+1)*scale;col++) if ((pixels[row*size+col]>>>24)!=0) return true;
        return false;
    }

    public static void clear() {
        epoch++;
        Minecraft.getInstance().execute(ChibiSkinClient::clearTextures);
    }

    /** TextureManager and these maps are owned exclusively by the client/render thread. */
    private static void clearTextures() {
        blockedUntil = 0;
        TEXTURES.values().forEach(Minecraft.getInstance().getTextureManager()::release);
        TEXTURES.clear();
        SLIM.clear();
        SPARSE_SLEEVES.clear();
        RETRY_AFTER.clear();
    }
}
