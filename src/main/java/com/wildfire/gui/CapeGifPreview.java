package com.wildfire.gui;
import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import org.w3c.dom.Node;
/** Bounded GIF compositor for cape previews. Original upload bytes remain untouched. */
public record CapeGifPreview(int width,int height,List<Frame> frames,int durationMs) {
 public record Frame(int[] argb,int durationMs){}
 public int frameAt(long elapsedMillis){
  long time=Math.floorMod(elapsedMillis,(long)durationMs);
  for(int i=0;i<frames.size();i++){time-=frames.get(i).durationMs();if(time<0)return i;}
  return 0;
 }
 public static CapeGifPreview decode(byte[] bytes)throws IOException{
  if(bytes.length>2*1024*1024||bytes.length<13)throw new IOException("GIF: максимум 2 МБ");
  int width=(bytes[6]&255)|(bytes[7]&255)<<8,height=(bytes[8]&255)|(bytes[9]&255)<<8;
  if(width<64||width>256||Integer.bitCount(width)!=1||height!=width/2)throw new IOException("GIF-плащ: 64, 128 или 256 px, пропорции 2:1");
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
   var reader=ImageIO.getImageReadersByFormatName("gif").next();
   try{
    reader.setInput(input,false,false);int count=reader.getNumImages(true);
    if(count<2||count>24)throw new IOException("GIF: от 2 до 24 кадров");
    var canvas=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
    BufferedImage restore=null;String previous="none";int px=0,py=0,pw=0,ph=0,total=0;
    var frames=new ArrayList<Frame>();
    for(int i=0;i<count;i++){
     if(previous.equals("restoreToBackgroundColor")){var g=canvas.createGraphics();g.setComposite(AlphaComposite.Clear);g.fillRect(px,py,pw,ph);g.dispose();}
     else if(previous.equals("restoreToPrevious")&&restore!=null){var g=canvas.createGraphics();g.setComposite(AlphaComposite.Src);g.drawImage(restore,0,0,null);g.dispose();}
     Node metadata=reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
     Node descriptor=child(metadata,"ImageDescriptor"),control=child(metadata,"GraphicControlExtension");
     if(descriptor==null)throw new IOException("GIF: повреждённый кадр");
     int x=number(descriptor,"imageLeftPosition"),y=number(descriptor,"imageTopPosition"),w=number(descriptor,"imageWidth"),h=number(descriptor,"imageHeight");
     if(x<0||y<0||w<1||h<1||x+w>width||y+h>height)throw new IOException("GIF: неверные границы кадра");
     String disposal=control==null?"none":attribute(control,"disposalMethod");
     if(disposal.equals("restoreToPrevious")){restore=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);var g=restore.createGraphics();g.drawImage(canvas,0,0,null);g.dispose();}else restore=null;
     var g=canvas.createGraphics();g.setComposite(AlphaComposite.SrcOver);g.drawImage(reader.read(i),x,y,null);g.dispose();
     int delay=control==null?100:Math.max(50,number(control,"delayTime")*10);total+=delay;
     if(total>30000)throw new IOException("GIF: максимум 30 секунд");
     frames.add(new Frame(canvas.getRGB(0,0,width,height,null,0,width),delay));
     previous=disposal;px=x;py=y;pw=w;ph=h;
    }
    return new CapeGifPreview(width,height,List.copyOf(frames),total);
   }finally{reader.dispose();}
  }catch(RuntimeException error){throw new IOException("Некорректный GIF",error);}
 }
 private static Node child(Node root,String name){for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling())if(n.getNodeName().equals(name))return n;return null;}
 private static String attribute(Node node,String name){var n=node.getAttributes().getNamedItem(name);return n==null?"":n.getNodeValue();}
 private static int number(Node node,String name){return Integer.parseInt(attribute(node,name));}
}
