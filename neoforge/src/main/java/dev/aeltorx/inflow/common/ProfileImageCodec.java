package dev.aeltorx.inflow.common;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** Shared bounded raster decoder; used only on file workers, never on either game tick. */
public final class ProfileImageCodec {
    public static final int MAX_SOURCE=8*1024*1024,MAX_STORED=512*1024,CHUNK=8192;
    public static byte[] normalize(byte[] bytes,String kind)throws IOException{
        if(!kind.equals("avatar")&&!kind.equals("banner"))throw new IllegalArgumentException("Неверный вид изображения");
        if(bytes.length<1||bytes.length>MAX_SOURCE)throw new IOException("Изображение должно быть до 8 МиБ");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Поддерживаются PNG и JPEG");var reader=readers.next();try{
            String type=reader.getFormatName();if(!type.equalsIgnoreCase("png")&&!type.equalsIgnoreCase("jpeg")&&!type.equalsIgnoreCase("jpg"))throw new IOException("Поддерживаются PNG и JPEG");reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>4096||h>4096||(long)w*h>16_000_000)throw new IOException("Слишком большое разрешение: до 4096 и 16 Мп");var original=reader.read(0);int ow=kind.equals("avatar")?256:640,oh=kind.equals("avatar")?256:200;var image=new BufferedImage(ow,oh,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);double scale=Math.max(ow/(double)w,oh/(double)h);int sw=(int)Math.ceil(w*scale),sh=(int)Math.ceil(h*scale);g.drawImage(original,(ow-sw)/2,(oh-sh)/2,sw,sh,null);}finally{g.dispose();}var output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);if(output.size()>MAX_STORED)throw new IOException("После обработки изображение слишком большое");return output.toByteArray();
        }finally{reader.dispose();}}
    }
}
