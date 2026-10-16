package com.wildfire.gui;

import java.nio.file.*;
import java.util.*;

/** Bounded two-file entries, owned by the avatar background worker. */
public final class AvatarDiskCache {
    public record Cached(String hash,byte[] png){}
    private final Path root;
    public AvatarDiskCache(Path root){this.root=root;}
    private Path file(String name,String suffix){if(!AvatarBatchProtocol.name(name))throw new IllegalArgumentException("Avatar name");return root.resolve(name.toLowerCase(Locale.ROOT)+suffix);}
    public Cached load(String name){
        try{
            Path meta=file(name,".sha256"),image=file(name,".png");
            if(Files.size(meta)!=64||Files.size(image)>32768)return null;
            String hash=Files.readString(meta);if(!AvatarBatchProtocol.hash(hash))return null;
            byte[] png=Files.readAllBytes(image);AvatarBatchProtocol.validateImage(png,hash);return new Cached(hash,png);
        }catch(Exception e){return null;}
    }
    public void save(String name,String hash,byte[] png)throws Exception{
        AvatarBatchProtocol.validateImage(png,hash);Files.createDirectories(root);
        write(file(name,".png"),png);write(file(name,".sha256"),hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    public void remove(String name){try{Files.deleteIfExists(file(name,".sha256"));Files.deleteIfExists(file(name,".png"));}catch(Exception ignored){}}
    private static void write(Path path,byte[] bytes)throws Exception{
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");Files.write(temporary,bytes);
        try{Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
    }
    public void prune(int max){
        try(var stream=Files.list(root)){
            var files=stream.filter(p->p.getFileName().toString().matches("[a-z0-9_]{1,16}\\.sha256")).sorted(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p).toMillis();}catch(Exception e){return 0L;}})).toList();
            for(int i=0;i<files.size()-max;i++){Path meta=files.get(i);String name=meta.getFileName().toString().replace(".sha256","");Files.deleteIfExists(file(name,".png"));Files.deleteIfExists(meta);}
        }catch(Exception ignored){}
    }
}
