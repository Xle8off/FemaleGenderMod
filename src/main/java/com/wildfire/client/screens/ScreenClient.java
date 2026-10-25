package com.wildfire.client.screens;
import net.springplus.screens.ScreenProtocol;
import java.io.IOException;
import java.util.List;
import java.util.function.*;

/** Layout cache only: a server packet must never start a browser, stream or P2P upload. */
public final class ScreenClient {
 private static Consumer<byte[]> send=bytes->{};
 private static BooleanSupplier supported=()->false;
 private static Object connection,level;
 private static long sequence,nonce,nextRequest;
 private static ScreenProtocol.Snapshot snapshot;
 private static boolean hadChannel;
 private static java.util.UUID awaiting;private static Consumer<java.util.UUID> onOpen;private static long openUntil;
 private static Consumer<ScreenProtocol.Snapshot> mediaOffer=value->{};
 private static java.util.UUID mediaGranted;
 private static volatile String activeMediaUrl="";
 public static boolean mediaGranted(java.util.UUID id){return id!=null&&id.equals(mediaGranted);}
 public static void configureMedia(Consumer<ScreenProtocol.Snapshot> consumer){mediaOffer=consumer;}
 public static String mediaUrl(){return activeMediaUrl;}
 public static String mediaUrl(java.util.UUID id){return mediaGranted(id)?activeMediaUrl:"";}
 static LongSupplier clock=System::currentTimeMillis;
 private ScreenClient(){}
 public static void configure(Consumer<byte[]> sender,BooleanSupplier available){send=sender;supported=available;}
 public static void reset(){connection=level=null;nonce=nextRequest=0;snapshot=null;hadChannel=false;awaiting=null;onOpen=null;mediaGranted=null;activeMediaUrl="";}
 public static void tick(Object currentConnection,Object currentLevel){
  if(currentConnection==null||currentLevel==null){reset();return;}
  if(connection!=currentConnection||level!=currentLevel){reset();connection=currentConnection;level=currentLevel;}
  boolean available=supported.getAsBoolean();
  if(!available){nonce=0;snapshot=null;hadChannel=false;mediaGranted=null;activeMediaUrl="";return;}
  if(!hadChannel){hadChannel=true;nextRequest=0;}
  long now=clock.getAsLong();if(awaiting!=null&&now<openUntil)return;if(awaiting!=null){awaiting=null;onOpen=null;}if(now<nextRequest)return;
  nextRequest=now+(snapshot==null?2000:5000);nonce=++sequence;
  try{send.accept(ScreenProtocol.request(nonce));}catch(IOException|RuntimeException ignored){snapshot=null;}
 }
 public static void accept(byte[] bytes){
  try{var value=ScreenProtocol.decode(bytes);if(nonce>0&&connection!=null&&level!=null&&value.nonce()==nonce){snapshot=value;if(value.opened()!=null){mediaGranted=value.mediaUrl().isEmpty()?null:value.opened();activeMediaUrl=value.mediaUrl();}if(value.mediaAction()!=0){mediaOffer.accept(value);return;}if(awaiting!=null&&value.opened()!=null){var id=awaiting;var callback=onOpen;awaiting=null;onOpen=null;if(clock.getAsLong()<=openUntil&&id.equals(value.opened())&&callback!=null)callback.accept(id);}}}
  catch(IOException ignored){}
 }
 public static List<ScreenProtocol.Screen> screens(Object currentLevel){return level==currentLevel&&snapshot!=null?snapshot.screens():List.of();}
 public static boolean requestOpen(java.util.UUID id,Consumer<java.util.UUID> callback){
  return requestOpen(id,callback,false);
 }
 public static boolean requestWatch(java.util.UUID id,Consumer<java.util.UUID> callback){return requestOpen(id,callback,true);}
 private static boolean requestOpen(java.util.UUID id,Consumer<java.util.UUID> callback,boolean watch){
  if(connection==null||level==null||!supported.getAsBoolean()||awaiting!=null&&clock.getAsLong()<openUntil)return false;
  if(screens(level).stream().noneMatch(s->s.id().equals(id)&&(watch?s.mediaActive():s.canUse())))return false;
  awaiting=id;onOpen=callback;openUntil=clock.getAsLong()+5000;nonce=++sequence;nextRequest=0;
  try{send.accept(watch?ScreenProtocol.watchRequest(nonce,id):ScreenProtocol.request(nonce,id));return true;}catch(IOException|RuntimeException error){awaiting=null;onOpen=null;return false;}
 }
}
