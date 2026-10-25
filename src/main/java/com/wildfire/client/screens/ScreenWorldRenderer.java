package com.wildfire.client.screens;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.springplus.screens.ScreenGeometry;

/** One depth-tested plane per registered screen, never a renderer per block. */
public final class ScreenWorldRenderer {
 private ScreenWorldRenderer(){}
 public static void submit(PoseStack pose,SubmitNodeCollector out,CameraRenderState camera){
  if(!ScreenQuality.enabled())return;
  var mc=Minecraft.getInstance();if(mc.level==null||camera==null||camera.pos==null)return;
  var forward=new org.joml.Vector3f(0,0,-1).rotate(camera.orientation);
  java.util.UUID visible=null;double best=Double.MAX_VALUE;
  for(var screen:ScreenClient.screens(mc.level)){
   var g=screen.geometry();var c=ScreenSurface.point(g,g.width()/2.0,g.height()/2.0);
   double dx=c.x()-camera.pos.x,dy=c.y()-camera.pos.y,dz=c.z()-camera.pos.z;
   if(dx*forward.x+dy*forward.y+dz*forward.z < -Math.hypot(g.width(),g.height())/2)continue;
   if(g.distanceSquared(camera.pos.x,camera.pos.y,camera.pos.z)>ScreenInteraction.RANGE*ScreenInteraction.RANGE||!ScreenSurface.front(g,camera.pos.x,camera.pos.y,camera.pos.z)||!mc.level.hasChunk(g.x()>>4,g.z()>>4))continue;
   if(camera.cullFrustum==null||!camera.cullFrustum.isVisible(new net.minecraft.world.phys.AABB(g.x()-.01,g.y()-.01,g.z()-.01,g.maxX()+1.01,g.maxY()+1.01,g.maxZ()+1.01)))continue;
   boolean selected=screen.id().equals(BrowserRuntime.selected());
   if(screen.mediaActive()){double score=selected?-1:g.distanceSquared(camera.pos.x,camera.pos.y,camera.pos.z);if(score<best){best=score;visible=screen.id();}}
   if(selected&&BrowserRuntime.running())BrowserRuntime.wantFrame();
   var texture=selected&&BrowserRuntime.running()?BrowserRuntime.texture():null;
   var a=local(g,ScreenSurface.point(g,0,0));var b=local(g,ScreenSurface.point(g,0,g.height()));var d=local(g,ScreenSurface.point(g,g.width(),0));var e=local(g,ScreenSurface.point(g,g.width(),g.height()));
   pose.pushPose();pose.translate(g.x()-camera.pos.x,g.y()-camera.pos.y,g.z()-camera.pos.z);
   out.submitCustomGeometry(pose,RenderTypes.debugQuads(),(matrix,v)->{
    plain(matrix,v,a);plain(matrix,v,b);plain(matrix,v,e);plain(matrix,v,d);
   });
   if(texture!=null){
    double tw=g.width(),th=g.height(),u=0,vv=0;
    var ta=local(g,ScreenSurface.point(g,u,vv,.003));var tb=local(g,ScreenSurface.point(g,u,vv+th,.003));var td=local(g,ScreenSurface.point(g,u+tw,vv,.003));var te=local(g,ScreenSurface.point(g,u+tw,vv+th,.003));
    out.submitCustomGeometry(pose,RenderTypes.text(texture),(matrix,v)->{tex(matrix,v,ta,0,0);tex(matrix,v,tb,0,1);tex(matrix,v,te,1,1);tex(matrix,v,td,1,0);});
   }
   pose.popPose();
  }
  ScreenInteraction.visible(visible);
 }
 private static ScreenSurface.Point local(ScreenGeometry g,ScreenSurface.Point p){return new ScreenSurface.Point(p.x()-g.x(),p.y()-g.y(),p.z()-g.z());}
 private static void plain(PoseStack.Pose p,VertexConsumer v,ScreenSurface.Point a){v.addVertex(p,(float)a.x(),(float)a.y(),(float)a.z()).setColor(0xFF11151C).setLight(0xF000F0);}
 private static void tex(PoseStack.Pose p,VertexConsumer v,ScreenSurface.Point a,float u,float t){v.addVertex(p,(float)a.x(),(float)a.y(),(float)a.z()).setColor(0xFFFFFFFF).setUv(u,t).setLight(0xF000F0);}
}
