package com.wildfire.main.networking.appearance;

import com.mojang.blaze3d.platform.NativeImage;
import com.wildfire.main.WildfireGender;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-side custom cape textures. Transfers and texture operations are confined to the client thread. */
public final class CapeClient {
    private static final Map<UUID, Cape> CAPES = new HashMap<>();
    private static final Map<UUID, Animation> ANIMATIONS = new HashMap<>();
    private static final Map<UUID, Transfer> TRANSFERS = new HashMap<>();
    private static int pendingBytes;
    private record Cape(int revision, Identifier id, DynamicTexture texture) {}
    private record Frame(int[] pixels, int durationMs) {}
    private static final class Animation {
        final Frame[] frames;
        int index;
        long nextAt;
        Animation(Frame[] frames) {this.frames=frames;nextAt=System.currentTimeMillis()+frames[0].durationMs();}
    }
    private static final class Transfer {
        final int revision, width, height;
        final byte[] bytes;
        int received;
        Transfer(HdSkinPackets.CapeChunk chunk) {
            revision = chunk.revision(); width = chunk.width(); height = chunk.height();
            bytes = new byte[chunk.totalBytes()];
        }
        boolean matches(HdSkinPackets.CapeChunk chunk) {
            return revision == chunk.revision() && width == chunk.width() && height == chunk.height()
                && bytes.length == chunk.totalBytes();
        }
    }
    private CapeClient() {}

