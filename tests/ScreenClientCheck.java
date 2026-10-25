package com.wildfire.client.screens;
import net.springplus.screens.*;import java.util.*;import java.util.concurrent.atomic.*;
public final class ScreenClientCheck{
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  var now=new AtomicLong(10000);var bytes=new AtomicReference<byte[]>();var available=new AtomicBoolean(true);var sent=new AtomicInteger();
  ScreenClient.clock=now::get;ScreenClient.configure(b->{bytes.set(b);sent.incrementAndGet();},available::get);
  Object connection=new Object(),world=new Object();UUID id=UUID.randomUUID(),worldId=UUID.randomUUID();
  ScreenClient.tick(connection,world);long nonce=ScreenProtocol.readRequest(bytes.get());
  var screen=new ScreenProtocol.Screen(id,new ScreenGeometry(0,64,0,48,24,ScreenGeometry.Face.SOUTH),true);
  byte[] snapshot=ScreenProtocol.encode(new ScreenProtocol.Snapshot(nonce,worldId,List.of(screen)));
  ScreenClient.accept(snapshot);check(ScreenClient.screens(world).size()==1,"Snapshot accepted");
  ScreenClient.tick(connection,world);check(sent.get()==1,"No per-tick request spam");
  var opened=new AtomicInteger();check(ScreenClient.requestOpen(id,approved->opened.incrementAndGet()),"Request permitted screen");
  var request=ScreenProtocol.readRequestData(bytes.get());check(request.open().equals(id),"Open ID transmitted");
  ScreenClient.accept(ScreenProtocol.encode(new ScreenProtocol.Snapshot(request.nonce(),worldId,List.of(screen))));check(opened.get()==0,"Broadcast is not an open grant");
  ScreenClient.accept(ScreenProtocol.encode(new ScreenProtocol.Snapshot(request.nonce(),worldId,List.of(screen),id)));check(opened.get()==1,"Fresh server grant opens once");
  ScreenClient.accept(ScreenProtocol.encode(new ScreenProtocol.Snapshot(request.nonce(),worldId,List.of(screen),id)));check(opened.get()==1,"Grant replay ignored");
  check(!ScreenClient.requestOpen(UUID.randomUUID(),approved->opened.incrementAndGet()),"Unknown screen denied");
  var viewing=new ScreenProtocol.Screen(id,screen.geometry(),false,true);ScreenClient.accept(ScreenProtocol.encode(new ScreenProtocol.Snapshot(request.nonce(),worldId,List.of(viewing))));
  check(!ScreenClient.requestOpen(id,approved->opened.incrementAndGet()),"Read-only viewer cannot request control");
  check(ScreenClient.requestWatch(id,approved->opened.incrementAndGet()),"Active screen accepts automatic viewer request");
  var viewRequest=ScreenProtocol.readRequestData(bytes.get());check(viewRequest.watch(),"Viewer operation transmitted");
  ScreenClient.accept(ScreenProtocol.encode(new ScreenProtocol.Snapshot(viewRequest.nonce(),worldId,List.of(viewing),id,"https://cinema.example/player#fixture",0)));
  check(opened.get()==2&&ScreenClient.mediaGranted(id),"Viewer grant accepted without control permission");
  check(ScreenClient.mediaUrl(id).contains("/player#")&&ScreenClient.mediaUrl(UUID.randomUUID()).isEmpty(),"Media URL scoped to granted screen");
  ScreenClient.accept(ScreenProtocol.encode(new ScreenProtocol.Snapshot(viewRequest.nonce(),worldId,List.of(screen),id)));
  check(!ScreenClient.mediaGranted(id)&&ScreenClient.mediaUrl(id).isEmpty(),"Legacy empty grant cannot reuse old media ticket");
  Object otherWorld=new Object();ScreenClient.tick(connection,otherWorld);check(ScreenClient.screens(otherWorld).isEmpty(),"World reset");
  ScreenClient.accept(snapshot);check(ScreenClient.screens(otherWorld).isEmpty(),"Late response ignored");
  nonce=ScreenProtocol.readRequest(bytes.get());ScreenClient.accept(ScreenProtocol.encode(new ScreenProtocol.Snapshot(nonce,UUID.randomUUID(),List.of())));
  check(ScreenClient.screens(otherWorld).isEmpty(),"Empty world snapshot");
  available.set(false);ScreenClient.tick(connection,otherWorld);check(ScreenClient.screens(otherWorld).isEmpty(),"Unsupported channel");
  ScreenClient.tick(null,null);ScreenClient.accept(snapshot);check(ScreenClient.screens(world).isEmpty(),"Disconnect ignores response");
  System.out.println("ScreenClientCheck PASS: request throttling, world switch, stale nonce, unsupported server, empty snapshot, disconnect");
 }
}
