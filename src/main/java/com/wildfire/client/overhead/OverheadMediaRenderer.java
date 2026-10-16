package com.wildfire.client.overhead;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.FormattedCharSequence;
import com.wildfire.client.config.CosmeticPreferences;
import java.util.*;

/** One billboard layer; camera and public-message bubbles are mutually exclusive. */
public final class OverheadMediaRenderer {
    private record Text(List<FormattedCharSequence> lines,int width) {}
    private static final Map<OverheadMedia.Message,Text> textCache=new WeakHashMap<>();
    private OverheadMediaRenderer(){}
    public static void submit(UUID owner,AvatarRenderState state,PoseStack pose,SubmitNodeCollector out,CameraRenderState camera){
        var client=Minecraft.getInstance();
        if(!com.wildfire.client.music.SpringPlusSession.enabled()||client.level==null||camera.orientation==null||state.isInvisible||state.isInvisibleToPlayer||state.isDiscrete||state.distanceToCameraSq>CameraVisibility.MAX_DISTANCE_SQUARED
            ||camera!=client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState)return;
        boolean own=client.player!=null&&owner.equals(client.player.getUUID());
        if(own&&client.options.getCameraType().isFirstPerson())return;
        var view=OverheadMedia.INSTANCE.view(owner,System.nanoTime());if(view==null||view.mode()==OverheadMedia.Mode.OFF)return;
        if(own&&view.mode()!=CosmeticPreferences.overheadMode())return;
        boolean video=view.mode()==OverheadMedia.Mode.CAMERA;
        if(video&&!own&&!CosmeticPreferences.overheadCameras())return;
        if(!video&&(!CosmeticPreferences.overheadMessages()||view.message()==null))return;
        pose.pushPose();
        try{
            pose.translate(0,state.boundingBoxHeight+(video?1.0:1.6),0);pose.mulPose(camera.orientation);pose.scale(.018f,-.018f,.018f);
            if(video){camera(owner,view,pose,out);return;}
            var message=view.message();var sticker=com.wildfire.gui.WebStickers.message(message.body());
            if(sticker!=null){
                var sprite=com.wildfire.gui.WebStickers.worldSprite(sticker);if(sprite==null)return;
                float scale=48f/Math.max(sprite.width(),sprite.height()),w=sprite.width()*scale,h=sprite.height()*scale;
                out.submitCustomGeometry(pose,RenderTypes.text(sprite.texture()),(matrix,v)->quad(matrix,v,-w/2,-h/2,w/2,h/2,0,0,sprite.v0(),1,sprite.v1(),0xFFFFFFFF));return;
            }
            if(com.wildfire.gui.WebStickers.isMessage(message.body()))return;
            Text prepared=textCache.computeIfAbsent(message,m->{
                var lines=client.font.split(com.wildfire.gui.ChatReactions.text(m.body()),128).stream().limit(3).toList();
                return new Text(lines,Math.max(24,lines.stream().mapToInt(client.font::width).max().orElse(24))+12);
            });
            float left=-prepared.width/2f,top=-prepared.lines.size()*10f/2-5,bottom=-top;
            out.submitCustomGeometry(pose,RenderTypes.textBackground(),(matrix,v)->{
                rect(matrix,v,left+3,top,left+prepared.width-3,top+3,0xD91B2029);
                rect(matrix,v,left,top+3,left+prepared.width,bottom-3,0xD91B2029);
                rect(matrix,v,left+3,bottom-3,left+prepared.width-3,bottom,0xD91B2029);
            });
            pose.translate(0,0,.05);
            for(int i=0;i<prepared.lines.size();i++)out.submitText(pose,left+6,top+5+i*10,prepared.lines.get(i),false,Font.DisplayMode.NORMAL,0xF000F0,0xFFF0EDF7,0,0);
        }finally{pose.popPose();}
    }
    private static void camera(UUID owner,OverheadMedia.View view,PoseStack pose,SubmitNodeCollector out){
        var texture=OverheadCameraTextures.request(owner,view.session());
        // No placeholder circle: only show actual live frames, never an old paused/off frame.
        if(view.camera()!=OverheadMedia.CameraStatus.LIVE||texture==null)return;
        if(texture!=null){
            float us=Math.min(1f,texture.height()/(float)texture.width()),vs=Math.min(1f,texture.width()/(float)texture.height());
            out.submitCustomGeometry(pose,RenderTypes.text(texture.id()),(matrix,v)->{
                for(int i=0;i<CameraCircleGeometry.SEGMENTS;i++){
                    double a=CameraCircleGeometry.angle(i),b=CameraCircleGeometry.angle(i+1);
                    float ax=(float)Math.sin(a),ay=(float)-Math.cos(a),bx=(float)Math.sin(b),by=(float)-Math.cos(b);
                    vertex(matrix,v,0,0,.08f,.5f,.5f,0xFFFFFFFF);
                    vertex(matrix,v,ax*30,ay*30,.08f,.5f+ax*us/2,.5f+ay*vs/2,0xFFFFFFFF);
                    vertex(matrix,v,bx*30,by*30,.08f,.5f+bx*us/2,.5f+by*vs/2,0xFFFFFFFF);
                    vertex(matrix,v,0,0,.08f,.5f,.5f,0xFFFFFFFF);
                }
            });
        }else{
            String label=view.camera()==OverheadMedia.CameraStatus.PAUSED?"Пауза":"Камера";
            var font=Minecraft.getInstance().font;
            pose.translate(0,0,.1);
            out.submitText(pose,-font.width(label)/2f,-4,net.minecraft.network.chat.Component.literal(label).getVisualOrderText(),false,Font.DisplayMode.NORMAL,0xF000F0,0xFFACB6C5,0,0);
            if(view.camera()!=OverheadMedia.CameraStatus.PAUSED)out.submitText(pose,-font.width("ожидание")/2f,7,net.minecraft.network.chat.Component.literal("ожидание").getVisualOrderText(),false,Font.DisplayMode.NORMAL,0xF000F0,0xFFACB6C5,0,0);
        }
    }
    private static void vertex(PoseStack.Pose p,VertexConsumer v,float x,float y,float z,float u,float t,int color){v.addVertex(p,x,y,z).setColor(color).setUv(u,t).setLight(0xF000F0);}
    private static void quad(PoseStack.Pose p,VertexConsumer v,float x1,float y1,float x2,float y2,float z,float u0,float v0,float u1,float v1,int color){
        vertex(p,v,x1,y1,z,u0,v0,color);vertex(p,v,x1,y2,z,u0,v1,color);vertex(p,v,x2,y2,z,u1,v1,color);vertex(p,v,x2,y1,z,u1,v0,color);
    }
    private static void rect(PoseStack.Pose p,VertexConsumer v,float x1,float y1,float x2,float y2,int color){
        v.addVertex(p,x1,y1,0).setColor(color).setLight(0xF000F0);v.addVertex(p,x1,y2,0).setColor(color).setLight(0xF000F0);
        v.addVertex(p,x2,y2,0).setColor(color).setLight(0xF000F0);v.addVertex(p,x2,y1,0).setColor(color).setLight(0xF000F0);
    }
    private static void circle(PoseStack.Pose p,VertexConsumer v,float radius,int color){
        for(int i=0;i<CameraCircleGeometry.SEGMENTS;i++){
            double a=CameraCircleGeometry.angle(i),b=CameraCircleGeometry.angle(i+1);
            v.addVertex(p,0,0,0).setColor(color).setLight(0xF000F0);
            v.addVertex(p,(float)Math.sin(a)*radius,(float)-Math.cos(a)*radius,0).setColor(color).setLight(0xF000F0);
            v.addVertex(p,(float)Math.sin(b)*radius,(float)-Math.cos(b)*radius,0).setColor(color).setLight(0xF000F0);
            v.addVertex(p,0,0,0).setColor(color).setLight(0xF000F0);
        }
    }
}