    public static void registerClient() {
        ClientTickEvents.END_CLIENT_TICK.register(CapeClient::tick);
        ClientPlayConnectionEvents.INIT.register((listener, client) -> {
            ClientPlayNetworking.registerReceiver(HdSkinPackets.CapeChunk.TYPE,
                (packet, context) -> context.client().schedule(() -> receive(packet)));
            ClientPlayNetworking.registerReceiver(HdSkinPackets.CapeClear.TYPE,
                (packet, context) -> context.client().schedule(() -> clear(packet.player())));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> clearAll());
    }

    public static ClientAsset.Texture texture(UUID player) {
        Cape cape = CAPES.get(player);
        return cape == null ? null : new ClientAsset.ResourceTexture(cape.id(), cape.id());
    }

    private static void receive(HdSkinPackets.CapeChunk chunk) {
        int width = chunk.width(), height = chunk.height();
        if (chunk.revision() < 0 || width < 64 || width > 2048 || height != width / 2
            || Integer.bitCount(width / 64) != 1 || chunk.totalBytes() < 1
            || chunk.totalBytes() > HdSkinPackets.MAX_PNG_BYTES || chunk.data().length < 1
            || chunk.data().length > HdSkinPackets.CHUNK_BYTES) {
            if (isSelf(chunk.player())) WildfireGender.LOGGER.warn("[skin-sync] Rejected own cape chunk revision={} size={}x{} total={}",
                chunk.revision(), width, height, chunk.totalBytes());
            return;
        }
        if (isSelf(chunk.player()) && chunk.offset() == 0)
            WildfireGender.LOGGER.info("[skin-sync] Receiving own cape revision={} size={}x{} bytes={}",
                chunk.revision(), width, height, chunk.totalBytes());
        Cape current = CAPES.get(chunk.player());
        if (current != null && chunk.revision() <= current.revision()) return;
        Transfer transfer = TRANSFERS.get(chunk.player());
        if (transfer != null && chunk.revision() < transfer.revision) return;
        if (transfer == null || !transfer.matches(chunk)) {
            if (chunk.offset() != 0 || pendingBytes + chunk.totalBytes() > 16 * 1024 * 1024) return;
            drop(chunk.player());
            transfer = new Transfer(chunk);
            TRANSFERS.put(chunk.player(), transfer);
            pendingBytes += transfer.bytes.length;
        }
        if (chunk.offset() != transfer.received) return;
        System.arraycopy(chunk.data(), 0, transfer.bytes, transfer.received, chunk.data().length);
        transfer.received += chunk.data().length;
        if (transfer.received == transfer.bytes.length) {
            drop(chunk.player());
            install(chunk.player(), transfer);
        }
    }

    private static void install(UUID player, Transfer transfer) {
        byte[] bytes = transfer.bytes;
        if (isGif(bytes)) {
            try {installGif(player,transfer);} catch(Exception invalid) {
                WildfireGender.LOGGER.warn("Rejected cape GIF for {}: {}",player,invalid.toString());
            }
            return;
        }
        if (bytes.length < 24 || bytes[0] != (byte) 137 || bytes[1] != 80 || bytes[2] != 78 || bytes[3] != 71
            || bytes[4] != 13 || bytes[5] != 10 || bytes[6] != 26 || bytes[7] != 10
            || bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R'
            || int32(bytes,16) != transfer.width || int32(bytes,20) != transfer.height) return;
        try (NativeImage image = NativeImage.read(transfer.bytes)) {
            if (image.getWidth() != transfer.width || image.getHeight() != transfer.height) return;
            NativeImage copy = new NativeImage(transfer.width, transfer.height, true);
            copy.copyFrom(image);
            orientCape(copy);
            Identifier id = Identifier.fromNamespaceAndPath("appearance_sync", "cape/" + player.toString().replace('-', '_'));
            clearOnClientThread(player);
            DynamicTexture texture = new DynamicTexture(() -> "Custom cape " + player, copy);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            CAPES.put(player, new Cape(transfer.revision, id, texture));
            if (isSelf(player)) WildfireGender.LOGGER.info("[skin-sync] Installed own cape revision={} size={}x{} bytes={}",
                transfer.revision, transfer.width, transfer.height, transfer.bytes.length);
        } catch (IOException | RuntimeException invalid) {
            WildfireGender.LOGGER.warn("Rejected cape PNG for {}: {}", player, invalid.toString());
        }
    }
    private static int int32(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 24 | (bytes[offset+1] & 255) << 16
            | (bytes[offset+2] & 255) << 8 | bytes[offset+3] & 255;
    }
    private static boolean isGif(byte[] bytes) {
        return bytes.length>=6 && bytes[0]=='G' && bytes[1]=='I' && bytes[2]=='F'
            && (bytes[3]=='8') && (bytes[4]=='7'||bytes[4]=='9') && bytes[5]=='a';
    }
    private static void installGif(UUID player, Transfer transfer) throws IOException {
        byte[] bytes=transfer.bytes;
        if(bytes.length>2*1024*1024||transfer.width>256||transfer.height!=transfer.width/2
            ||bytes.length<13||((bytes[6]&255)|((bytes[7]&255)<<8))!=transfer.width
            ||((bytes[8]&255)|((bytes[9]&255)<<8))!=transfer.height) return;
        ArrayList<Frame> frames=new ArrayList<>();
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            ImageReader reader=ImageIO.getImageReadersByFormatName("gif").next();
            try{
                reader.setInput(input,false,false);
                int count=reader.getNumImages(true);
                if(count<2||count>24)return;
                BufferedImage composed=new BufferedImage(transfer.width,transfer.height,BufferedImage.TYPE_INT_ARGB);
                BufferedImage restore=null;
                String priorDisposal="none";int priorX=0,priorY=0,priorW=0,priorH=0;
                for(int i=0;i<count;i++){
                    if("restoreToBackgroundColor".equals(priorDisposal)){
                        Graphics2D clear=composed.createGraphics();
                        clear.setComposite(AlphaComposite.Clear);clear.fillRect(priorX,priorY,priorW,priorH);clear.dispose();
                    }else if("restoreToPrevious".equals(priorDisposal)&&restore!=null){
                        Graphics2D reset=composed.createGraphics();reset.setComposite(AlphaComposite.Src);reset.drawImage(restore,0,0,null);reset.dispose();
                    }
                    Node metadata=reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                    Node descriptor=child(metadata,"ImageDescriptor"),control=child(metadata,"GraphicControlExtension");
                    if(descriptor==null||control==null)return;
                    int x=attributeInt(descriptor,"imageLeftPosition"),y=attributeInt(descriptor,"imageTopPosition");
                    int w=attributeInt(descriptor,"imageWidth"),h=attributeInt(descriptor,"imageHeight");
                    if(w<1||h<1||x<0||y<0||x+w>transfer.width||y+h>transfer.height)return;
                    String disposal=attribute(control,"disposalMethod");
                    restore="restoreToPrevious".equals(disposal)?copy(composed):null;
                    BufferedImage image=reader.read(i);
                    Graphics2D draw=composed.createGraphics();draw.setComposite(AlphaComposite.SrcOver);draw.drawImage(image,x,y,null);draw.dispose();
                    NativeImage frame=new NativeImage(transfer.width,transfer.height,true);
                    try{
                        int[] argb=composed.getRGB(0,0,transfer.width,transfer.height,null,0,transfer.width);
                        for(int p=0;p<argb.length;p++){
                            int color=argb[p],abgr=color&0xff00ff00|(color&0xff)<<16|(color>>>16&0xff);
                            frame.setPixelABGR(p%transfer.width,p/transfer.width,abgr);
                        }
                        orientCape(frame);
                        frames.add(new Frame(frame.getPixelsABGR(),Math.max(50,attributeInt(control,"delayTime")*10)));
                    }finally{frame.close();}
                    priorDisposal=disposal;priorX=x;priorY=y;priorW=w;priorH=h;
                }
            }finally{reader.dispose();}
        }
        if(frames.size()<2)return;
        NativeImage first=new NativeImage(transfer.width,transfer.height,true);
        int[] pixels=frames.getFirst().pixels();
        for(int p=0;p<pixels.length;p++)first.setPixelABGR(p%transfer.width,p/transfer.width,pixels[p]);
        Identifier id=Identifier.fromNamespaceAndPath("appearance_sync","cape/"+player.toString().replace('-','_'));
        clearOnClientThread(player);
        DynamicTexture texture=new DynamicTexture(()->"Animated cape "+player,first);
        Minecraft.getInstance().getTextureManager().register(id,texture);texture.upload();
        CAPES.put(player,new Cape(transfer.revision,id,texture));
        if (isSelf(player)) WildfireGender.LOGGER.info("[skin-sync] Installed own animated cape revision={} size={}x{} frames={}",
            transfer.revision, transfer.width, transfer.height, frames.size());
        ANIMATIONS.put(player,new Animation(frames.toArray(Frame[]::new)));
    }
    private static BufferedImage copy(BufferedImage source){
        BufferedImage result=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw=result.createGraphics();draw.drawImage(source,0,0,null);draw.dispose();return result;
    }
    private static Node child(Node parent,String name){
        for(Node node=parent.getFirstChild();node!=null;node=node.getNextSibling())if(name.equals(node.getNodeName()))return node;
        return null;
    }
    private static String attribute(Node node,String name){
        NamedNodeMap attrs=node.getAttributes();Node value=attrs==null?null:attrs.getNamedItem(name);
        return value==null?"":value.getNodeValue();
    }
    private static int attributeInt(Node node,String name){return Integer.parseInt(attribute(node,name));}
    private static void tick(Minecraft client){
        long now=System.currentTimeMillis();
        for(var entry:ANIMATIONS.entrySet()){
            Animation animation=entry.getValue();
            if(now<animation.nextAt)continue;
            Cape cape=CAPES.get(entry.getKey());if(cape==null)continue;
            animation.index=(animation.index+1)%animation.frames.length;
            Frame frame=animation.frames[animation.index];animation.nextAt=now+frame.durationMs();
            NativeImage image=cape.texture().getPixels();if(image==null)continue;
            int[] pixels=frame.pixels();
            for(int p=0;p<pixels.length;p++)image.setPixelABGR(p%image.getWidth(),p/image.getWidth(),pixels[p]);
            cape.texture().upload();
        }
    }

