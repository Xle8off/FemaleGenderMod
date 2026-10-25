package com.wildfire.client.discord;

import net.minecraft.client.Minecraft;
import com.wildfire.client.music.SpringPlusSession;
import com.wildfire.client.towns.TownsClient;
import com.wildfire.client.towns.TerritoryPresence;

/** Called on the client tick even outside an authenticated SpringPlus connection. */
public final class DiscordPresence {
 private static DiscordIpc ipc;private static Object connection;private static long started,nextTick;
 private DiscordPresence(){}
 public static void tick(Minecraft mc,String privilege){
  if(!PresenceSettings.get("enabled")){TerritoryPresence.reset();if(ipc!=null){ipc.close();ipc=null;}return;}
  long now=System.currentTimeMillis();if(now<nextTick)return;nextTick=now+1000;
  if(ipc==null)ipc=new DiscordIpc();
  Object current=mc.getConnection();if(connection!=current){TerritoryPresence.reset();connection=current;started=now/1000;}
  var server=mc.getCurrentServer();
  boolean active=mc.player!=null&&mc.level!=null&&current!=null&&!mc.hasSingleplayerServer()&&(SpringPlusSession.enabled()||server!=null&&PresenceActivity.springPlusAddress(server.ip));
  String world="",city="",role="",cityAvatar="";boolean capital=false;
  if(active){
   String dimension=mc.level.dimension().identifier().getPath();world=switch(dimension){case "overworld"->"Обычный мир";case "the_nether"->"Незер";case "the_end"->"Энд";default->"Другой мир";};
   if(PresenceSettings.get("city")){
    var territory=TerritoryPresence.at(mc.level,mc.player.getBlockX()>>4,mc.player.getBlockZ()>>4,token->TownsClient.action(9,"",token));
    if(territory!=null){city=territory.name();capital=territory.capital();cityAvatar=territory.avatar();}
   }else TerritoryPresence.reset();
  }else TerritoryPresence.reset();
  var population=active&&server!=null?server.players:null;
  var snapshot=new PresenceActivity.Snapshot(active,mc.hasSingleplayerServer(),active?mc.player.getName().getString():"",active?privilege:"",city,role,world,active?mc.getConnection().getOnlinePlayers().size():0,current==null?0:started,population==null?-1:population.online(),population==null?-1:population.max(),cityAvatar,capital);
  ipc.activity(PresenceActivity.build(snapshot,new PresenceActivity.Visibility(PresenceSettings.get("nickname"),PresenceSettings.get("privilege"),PresenceSettings.get("city"),PresenceSettings.get("world"))));
 }
 public static String status(){return !PresenceSettings.get("enabled")?"Rich Presence выключен":ipc==null?"Подключаемся к Discord…":ipc.status();}
}
