package com.wildfire.client.screens;
import java.io.*;import java.net.*;import java.net.http.*;import java.nio.file.*;import java.security.*;import java.time.Duration;import java.util.*;import java.util.function.*;import java.util.zip.*;

/** Explicitly installed, pinned Windows runtime. Never searches the system's browsers. */
public final class BrowserEngine {
 public static final String VERSION="44.5.1+wvcus";
 public static final String SHA256="c7d51e4bf80de9ef18b3306b0d140059bb203510d3dfc88f3404049a12c90f89";
 public static final long ARCHIVE_BYTES=158299258;
 private static final URI DOWNLOAD=URI.create("https://github.com/castlabs/electron-releases/releases/download/v44.5.1%2Bwvcus/electron-v44.5.1%2Bwvcus-win32-x64.zip");
 private BrowserEngine(){}
 public static boolean supported(){String arch=System.getProperty("os.arch","").toLowerCase(Locale.ROOT);return System.getProperty("os.name","").startsWith("Windows")&&(arch.equals("amd64")||arch.equals("x86_64"));}
 public static Path directory(Path game){return game.toAbsolutePath().normalize().resolve("config/springplus/browser-engine/castlabs-44.5.1-win64");}
 public static boolean installed(Path game){Path root=directory(game);try{return Files.isRegularFile(root.resolve("electron.exe"))&&Files.readString(root.resolve(".springplus-verified")).equals(SHA256);}catch(IOException e){return false;}}
 public static Path install(Path game,Consumer<String> progress,BooleanSupplier cancelled)throws Exception{
  if(!supported())throw new IOException("Windows x64 required");
  Path root=directory(game);if(installed(game))return root.resolve("electron.exe");
  if(Files.exists(root))throw new IOException("Incomplete engine installation; preserve/remove it manually before retry");
  Files.createDirectories(root.getParent());Path temp=Files.createTempDirectory(root.getParent(),"installer-");Path zip=temp.resolve("engine.zip"),unpacked=temp.resolve("unpacked");
  try{
   progress.accept("Скачиваем движок: 0 / 151 МБ");
   var client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
   var response=client.send(HttpRequest.newBuilder(DOWNLOAD).timeout(Duration.ofMinutes(15)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
   try(var in=response.body()){
    if(response.statusCode()!=200)throw new IOException("Engine HTTP "+response.statusCode());
    var digest=MessageDigest.getInstance("SHA-256");long total=0,shown=-1;
    try(var out=Files.newOutputStream(zip)){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))>=0;){if(cancelled.getAsBoolean())throw new InterruptedIOException("Cancelled");total+=n;if(total>ARCHIVE_BYTES)throw new IOException("Engine archive size");out.write(buffer,0,n);digest.update(buffer,0,n);long mb=total/1048576;if(mb!=shown){shown=mb;progress.accept("Скачиваем движок: "+mb+" / 151 МБ");}}}
    if(total!=ARCHIVE_BYTES||!HexFormat.of().formatHex(digest.digest()).equals(SHA256))throw new IOException("Engine checksum mismatch");
   }
   progress.accept("Распаковываем проверенный движок…");extract(zip,unpacked,cancelled);
   if(!Files.isRegularFile(unpacked.resolve("electron.exe")))throw new IOException("Engine executable missing");
   Files.writeString(unpacked.resolve(".springplus-verified"),SHA256);
   Files.move(unpacked,root);return root.resolve("electron.exe");
  }finally{
   // Only our fresh installer directory, never a profile or a general cache root.
   if(temp.getParent().equals(root.getParent())&&temp.getFileName().toString().startsWith("installer-"))try(var paths=Files.walk(temp)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
  }
 }
 static void extract(Path zip,Path root,BooleanSupplier cancelled)throws IOException{
  Files.createDirectories(root);Path base=root.toAbsolutePath().normalize();long total=0;int count=0;var seen=new HashSet<String>();
  try(var in=new ZipInputStream(Files.newInputStream(zip))){
   for(ZipEntry entry;(entry=in.getNextEntry())!=null;){
    if(cancelled.getAsBoolean())throw new InterruptedIOException("Cancelled");
    String name=entry.getName().replace('\\','/');if(name.contains(":")||name.startsWith("/")||++count>10000)throw new IOException("Unsafe archive entry");
    Path target=base.resolve(name).normalize();if(!target.startsWith(base)||target.equals(base)||!seen.add(target.toString().toLowerCase(Locale.ROOT)))throw new IOException("Archive traversal/duplicate");
    if(entry.isDirectory()){Files.createDirectories(target);continue;}
    Files.createDirectories(target.getParent());try(var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))>=0;){if(cancelled.getAsBoolean())throw new InterruptedIOException("Cancelled");total+=n;if(total>2_000_000_000L)throw new IOException("Archive expansion limit");out.write(buffer,0,n);}}
   }
  }
 }
 /** Explicit developer opt-in. The file contains only a short-lived local player ticket, never the worker secret. */
 public static String localTestUrl(){String file=System.getProperty("springplus.cinema.localTest","");if(file.isBlank())return "";try{Path p=Path.of(file);if(!p.isAbsolute()||Files.size(p)>4096)return "";String value=Files.readString(p).trim();URI u=URI.create(value);if(!"http".equals(u.getScheme())||!"127.0.0.1".equals(u.getHost())||u.getUserInfo()!=null||!"/player".equals(u.getPath())||u.getQuery()!=null||u.getFragment()==null||!u.getFragment().matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))return "";return value;}catch(Exception ignored){return "";}}
 public static URI webUri(String value){URI u=URI.create(value);boolean local=!localTestUrl().isEmpty()&&"http".equals(u.getScheme())&&"127.0.0.1".equals(u.getHost())&&"/player".equals(u.getPath());if((!local&&(!"https".equalsIgnoreCase(u.getScheme())||(u.getPort()!=-1&&u.getPort()!=443)))||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException("Нужна HTTPS-ссылка без логина/пароля в адресе");return u;}
}
