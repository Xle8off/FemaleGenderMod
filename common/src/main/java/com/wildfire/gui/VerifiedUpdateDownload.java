package com.wildfire.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.IntConsumer;

/** Streams trusted update bodies to disk with constant memory, without an arbitrary size cap. */
public final class VerifiedUpdateDownload {
    private VerifiedUpdateDownload(){}
    public static void write(InputStream input,Path target,long expected,String sha,IntConsumer progress) throws IOException {
        MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IOException(e);}
        long total=0;int previous=-1;
        try(var output=Files.newOutputStream(target)){
            byte[] buffer=new byte[32*1024];int count;
            while((count=input.read(buffer))!=-1){
                if(count==0)continue;
                output.write(buffer,0,count);digest.update(buffer,0,count);total+=count;
                if(expected>0){int percent=(int)Math.min(100,total*100.0/expected);if(percent!=previous){progress.accept(percent);previous=percent;}}
            }
        }
        if(total<1024)throw new IOException("Неверный ответ сервера");
        if(expected>=0&&total!=expected)throw new IOException("Файл загружен не полностью");
        if(!HexFormat.of().formatHex(digest.digest()).equals(sha))throw new IOException("Контрольная сумма не совпала");
    }
}