    private static void orientCape(NativeImage image) {
        int scale = image.getWidth() / 64;
        int[] pixels = image.getPixelsABGR();
        int frontVisible = 0, backVisible = 0;
        for (int y = scale; y < 17 * scale; y++) for (int x = 0; x < 10 * scale; x++) {
            if ((pixels[y * image.getWidth() + scale + x] >>> 24) != 0) frontVisible++;
            if ((pixels[y * image.getWidth() + 12 * scale + x] >>> 24) != 0) backVisible++;
        }
        // Some uploaded cape templates put the visible outside on the first face.
        if (frontVisible == 0 || backVisible != 0) return;
        for (int y = scale; y < 17 * scale; y++) for (int x = 0; x < 10 * scale; x++) {
            image.setPixelABGR(12 * scale + x, y, pixels[y * image.getWidth() + scale + x]);
            image.setPixelABGR(scale + x, y, pixels[y * image.getWidth() + 12 * scale + x]);
        }
    }
    private static void drop(UUID player) {
        // Transfers and registered textures are maintained on the client thread.
        Transfer transfer = TRANSFERS.remove(player);
        if (transfer != null) pendingBytes -= transfer.bytes.length;
    }
    private static boolean isSelf(UUID player) {
        var local = Minecraft.getInstance().player;
        return local != null && player.equals(local.getUUID());
    }
    public static void clear(UUID player) {
        Minecraft.getInstance().execute(() -> clearOnClientThread(player));
    }
    private static void clearOnClientThread(UUID player) {
        drop(player);
        ANIMATIONS.remove(player);
        Cape removed = CAPES.remove(player);
        if (removed != null) Minecraft.getInstance().getTextureManager().release(removed.id());
    }
    public static void clearAll() {
        Minecraft.getInstance().execute(() -> {
            for (UUID player : CAPES.keySet().toArray(UUID[]::new)) clearOnClientThread(player);
            TRANSFERS.clear(); pendingBytes = 0;
        });
    }
}
