package com.wildfire.client.music;
import com.wildfire.gui.DiscVoiceVolume;
/** Discs are muted only on this client; personal players retain their pause/resume semantics. */
public final class MusicPriority {
 private static double restore=-1;
 private static boolean server,clientPlaying;
 private static volatile boolean discsMuted;
 public static boolean discsMuted(){return discsMuted;}
 private static volatile boolean clientMuted;
 private static volatile String notice="";
 private MusicPriority(){}
 public static boolean clientMuted(){return clientMuted;}
 public static String notice(){return notice;}
 public static void tick(boolean discs){
  server=discs;
  boolean preferClient=AudioPreferences.clientPriority();
  var local=PersonalMusicPlayer.view();var web=SpotifyWebPlayer.view();
  clientPlaying=local.playing()||web.playing()||com.wildfire.client.screens.ScreenAudio.playing();
  clientMuted=discs&&!preferClient;
  PersonalMusicPlayer.serverPlaying(clientMuted);
  SpotifyWebPlayer.serverPlaying(clientMuted);
  boolean silenceDiscs=preferClient&&clientPlaying&&discs;
  if(silenceDiscs){double current=DiscVoiceVolume.get();if(current>=0){if(restore<0||current>0)restore=current;DiscVoiceVolume.set(0,false);}}
  else release();
  discsMuted=silenceDiscs&&restore>=0;
  boolean personalPaused=discs&&!preferClient&&(local.track()!=null||web.running()&&!web.title().isBlank());
  notice=silenceDiscs?"Диски заглушены: остановите свою музыку или измените приоритет":
   clientMuted&&com.wildfire.client.screens.ScreenAudio.playing()?"Экран заглушён: остановите диски или измените приоритет":
   personalPaused?"Личная музыка на паузе: играют диски":"";
 }
 private static void release(){if(restore>=0){double current=DiscVoiceVolume.get();if(current==0)DiscVoiceVolume.set(restore,false);restore=-1;}}
 public static void reset(){release();discsMuted=false;clientMuted=false;notice="";PersonalMusicPlayer.serverPlaying(false);SpotifyWebPlayer.serverPlaying(false);}
}
