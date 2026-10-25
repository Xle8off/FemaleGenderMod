package com.wildfire.client.screens;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
import net.springplus.screens.ScreenGeometry;
public final class BrowserCheck{
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void rejects(String url){try{BrowserEngine.webUri(url);throw new AssertionError("Unsafe URL accepted");}catch(IllegalArgumentException expected){}}
 static Path archive(Path dir,String name)throws Exception{Path zip=dir.resolve(UUID.randomUUID()+".zip");try(var out=new ZipOutputStream(Files.newOutputStream(zip))){out.putNextEntry(new ZipEntry(name));out.write(new byte[]{1,2,3});out.closeEntry();}return zip;}
 public static void main(String[] args)throws Exception{
  BrowserEngine.webUri("https://music.youtube.com/");BrowserEngine.webUri("https://music.yandex.ru:443/");
  for(String url:List.of("http://example.com","javascript:alert(1)","file:///C:/Windows","data:text/html,test","https://user:pass@example.com","https://example.com:8080","https:///empty"))rejects(url);
  Path temp=Files.createTempDirectory("springplus-browser-check-");
  try{
   rejects("http://127.0.0.1:8789/player#abc.def");
   Path local=temp.resolve("player-url.txt");Files.writeString(local,"http://127.0.0.1:8789/player#abc.def");System.setProperty("springplus.cinema.localTest",local.toString());
   check(!BrowserEngine.localTestUrl().isEmpty(),"Explicit local test handoff");BrowserEngine.webUri(BrowserEngine.localTestUrl());rejects("http://192.168.1.1/player#abc.def");rejects("http://localhost/player#abc.def");rejects("http://127.0.0.1/admin");
   Files.writeString(local,"http://192.168.1.1/player#abc.def");check(BrowserEngine.localTestUrl().isEmpty(),"No LAN override");System.clearProperty("springplus.cinema.localTest");
   BrowserEngine.extract(archive(temp,"safe/file.txt"),temp.resolve("valid"),()->false);check(Files.readAllBytes(temp.resolve("valid/safe/file.txt")).length==3,"Archive extraction");
   for(String name:List.of("../escape","/absolute","C:/escape","..\\escape"))try{BrowserEngine.extract(archive(temp,name),temp.resolve(UUID.randomUUID().toString()),()->false);throw new AssertionError("Zip traversal accepted");}catch(IOException expected){}
   try{BrowserEngine.extract(archive(temp,"cancelled"),temp.resolve("cancel"),()->true);throw new AssertionError("Cancellation ignored");}catch(InterruptedIOException expected){}
  }finally{try(var files=Files.walk(temp)){for(Path f:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(f);}}
  for(var face:ScreenGeometry.Face.values()){
   var g=new ScreenGeometry(100,70,-100,48,24,face);var a=ScreenSurface.point(g,0,0);var b=ScreenSurface.point(g,0,24);var c=ScreenSurface.point(g,48,24);
   double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z(),vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();
   double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
   var center=ScreenSurface.point(g,24,12);
   check(ScreenSurface.front(g,center.x()+nx,center.y()+ny,center.z()+nz),"Correct winding for "+face);
   check(!ScreenSurface.front(g,center.x()-nx,center.y()-ny,center.z()-nz),"Back face hidden for "+face);
   check(Math.abs(Math.sqrt((a.x()-b.x())*(a.x()-b.x())+(a.y()-b.y())*(a.y()-b.y())+(a.z()-b.z())*(a.z()-b.z()))-24)<.0001,"Full block height");
  }
  check(BrowserEngine.SHA256.matches("[a-f0-9]{64}")&&BrowserEngine.ARCHIVE_BYTES==158299258,"Pinned runtime metadata");
  System.out.println("BrowserCheck PASS: HTTPS policy, safe extraction, traversal rejection, cancellation, six face winding/size, pinned runtime");
 }
}
