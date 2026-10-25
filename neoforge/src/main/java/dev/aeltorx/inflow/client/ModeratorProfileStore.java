package dev.aeltorx.inflow.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;

/** Local avatars only. Decode bounded images off the game thread; persist a normalized square PNG. */
public final class ModeratorProfileStore {
    public static final long MAX_BYTES=8*1024*1024;
    private final Path directory;
    public ModeratorProfileStore(Path directory){this.directory=directory;}
    private Path path(UUID id){return directory.resolve(id+".png");}
    public BufferedImage load(UUID id)throws IOException{return Files.exists(path(id))?decode(path(id)):null;}
    public void remove(UUID id)throws IOException{Files.deleteIfExists(path(id));}
    private static BufferedImage decode(Path source)throws IOException {
        if(!Files.isRegularFile(source)||Files.size(source)<1||Files.size(source)>MAX_BYTES)throw new IOException("Изображение должно быть не больше 8 МиБ");
        try(var input=ImageIO.createImageInputStream(source.toFile())){if(input==null)throw new IOException("Не удалось открыть изображение");var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Выбери PNG или JPEG");var reader=readers.next();try{
            String format=reader.getFormatName();if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("jpeg")&&!format.equalsIgnoreCase("jpg"))throw new IOException("Поддерживаются PNG и JPEG");
            reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>4096||h>4096||(long)w*h>16_000_000)throw new IOException("Максимальное разрешение — 4096×4096 и 16 Мп");
            var image=reader.read(0);if(image==null)throw new IOException("Повреждённое изображение");return normalize(image);
        }finally{reader.dispose();}}
    }
    public static BufferedImage normalize(BufferedImage input){var result=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);var g=result.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);int side=Math.min(input.getWidth(),input.getHeight()),x=(input.getWidth()-side)/2,y=(input.getHeight()-side)/2;g.drawImage(input,0,0,256,256,x,y,x+side,y+side,null);}finally{g.dispose();}return result;}
    public BufferedImage importImage(UUID id,Path source)throws IOException{var image=decode(source);Files.createDirectories(directory);var temp=Files.createTempFile(directory,"avatar-",".tmp");try{if(!ImageIO.write(image,"png",temp.toFile()))throw new IOException("PNG encoder unavailable");try{Files.move(temp,path(id),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path(id),StandardCopyOption.REPLACE_EXISTING);}return image;}finally{Files.deleteIfExists(temp);}}
}
