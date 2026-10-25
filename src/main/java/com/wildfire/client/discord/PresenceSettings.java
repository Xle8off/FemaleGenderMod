package com.wildfire.client.discord;

import java.nio.file.*;
import java.util.Properties;
import java.io.*;

/** Local opt-in only. No login tokens, coordinates or arbitrary join addresses. */
public final class PresenceSettings {
 public static final String APPLICATION_ID="1512105518779338843",SERVER="springplus.net",SITE="https://forum.springplus.net",JOIN="springplus:join:v1";
 private static final Path FILE=Path.of("config","springplus-discord.properties");
 private static boolean loaded;
 private static final Properties VALUES=new Properties();
 private PresenceSettings(){}
 public static synchronized boolean get(String key){load();return Boolean.parseBoolean(VALUES.getProperty(key,"false"));}
 public static synchronized void toggle(String key){boolean next=!get(key);VALUES.setProperty(key,Boolean.toString(next));save();}
 private static void load(){if(loaded)return;loaded=true;try(var in=Files.newInputStream(FILE)){VALUES.load(in);}catch(IOException ignored){}}
 private static void save(){try{Files.createDirectories(FILE.getParent());Path tmp=FILE.resolveSibling(FILE.getFileName()+".tmp");try(var out=Files.newOutputStream(tmp)){VALUES.store(out,"SpringPlus Discord privacy preferences");}Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}catch(IOException e){System.getLogger("SpringPlus-Discord").log(System.Logger.Level.WARNING,"Cannot save Discord preferences");}}
}
