import com.wildfire.gui.VerifiedUpdateDownload;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class VerifiedUpdateDownloadCheck {
    private static InputStream zeros(long size){return new InputStream(){long remaining=size;
        public int read(){if(remaining==0)return -1;remaining--;return 0;}
        public int read(byte[] bytes,int offset,int length){if(remaining==0)return -1;int n=(int)Math.min(length,remaining);java.util.Arrays.fill(bytes,offset,offset+n,(byte)0);remaining-=n;return n;}
    };}
    private static String hash(long size)throws Exception{var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[32768];try(var in=zeros(size)){for(int n;(n=in.read(buffer))!=-1;)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}
    private static void rejected(Path file,long bytes,long expected,String hash)throws Exception{
        try(var in=zeros(bytes)){try{VerifiedUpdateDownload.write(in,file,expected,hash,p->{});throw new AssertionError("Invalid update accepted");}catch(IOException expectedFailure){}}
    }
    public static void main(String[] args)throws Exception{
        Path file=Files.createTempFile("springplus-stream-check-",".part");
        try{
            long size=129L*1024*1024;int[] progress={-1};
            try(var in=zeros(size)){VerifiedUpdateDownload.write(in,file,size,hash(size),p->{if(p<progress[0]||p>100)throw new AssertionError("Invalid progress");progress[0]=p;});}
            if(Files.size(file)!=size||progress[0]!=100)throw new AssertionError("Large download truncated");
            try(var in=zeros(4096)){VerifiedUpdateDownload.write(in,file,-1,hash(4096),p->{});}
            rejected(file,4096,8192,hash(4096));rejected(file,4096,4096,"0".repeat(64));rejected(file,128,128,hash(128));
            System.out.println("PASS: 129 MiB update with bounded memory, unknown length, progress, truncated body, SHA-256 and invalid response checks");
        }finally{Files.deleteIfExists(file);}
    }
}
