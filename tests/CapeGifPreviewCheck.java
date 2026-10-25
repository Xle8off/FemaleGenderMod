import com.wildfire.gui.CapeGifPreview;
import javax.imageio.*;
import javax.imageio.metadata.*;
import java.awt.image.BufferedImage;
import java.io.*;
public final class CapeGifPreviewCheck {
 public static void main(String[] args)throws Exception{
  var out=new ByteArrayOutputStream();var writer=ImageIO.getImageWritersByFormatName("gif").next();
  try(var stream=ImageIO.createImageOutputStream(out)){
   writer.setOutput(stream);writer.prepareWriteSequence(null);
   for(int i=0;i<2;i++){
    var image=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);
    var g=image.createGraphics();g.setColor(i==0?java.awt.Color.RED:java.awt.Color.BLUE);g.fillRect(0,0,64,32);g.dispose();
    var metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image),null);
    var tree=(IIOMetadataNode)metadata.getAsTree("javax_imageio_gif_image_1.0");
    var control=(IIOMetadataNode)tree.getElementsByTagName("GraphicControlExtension").item(0);
    control.setAttribute("delayTime",i==0?"10":"20");control.setAttribute("disposalMethod","restoreToBackgroundColor");
    metadata.setFromTree("javax_imageio_gif_image_1.0",tree);
    writer.writeToSequence(new IIOImage(image,null,metadata),null);
   }
   writer.endWriteSequence();
  }finally{writer.dispose();}
  var gif=CapeGifPreview.decode(out.toByteArray());
  if(gif.frames().size()!=2||gif.durationMs()!=300||gif.frameAt(99)!=0||gif.frameAt(100)!=1||gif.frameAt(300)!=0||gif.frameAt(700)!=1)
   throw new AssertionError("loop/timing");
  if(gif.frames().get(0).argb()[0]!=0xFFFF0000||gif.frames().get(1).argb()[0]!=0xFF0000FF)throw new AssertionError("frame colours");
  try{CapeGifPreview.decode(new byte[2*1024*1024+1]);throw new AssertionError("limit");}catch(IOException expected){}
  System.out.println("GIF preview: full decode, frame colours, individual delays, repeated loop and byte limit passed");
 }
}
