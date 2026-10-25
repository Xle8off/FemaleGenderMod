package net.springplus.screens;
import java.io.*;import java.util.*;
public final class ScreenProtocolTest {
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
 static void reject(Runnable r){try{r.run();throw new AssertionError("Expected geometry rejection");}catch(IllegalArgumentException expected){}}
 static void bad(byte[] bytes){try{ScreenProtocol.decode(bytes);throw new AssertionError("Invalid packet accepted");}catch(IOException expected){}}
 public static void main(String[] args)throws Exception{
  for(var face:ScreenGeometry.Face.values()){
   boolean side=face==ScreenGeometry.Face.WEST||face==ScreenGeometry.Face.EAST,horizontal=face==ScreenGeometry.Face.UP||face==ScreenGeometry.Face.DOWN;
   int bx=side?0:47,by=horizontal?0:23,bz=side?47:horizontal?23:0;
   var g=ScreenGeometry.select(0,0,0,face,bx,by,bz,face);
   check(g.width()==48&&g.height()==24,"Inclusive maximum dimensions");
   check(g.equals(ScreenGeometry.select(bx,by,bz,face,0,0,0,face)),"Corner order");
   check(g.overlaps(g),"Self overlap");
  }
  reject(()->ScreenGeometry.select(0,0,0,ScreenGeometry.Face.NORTH,48,23,0,ScreenGeometry.Face.NORTH));
  reject(()->ScreenGeometry.select(0,0,0,ScreenGeometry.Face.NORTH,47,24,0,ScreenGeometry.Face.NORTH));
  reject(()->ScreenGeometry.select(0,0,0,ScreenGeometry.Face.NORTH,2,2,1,ScreenGeometry.Face.NORTH));
  reject(()->ScreenGeometry.select(0,0,0,ScreenGeometry.Face.NORTH,2,2,0,ScreenGeometry.Face.SOUTH));
  reject(()->new ScreenGeometry(Integer.MAX_VALUE,0,0,48,24,ScreenGeometry.Face.NORTH));
  var g=new ScreenGeometry(0,0,0,48,24,ScreenGeometry.Face.NORTH);
  check(!g.overlaps(new ScreenGeometry(48,0,0,1,24,ScreenGeometry.Face.NORTH)),"Adjacent screens do not overlap");
  check(g.contains(47,23,0,ScreenGeometry.Face.NORTH)&&!g.contains(47,23,0,ScreenGeometry.Face.SOUTH),"Click face and bounds");
  check(g.distanceSquared(-128,0,0)==128*128&&g.distanceSquared(-129,0,0)>128*128,"Eight chunk range from surface");
  var screen=new ScreenProtocol.Screen(UUID.randomUUID(),g);var data=new ScreenProtocol.Snapshot(12,UUID.randomUUID(),List.of(screen));
  byte[] encoded=ScreenProtocol.encode(data);check(data.equals(ScreenProtocol.decode(encoded)),"Metadata round trip");
  check(ScreenProtocol.readRequest(ScreenProtocol.request(12))==12,"Request round trip");
  var usable=new ScreenProtocol.Screen(screen.id(),g,true);var grant=new ScreenProtocol.Snapshot(14,data.world(),List.of(usable),screen.id());
  check(ScreenProtocol.decode(ScreenProtocol.encode(grant)).equals(grant),"Permission and grant round trip");
  var open=ScreenProtocol.readRequestData(ScreenProtocol.request(14,screen.id()));check(open.nonce()==14&&open.open().equals(screen.id()),"Open request round trip");
  var watch=ScreenProtocol.readRequestData(ScreenProtocol.watchRequest(15,screen.id()));check(watch.watch()&&watch.open().equals(screen.id()),"Automatic viewer request is not controller request");
  var viewing=new ScreenProtocol.Screen(screen.id(),g,false,true);var viewerGrant=new ScreenProtocol.Snapshot(15,data.world(),List.of(viewing),screen.id(),"https://cinema.example/player#fixture",0);
  check(ScreenProtocol.decode(ScreenProtocol.encode(viewerGrant)).equals(viewerGrant),"Read-only ticket, activity and metadata round trip");
  bad(ScreenProtocol.encode(new ScreenProtocol.Snapshot(14,data.world(),List.of(screen),screen.id())));
  for(int i=0;i<encoded.length;i++)bad(Arrays.copyOf(encoded,i));
  bad(Arrays.copyOf(encoded,encoded.length+1));bad(new byte[ScreenProtocol.MAX_BYTES+1]);
  byte[] invalid=encoded.clone();invalid[invalid.length-1]=6;bad(invalid);
  invalid=encoded.clone();invalid[invalid.length-3]=49;bad(invalid);
  try{ScreenProtocol.encode(new ScreenProtocol.Snapshot(1,data.world(),List.of(screen,screen)));throw new AssertionError("Duplicate ID");}catch(IOException expected){}
  var max=new ArrayList<ScreenProtocol.Screen>();for(int i=0;i<128;i++)max.add(new ScreenProtocol.Screen(UUID.randomUUID(),g));
  check(ScreenProtocol.encode(new ScreenProtocol.Snapshot(1,data.world(),max)).length<=ScreenProtocol.MAX_BYTES,"Bounded maximum packet");
  System.out.println("ScreenProtocolTest PASS: six faces, bounds, inclusive size, order, overlap, roundtrip, malformed/truncated packets, duplicate IDs");
 }
}
