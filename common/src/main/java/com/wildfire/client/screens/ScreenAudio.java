package com.wildfire.client.screens;
import net.minecraft.client.Minecraft;
import com.wildfire.client.music.MusicPriority;
public final class ScreenAudio {
 private static volatile float distanceGain;
 private static volatile boolean inRange;
 private ScreenAudio(){}
 /** Called on the game thread; worker threads only read the resulting scalar. */
 public static void tick(){
  var mc=Minecraft.getInstance();distanceGain=0;inRange=false;
  if(!ScreenQuality.enabled()||mc.player==null||mc.level==null||BrowserRuntime.selected()==null)return;
  for(var s:ScreenClient.screens(mc.level))if(s.id().equals(BrowserRuntime.selected())&&s.mediaActive()){
   double distance=ScreenAudioMath.distance(s.geometry(),mc.player.getX(),mc.player.getY(),mc.player.getZ());
   distanceGain=ScreenAudioMath.gain(distance);inRange=distance<48;break;
  }
 }
 public static boolean inRange(){return inRange;}
 public static boolean playing(){return inRange&&BrowserRuntime.running()&&BrowserRuntime.sharedPlaying()&&BrowserRuntime.volume()>0;}
 public static float gain(){return MusicPriority.clientMuted()?0:distanceGain;}
 public static String site(){try{String host=java.net.URI.create(BrowserRuntime.pageUrl()).getHost();return host==null?"Экран":host.replaceFirst("^www\\.","");}catch(Exception e){return "Экран";}}
}
