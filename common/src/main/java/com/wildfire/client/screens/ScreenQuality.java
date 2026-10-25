package com.wildfire.client.screens;
import java.nio.file.*;
import java.util.Properties;
/** Local capture quality. Never alters the shared browser or other viewers. */
public final class ScreenQuality {
 private static final Path FILE=Path.of("config","springplus-screen.properties");
 private static int height=720,fps=30;
 private static boolean enabled=true;
 static{
  var p=new Properties();try(var in=Files.newInputStream(FILE)){p.load(in);}catch(Exception ignored){}
  enabled=Boolean.parseBoolean(p.getProperty("enabled","true"));
  try{int h=Integer.parseInt(p.getProperty("height","720"));if(h==920||h==720||h==480)height=h;}catch(NumberFormatException ignored){}
  try{int f=Integer.parseInt(p.getProperty("fps","30"));if(f==60||f==30||f==24)fps=f;}catch(NumberFormatException ignored){}
 }
 private ScreenQuality(){}
 public static int height(){return height;}
 public static int fps(){return fps;}
 public static boolean enabled(){return enabled;}
 public static void toggleEnabled(){enabled=!enabled;save();}
 public static void cycleHeight(){height=switch(height){case 920->720;case 720->480;default->920;};save();}
 public static void cycleFps(){fps=switch(fps){case 60->30;case 30->24;default->60;};save();}
 public static net.springplus.screens.ScreenViewport scale(net.springplus.screens.ScreenViewport viewport){
  if(viewport.height()<=height)return viewport;
  int gcd=gcd(viewport.width(),viewport.height()),w=viewport.width()/gcd,h=viewport.height()/gcd;
  int factor=Math.max(1,Math.min(gcd,height/h));
  return new net.springplus.screens.ScreenViewport(w*factor,h*factor);
 }
 private static int gcd(int a,int b){while(b!=0){int t=a%b;a=b;b=t;}return a;}
 private static void save(){try{Files.createDirectories(FILE.getParent());var p=new Properties();p.setProperty("enabled",Boolean.toString(enabled));p.setProperty("height",Integer.toString(height));p.setProperty("fps",Integer.toString(fps));var temp=Files.createTempFile(FILE.getParent(),"screen-quality-",".tmp");try{try(var out=Files.newOutputStream(temp)){p.store(out,"SpringPlus local screen render quality");}Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}catch(Exception ignored){}}
}
