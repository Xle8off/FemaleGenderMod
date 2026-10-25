package com.wildfire.client.music;
import java.nio.file.*;
import java.util.Properties;
/** Only local audio preferences, never synchronized with other players. */
public final class AudioPreferences {
 private static final Path FILE=Path.of("config","springplus-audio.properties");
 private static volatile boolean clientPriority;
 private static volatile float screenVolume=.5f;
 static {try(var in=Files.newInputStream(FILE)){var p=new Properties();p.load(in);clientPriority=Boolean.parseBoolean(p.getProperty("client-priority","false"));float v=Float.parseFloat(p.getProperty("screen-volume","0.5"));if(Float.isFinite(v))screenVolume=Math.clamp(v,0,1);}catch(Exception ignored){}}
 private AudioPreferences(){}
 public static boolean clientPriority(){return clientPriority;}
 public static float screenVolume(){return screenVolume;}
 public static void screenVolume(float v){if(Float.isFinite(v))screenVolume=Math.clamp(v,0,1);}
 public static void togglePriority(){clientPriority=!clientPriority;save();}
 public static synchronized void save(){try{Files.createDirectories(FILE.getParent());var p=new Properties();p.setProperty("client-priority",Boolean.toString(clientPriority));p.setProperty("screen-volume",Float.toString(screenVolume));var tmp=Files.createTempFile(FILE.getParent(),"audio-",".tmp");try{try(var out=Files.newOutputStream(tmp)){p.store(out,"SpringPlus local audio");}Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}}catch(Exception ignored){}}
}
