import com.wildfire.gui.AvatarBatchProtocol;
import com.wildfire.gui.AvatarDiskCache;
import com.google.gson.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public final class AvatarBatchCheck {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    private static void bad(Runnable action){try{action.run();throw new AssertionError("Accepted bad avatar input");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args)throws Exception{
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB),"png",out);
        byte[] png=out.toByteArray();String hash=AvatarBatchProtocol.sha(png);
        var request=Map.of("xle8",hash,"other","");
        var encoded=AvatarBatchProtocol.request(request);check(JsonParser.parseString(encoded).getAsJsonObject().getAsJsonArray("players").size()==2);
        String json="{\"version\":1,\"avatars\":[{\"name\":\"xle8\",\"status\":\"ok\",\"hash\":\""+hash+"\",\"unchanged\":true},{\"name\":\"other\",\"status\":\"ok\",\"hash\":\""+hash+"\",\"png\":\""+Base64.getEncoder().encodeToString(png)+"\"}]}";
        var faces=AvatarBatchProtocol.parse(json,request);check(faces.getFirst().png()==null&&faces.getFirst().unchanged()&&Arrays.equals(faces.get(1).png(),png));
        bad(()->AvatarBatchProtocol.parse(json,Map.of("xle8","","other","")));
        bad(()->AvatarBatchProtocol.request(Map.of("../file","")));
        bad(()->AvatarBatchProtocol.request(Map.of("Xle8","","xle8","")));
        bad(()->AvatarBatchProtocol.validateImage(png,"0".repeat(64)));
        byte[] wrong=png.clone();wrong[19]=32;bad(()->AvatarBatchProtocol.validateImage(wrong,AvatarBatchProtocol.sha(wrong)));
        var many=new LinkedHashMap<String,String>();for(int i=0;i<33;i++)many.put("p"+i,"");bad(()->AvatarBatchProtocol.request(many));
        String partial="{\"version\":1,\"avatars\":[{\"name\":\"other\",\"status\":\"retry\"}]}";check(AvatarBatchProtocol.parse(partial,request).size()==1);
        var subscriber=new AvatarBatchProtocol.LimitedBody(4);var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        subscriber.onSubscribe(new java.util.concurrent.Flow.Subscription(){public void request(long n){}public void cancel(){cancelled.set(true);}});
        subscriber.onNext(List.of(java.nio.ByteBuffer.wrap(new byte[5])));subscriber.onComplete();
        check(cancelled.get()&&subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
        Path directory=Files.createTempDirectory("springplus-avatar-check-");
        try{
            var disk=new AvatarDiskCache(directory);disk.save("Xle8",hash,png);check(disk.load("xle8").hash().equals(hash));
            disk.save("other",hash,png);disk.prune(1);try(var files=Files.list(directory)){check(files.filter(p->p.toString().endsWith(".sha256")).count()==1);}
            disk.remove("other");check(disk.load("other")==null&&disk.load("../file")==null);
        }finally{try(var paths=Files.walk(directory)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        System.out.println("Avatar batch checks passed: bounds, unchanged hashes, PNG dimensions/hash, isolated retry, persistent cache, pruning");
    }
}
