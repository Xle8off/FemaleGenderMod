package com.wildfire.gui;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
/** Ephemeral server-reported capability, never inferred from skin or online state. */
public final class ModPresence {
 private static final Map<String,Long> users=new HashMap<>();
 private static Object connection;
 private ModPresence(){}
 private static void session(){var client=Minecraft.getInstance();Object current=client==null?null:client.getConnection();if(current!=connection){connection=current;users.clear();}}
 public static void accept(JsonElement node){
  session();if(node==null||!node.isJsonObject()&&!node.isJsonArray())return;
  if(node.isJsonArray()){for(var child:node.getAsJsonArray())accept(child);return;}
  var object=node.getAsJsonObject();
  if(object.has("hasMod")){
   for(String key:List.of("nickname","name","partner","senderName","targetName"))
    if(object.has(key)&&object.get(key).isJsonPrimitive()){
     String name=object.get(key).getAsString().toLowerCase(Locale.ROOT);
     if(name.matches("[a-z0-9_]{1,16}")){
      if(object.get("hasMod").getAsBoolean()){
       if(users.size()>=1024&&!users.containsKey(name))users.remove(Collections.min(users.entrySet(),Map.Entry.comparingByValue()).getKey());
       users.put(name,System.currentTimeMillis());
      }else users.remove(name);
      break;
     }
    }
  }
  for(var child:object.entrySet())if(child.getValue().isJsonObject()||child.getValue().isJsonArray())accept(child.getValue());
 }
 public static boolean has(String name){session();return name!=null&&users.containsKey(name.toLowerCase(Locale.ROOT));}
 public static void draw(GuiGraphicsExtractor g,String name,int x,int y,int mx,int my){
  if(!has(name))return;
  g.fill(x,y,x+9,y+9,0xFF20202D);g.fill(x+1,y+1,x+8,y+8,0xFFAE86D3);
  g.fill(x+2,y+2,x+6,y+3,0xFFFFFFFF);g.fill(x+2,y+3,x+3,y+5,0xFFFFFFFF);
  g.fill(x+2,y+4,x+6,y+5,0xFFFFFFFF);g.fill(x+5,y+5,x+6,y+7,0xFFFFFFFF);g.fill(x+2,y+6,x+6,y+7,0xFFFFFFFF);
  if(mx>=x&&mx<x+9&&my>=y&&my<y+9)g.setTooltipForNextFrame(Component.literal("Использует SpringPlus"),mx,my);
 }
}
