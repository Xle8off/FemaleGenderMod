package dev.aeltorx.inflow.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
/** Host supplies the existing batched SpringPlus avatar cache, never another HTTP client. */
public final class ModerationPeople {
 @FunctionalInterface public interface Heads {void draw(GuiGraphicsExtractor g,String name,int x,int y,int size);}
 private static Heads heads=(g,n,x,y,s)->{};
 private static Object session;
 private static final java.util.Map<String,String> prefixes=new java.util.LinkedHashMap<>();
 public static void person(com.google.gson.JsonObject person){checkSession();if(person.has("name")&&person.has("prefix")){if(prefixes.size()>=512)prefixes.remove(prefixes.keySet().iterator().next());prefixes.put(person.get("name").getAsString().toLowerCase(java.util.Locale.ROOT),person.get("prefix").getAsString());}}
 private static void checkSession(){Object current=Minecraft.getInstance().getConnection();if(session!=current){session=current;prefixes.clear();}}
 private ModerationPeople(){}
 public static void install(Heads renderer){heads=java.util.Objects.requireNonNull(renderer);}
 public static void head(GuiGraphicsExtractor g,String name,int x,int y,int size){if(name.matches("[A-Za-z0-9_]{1,16}"))heads.draw(g,name,x,y,size);}
 public static Component name(String name){checkSession();var connection=Minecraft.getInstance().getConnection();if(connection!=null)for(var info:connection.getOnlinePlayers())if(info.getProfile().name().equalsIgnoreCase(name)){var display=info.getTabListDisplayName();if(display!=null)return display;}String prefix=prefixes.getOrDefault(name.toLowerCase(java.util.Locale.ROOT),"");return Component.literal(prefix+(prefix.isBlank()||prefix.endsWith(" ")?"":" ")+name);}
 public static Component label(String name,Component original){String text=original.getString();return name(name).copy().append(text.startsWith(name)?text.substring(name.length()):"");}
}
