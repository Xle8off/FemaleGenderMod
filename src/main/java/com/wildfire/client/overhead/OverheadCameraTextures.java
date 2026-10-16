package com.wildfire.client.overhead;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Render-thread GPU cache. Only visible cameras request uploads; never one task per frame. */
public final class OverheadCameraTextures {
    public record Texture(Identifier id,int width,int height,UUID session) {}
    private record Requested(UUID session,CameraVisibility visibility) {}
    private static final LinkedHashMap<UUID,Requested> wanted=new LinkedHashMap<>(16,.75f,true);
    private static final Map<UUID,Texture> textures=new HashMap<>();
    private static int cursor;
    private static Object world;
    private static final class CameraTexture extends DynamicTexture {
        CameraTexture(NativeImage image){
            super(()->"SpringPlus overhead camera",image);
            sampler=com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
        }
    }
    private OverheadCameraTextures(){}
    public static boolean acceptFrame(UUID owner,UUID session,long sequence,int width,int height,byte[] rgba){
        var client=Minecraft.getInstance();
        boolean own=client.player!=null&&client.player.getUUID().equals(owner);
        if(!com.wildfire.client.music.SpringPlusSession.enabled()||(!own&&!com.wildfire.client.config.CosmeticPreferences.overheadCameras()))return false;
        if(own&&com.wildfire.client.config.CosmeticPreferences.overheadMode()!=OverheadMedia.Mode.CAMERA)return false;
        if(!receiving(owner,session))return false;
        return OverheadMedia.INSTANCE.frame(owner,session,sequence,width,height,rgba,System.nanoTime());
    }
    /** Client-thread desired subscriptions for the future transport adapter (not just GPU visibility). */
    public static Map<UUID,UUID> subscriptions(){
        Map<UUID,UUID> result=new HashMap<>();
        var player=Minecraft.getInstance().player;
        // Self-view uses local capture, never a receiving SFU subscription (even in F5).
        for(var entry:wanted.entrySet())if(player!=null&&!entry.getKey().equals(player.getUUID())&&receiving(entry.getKey(),entry.getValue().session))result.put(entry.getKey(),entry.getValue().session);
        return Map.copyOf(result);
    }
    private static boolean receiving(UUID owner,UUID session){
        var client=Minecraft.getInstance();var request=wanted.get(owner);
        if(client.level==null||client.level!=world||client.player==null||request==null||!Objects.equals(session,request.session))return false;
        var player=client.level.getPlayerByUUID(owner);var view=OverheadMedia.INSTANCE.view(owner,System.nanoTime());
        boolean own=owner.equals(client.player.getUUID());
        if(!CameraVisibility.previewAllowed(own,client.options.getCameraType().isFirstPerson()))return false;
        return request.visibility.wanted(player!=null&&view!=null&&view.mode()==OverheadMedia.Mode.CAMERA&&Objects.equals(session,view.session())
            &&com.wildfire.client.music.SpringPlusSession.enabled()&&(own?com.wildfire.client.config.CosmeticPreferences.overheadMode()==OverheadMedia.Mode.CAMERA:com.wildfire.client.config.CosmeticPreferences.overheadCameras()),
            player==null?Double.POSITIVE_INFINITY:player.distanceToSqr(client.player),System.nanoTime());
    }
    public static Texture request(UUID owner,UUID session){
        if(session==null)return null;
        var client=Minecraft.getInstance();
        if(client.level!=world)return null;
        if(client.player!=null&&!CameraVisibility.previewAllowed(owner.equals(client.player.getUUID()),client.options.getCameraType().isFirstPerson()))return null;
        var request=wanted.get(owner);
        if(request==null||!session.equals(request.session)){
            release(owner);request=new Requested(session,new CameraVisibility());wanted.put(owner,request);
        }
        request.visibility.visible(System.nanoTime());
        while(wanted.size()>8){UUID oldest=wanted.keySet().iterator().next();wanted.remove(oldest);release(oldest);}
        Texture texture=textures.get(owner);return texture!=null&&texture.session.equals(session)?texture:null;
    }
    public static void tick(){
        var client=Minecraft.getInstance();long now=System.nanoTime();
        if(!com.wildfire.client.music.SpringPlusSession.enabled()){clear();return;}
        if(client.level!=world){clear();world=client.level;}
        if(client.player!=null)OverheadMedia.INSTANCE.mode(client.player.getUUID(),com.wildfire.client.config.CosmeticPreferences.overheadMode(),now);
        for(UUID owner:List.copyOf(wanted.keySet())){
            if(!receiving(owner,wanted.get(owner).session)){wanted.remove(owner);release(owner);}
        }
        var owners=List.copyOf(wanted.keySet());if(owners.isEmpty())return;
        int uploads=0;
        for(int i=0;i<owners.size()&&uploads<2;i++){
            UUID owner=owners.get(Math.floorMod(cursor++,owners.size()));
            var frame=OverheadMedia.INSTANCE.frameForRender(owner,now);if(frame==null)continue;
            if(!Objects.equals(frame.session(),wanted.get(owner).session))continue;
            uploads++;
            DynamicTexture texture=null;Texture previous=textures.get(owner);
            if(previous!=null&&previous.width==frame.width()&&previous.height==frame.height()&&previous.session.equals(frame.session())
                &&client.getTextureManager().getTexture(previous.id) instanceof DynamicTexture existing)texture=existing;
            NativeImage image=texture==null?new NativeImage(frame.width(),frame.height(),false):texture.getPixels();
            for(int y=0;y<frame.height();y++)for(int x=0;x<frame.width();x++){
                int at=(y*frame.width()+x)*4;byte[] data=frame.rgba();
                int color=(data[at+3]&255)<<24|(data[at]&255)<<16|(data[at+1]&255)<<8|(data[at+2]&255);
                image.setPixel(x,y,color);
            }
            if(texture==null){
                release(owner);Identifier id=Identifier.fromNamespaceAndPath("appearance_sync","overhead_camera/"+owner.toString());
                texture=new CameraTexture(image);client.getTextureManager().register(id,texture);
                textures.put(owner,new Texture(id,frame.width(),frame.height(),frame.session()));
            }else texture.upload();
        }
    }
    private static void release(UUID owner){Texture old=textures.remove(owner);if(old!=null)Minecraft.getInstance().getTextureManager().release(old.id);}
    public static void clear(){for(UUID owner:List.copyOf(textures.keySet()))release(owner);wanted.clear();cursor=0;world=null;OverheadMedia.INSTANCE.clear();}
}
