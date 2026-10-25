package dev.aeltorx.inflow.client;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
/** Bounded CPU artwork queue. Only the final bulk texture copy/upload runs on the render thread. */
public final class ModerationRaster {
 private ModerationRaster(){}
 private static final java.util.Map<BufferedImage,int[]> PREPARED=new ConcurrentHashMap<>();
 private static int[] pixels(BufferedImage image){int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());for(int i=0;i<pixels.length;i++){int c=pixels[i];pixels[i]=(c&0xff00ff00)|((c>>>16)&255)|((c&255)<<16);}return pixels;}
 private static final ExecutorService WORK=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"SpringPlus moderation artwork");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 public static void paint(Supplier<BufferedImage> paint,Consumer<BufferedImage> ready){
  try{WORK.execute(()->{BufferedImage image=null;try{image=paint.get();PREPARED.put(image,pixels(image));}catch(Exception e){org.slf4j.LoggerFactory.getLogger("InFlow/Art").warn("Moderation artwork failed",e);}var result=image;Minecraft.getInstance().execute(()->{try{ready.accept(result);}finally{if(result!=null)PREPARED.remove(result);}});});}
  catch(RejectedExecutionException busy){Minecraft.getInstance().execute(()->ready.accept(null));}
 }
 public static void upload(DynamicTexture texture,BufferedImage image){
  int[] pixels=PREPARED.get(image);if(pixels==null)pixels=pixels(image);
  texture.getPixels().getPixelBytes().order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(pixels);texture.upload();
 }
}
