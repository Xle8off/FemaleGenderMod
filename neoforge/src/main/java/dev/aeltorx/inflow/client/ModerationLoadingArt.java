package dev.aeltorx.inflow.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Static local artwork: no downloads or per-frame texture uploads. */
public final class ModerationLoadingArt {
 public static final int IMAGE_VARIANTS=6;
 public static final int VARIANTS=IMAGE_VARIANTS+1;
 public static final long DURATION_NANOS=1_600_000_000L;
 private static int previous=-1;
 private ModerationLoadingArt(){}
 public static int nextVariant(){int n=java.util.concurrent.ThreadLocalRandom.current().nextInt(VARIANTS-1);if(previous>=0&&n>=previous)n++;previous=n;return n;}
 public static double progress(long elapsed){return Math.clamp(elapsed/(double)DURATION_NANOS,0,1);}
 public static BufferedImage paint(int width,int height,int variant){
  var result=new BufferedImage(width*3,height*3,BufferedImage.TYPE_INT_ARGB);var g=result.createGraphics();
  try{
   g.scale(3,3);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
   var shape=new java.awt.geom.RoundRectangle2D.Float(0,0,width-1,height-1,12,12);g.setClip(shape);
   g.setColor(ModerationTheme.BACKGROUND);g.fillRect(0,0,width,height);g.setColor(ModerationTheme.CHROME);g.fillRect(0,0,width,34);g.setColor(ModerationTheme.BORDER);g.drawLine(0,34,width,34);
   g.setFont(new Font("Segoe UI",Font.BOLD,11));g.setColor(ModerationTheme.TEXT);center(g,"Модерация",width,22);
   int top=48,bottom=Math.max(top+1,height-32);BufferedImage meme=null;
   if(variant<IMAGE_VARIANTS)try(var stream=ModerationLoadingArt.class.getResourceAsStream("/assets/inflow_moderation/textures/gui/loading_meme_"+(variant+1)+".jpg")){if(stream!=null)meme=ImageIO.read(stream);}catch(java.io.IOException ignored){}
   if(meme!=null){
    int boxW=Math.min(width-32,(bottom-top)*4/3),boxH=boxW*3/4,boxX=(width-boxW)/2,boxY=top+(bottom-top-boxH)/2;
    var oldClip=g.getClip();g.clipRect(boxX,boxY,boxW,boxH);
    float scale=Math.max(boxW/(float)meme.getWidth(),boxH/(float)meme.getHeight());
    int w=Math.max(1,Math.round(meme.getWidth()*scale)),h=Math.max(1,Math.round(meme.getHeight()*scale));
    g.drawImage(meme,boxX+(boxW-w)/2,boxY+(boxH-h)/2,w,h,null);g.setClip(oldClip);
   }
   else{int cy=(top+bottom)/2;g.setColor(ModerationTheme.ACCENT);g.setFont(new Font("Consolas",Font.BOLD,Math.min(27,Math.max(10,width/17))));center(g,"[ МОДЕРАТОР ДУМАЕТ ]",width,cy-6);g.setColor(ModerationTheme.MUTED);g.setFont(new Font("Segoe UI",Font.PLAIN,10));center(g,"Пожалуйста, не мешайте процессу",width,cy+16);}
   g.setClip(null);g.setColor(ModerationTheme.ACCENT);g.draw(shape);
  }finally{g.dispose();}return result;
 }
 private static void center(Graphics2D g,String text,int width,int y){g.drawString(text,(width-g.getFontMetrics().stringWidth(text))/2,y);}
}
