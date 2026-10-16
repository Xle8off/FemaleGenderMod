import java.nio.file.*;
import java.util.*;
public final class SharedLoaderParityCheck {
    public static void main(String[] args)throws Exception {
        Path fabric=Path.of(args[0]).toAbsolutePath().normalize(),neo=fabric.resolveSibling("FemaleGenderMod-NeoForge");
        var manifest=Files.readAllLines(neo.resolve("shared-ui-files.txt"));
        var failures=new ArrayList<String>();
        for(String path:manifest){
            if(path.isBlank())continue;
            String a=Files.readString(fabric.resolve("src/main/java").resolve(path)).replace("\r","");
            String b=Files.readString(neo.resolve("common/src/main/java").resolve(path)).replace("\r","");
            if(!a.stripTrailing().equals(b.stripTrailing()))failures.add(path);
        }
        if(!failures.isEmpty())throw new AssertionError("Shared loader sources diverged: "+failures);
        System.out.println("Fabric/NeoForge: "+manifest.size()+" shared GUI, music, receipts and camera sources are identical");
    }
}
