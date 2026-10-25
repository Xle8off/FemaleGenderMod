package com.wildfire.client.discord;

import com.google.gson.*;
import java.util.*;

/** Pure payload builder. Values come from the current client session, never another server. */
public final class PresenceActivity {
 public record Snapshot(boolean springplus,boolean singleplayer,String nickname,String privilege,String city,String cityRole,String world,int listedPlayers,long started,int serverOnline,int serverMax,String cityAvatar,boolean capital){
  public Snapshot(boolean springplus,boolean singleplayer,String nickname,String privilege,String city,String cityRole,String world,int listedPlayers,long started,int serverOnline,int serverMax,String cityAvatar){this(springplus,singleplayer,nickname,privilege,city,cityRole,world,listedPlayers,started,serverOnline,serverMax,cityAvatar,false);}
  public Snapshot(boolean springplus,boolean singleplayer,String nickname,String privilege,String city,String cityRole,String world,int listedPlayers,long started){this(springplus,singleplayer,nickname,privilege,city,cityRole,world,listedPlayers,started,-1,-1,"");}
 }
 public record Visibility(boolean nickname,boolean privilege,boolean city,boolean world){}
 private PresenceActivity(){}
 /** Presence-only fallback, not an authentication/permission gate for other mod features. */
 public static boolean springPlusAddress(String address){
  if(address==null)return false;String value=address.trim().toLowerCase(Locale.ROOT);
  int colon=value.lastIndexOf(':');if(colon>=0){if(!value.substring(colon+1).equals("25565"))return false;value=value.substring(0,colon);}
  if(value.endsWith("."))value=value.substring(0,value.length()-1);
  return value.equals("springplus.net")||value.equals("mc.springplus.net");
 }
 public static String cityImage(String hash){return hash!=null&&hash.matches("[a-f0-9]{64}")?"https://skin.springplus.net/api/chat-images/"+hash+".png":"";}
 public static String clean(String value,int max){if(value==null)return "";String clean=value.replaceAll("§.","").replaceAll("[\\p{Cntrl}]","").trim();return clean.codePointCount(0,clean.length())<=max?clean:clean.substring(0,clean.offsetByCodePoints(0,max));}
 public static JsonObject build(Snapshot s,Visibility v){
  var a=new JsonObject();a.addProperty("name","SpringPlus");a.addProperty("type",0);a.addProperty("details",s.springplus()?"Играет на SpringPlus":s.singleplayer()?"Одиночная игра":s.started()>0?"Играет в Minecraft":"В главном меню");
  var lines=new ArrayList<String>();
  if(s.springplus()){
   if(v.nickname()&&!clean(s.nickname(),16).isEmpty())lines.add(clean(s.nickname(),16));
   if(v.privilege()&&!clean(s.privilege(),40).isEmpty())lines.add(clean(s.privilege(),40));
   if(v.city()&&!clean(s.city(),48).isEmpty())lines.add((s.capital()?"В столице ":"В городе ")+clean(s.city(),48));
   if(v.world()&&!clean(s.world(),32).isEmpty())lines.add(clean(s.world(),32));
  }
  a.addProperty("state",lines.isEmpty()?(s.springplus()?"Сервер выживания":"SpringPlus"):clean(String.join(" · ",lines),120));
  var assets=new JsonObject();assets.addProperty("large_image","springplus");assets.addProperty("large_text",s.springplus()?"SpringPlus · игроков в списке: "+s.listedPlayers():"SpringPlus");
  boolean showCity=s.springplus()&&v.city()&&!clean(s.city(),48).isEmpty();String cityImage=showCity?cityImage(s.cityAvatar()):"";
  if(!cityImage.isEmpty()){assets.addProperty("large_image",cityImage);assets.addProperty("large_text",clean("Город: "+clean(s.city(),48)+(clean(s.cityRole(),40).isEmpty()?"":" · "+clean(s.cityRole(),40)),120));assets.addProperty("small_image","springplus");assets.addProperty("small_text","SpringPlus · игроков в списке: "+s.listedPlayers());}
  else if(showCity&&!clean(s.cityRole(),40).isEmpty()){assets.addProperty("small_image","springplus");assets.addProperty("small_text","Роль в городе: "+clean(s.cityRole(),40));}a.add("assets",assets);
  if(s.started()>0){var times=new JsonObject();times.addProperty("start",s.started());a.add("timestamps",times);}
  // Site-only presence: never attach join secrets, even with an old join=true config.
  a.addProperty("details_url",PresenceSettings.SITE);assets.addProperty("large_url",PresenceSettings.SITE);
  var buttons=new JsonArray();var site=new JsonObject();site.addProperty("label","Сайт");site.addProperty("url",PresenceSettings.SITE);buttons.add(site);a.add("buttons",buttons);
  return a;
 }
}
