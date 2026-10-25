package com.wildfire.client.discord;
import java.io.*;import java.nio.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import com.google.gson.*;
public final class DiscordPresenceCheck {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static JsonObject json(DiscordIpc.Frame frame){return JsonParser.parseString(new String(frame.data(),StandardCharsets.UTF_8)).getAsJsonObject();}
 static void send(OutputStream out,int op,String data)throws IOException{out.write(DiscordIpc.frame(op,data.getBytes(StandardCharsets.UTF_8)));out.flush();}
 public static void main(String[] args)throws Exception{
  for(String address:new String[]{"springplus.net","SPRINGPLUS.NET","springplus.net:25565","mc.springplus.net.","mc.springplus.net.:25565"})check(PresenceActivity.springPlusAddress(address),"Public SpringPlus address not recognized: "+address);
  for(String address:new String[]{"springplus.net.evil.example","evil.springplus.net","localhost","127.0.0.1","https://springplus.net","springplus.net/path","springplus.net@evil.example","springplus.net:12345",""})check(!PresenceActivity.springPlusAddress(address),"Other server exposed as SpringPlus: "+address);
  check(!PresenceActivity.springPlusAddress(null),"Null server");
  var snapshot=new PresenceActivity.Snapshot(true,false,"PrivateNick","Admin","PrivateTown","Mayor","Незер",42,1234);
  var hidden=PresenceActivity.build(snapshot,new PresenceActivity.Visibility(false,false,false,false));
  check(!hidden.toString().contains("Private")&&!hidden.toString().contains("Mayor")&&!hidden.has("secrets"),"Private fields leaked");
  var shown=PresenceActivity.build(snapshot,new PresenceActivity.Visibility(true,true,true,true));
  check(shown.get("state").getAsString().contains("В городе PrivateTown"),"Current town wording");
  var capital=new PresenceActivity.Snapshot(true,false,"","","Тест","","",1,1234,-1,-1,"",true);
  check(PresenceActivity.build(capital,new PresenceActivity.Visibility(false,false,true,false)).get("state").getAsString().equals("В столице Тест"),"Capital wording");
  check(!PresenceActivity.build(capital,new PresenceActivity.Visibility(false,false,false,false)).toString().contains("Тест"),"Territory privacy");
  check(shown.get("name").getAsString().equals("SpringPlus")&&!shown.toString().contains("SpringPlus Mod"),"Incorrect application branding");
  check(shown.get("details").getAsString().equals("Играет на SpringPlus"),"Wrong server state");
  check(!shown.has("secrets")&&!shown.has("party"),"Site-only card must not publish join metadata");
  check(shown.has("buttons")&&!shown.has("secrets"),"Discord rejects URL buttons with join secrets (5005)");
  check(shown.get("details_url").getAsString().equals("https://forum.springplus.net")&&shown.getAsJsonObject("assets").get("large_url").getAsString().equals("https://forum.springplus.net"),"Join mode must keep clickable forum link");
  check(hidden.getAsJsonArray("buttons").get(0).getAsJsonObject().get("url").getAsString().equals("https://forum.springplus.net"),"Site button without join");
  var other=PresenceActivity.build(new PresenceActivity.Snapshot(false,false,"PrivateNick","Admin","PrivateTown","Mayor","SecretWorld",42,1234),new PresenceActivity.Visibility(true,true,true,true));
  check(!other.toString().contains("Private")&&!other.toString().contains("SecretWorld")&&!other.has("secrets"),"Other server data leaked");
  check(PresenceActivity.clean("§cA\nB",2).equals("AB"),"Format/control sanitization");
  check(PresenceActivity.clean("😀😀😀",2).equals("😀😀"),"Surrogate-safe truncation");
  String avatar="a".repeat(64);var city=new PresenceActivity.Snapshot(true,false,"PrivateNick","Admin","PrivateTown","Mayor","Незер",42,1234,42,100,avatar);var cityVisible=new PresenceActivity.Visibility(false,false,true,false);
  var cityAssets=PresenceActivity.build(city,cityVisible).getAsJsonObject("assets");
  check(cityAssets.get("large_image").getAsString().equals("https://skin.springplus.net/api/chat-images/"+avatar+".png"),"City avatar URL");
  check(cityAssets.get("large_text").getAsString().contains("PrivateTown")&&cityAssets.get("small_image").getAsString().equals("springplus"),"City tooltip and server badge");
  var privateActivity=PresenceActivity.build(city,new PresenceActivity.Visibility(false,false,false,false));check(!privateActivity.toString().contains(avatar)&&!privateActivity.toString().contains("PrivateTown")&&!privateActivity.toString().contains("Mayor"),"City artwork reveals hidden membership");
  for(String invalid:new String[]{"","https://evil.example/image.png","../secret","a".repeat(63),"F".repeat(64)}){check(PresenceActivity.cityImage(invalid).isEmpty(),"Untrusted city image accepted");var invalidCity=new PresenceActivity.Snapshot(true,false,"","","Town","","",1,0,-1,-1,invalid);check(PresenceActivity.build(invalidCity,cityVisible).getAsJsonObject("assets").get("large_image").getAsString().equals("springplus"),"Missing avatar fallback");}
  check(PresenceActivity.cityImage(null).isEmpty(),"Null avatar");
  var noTown=new PresenceActivity.Snapshot(true,false,"","","","Mayor","",1,0,-1,-1,avatar);check(!PresenceActivity.build(noTown,cityVisible).toString().contains(avatar),"Stale avatar without town");
  var otherTown=new PresenceActivity.Snapshot(false,false,"","","Town","Mayor","",1,0,-1,-1,avatar);check(!PresenceActivity.build(otherTown,cityVisible).toString().contains(avatar),"City leaked outside SpringPlus");
  check(!PresenceActivity.cityImage(avatar).equals(PresenceActivity.cityImage("b".repeat(64))),"Changed avatar must change immutable URL");
  byte[] bytes=DiscordIpc.frame(1,"abc".getBytes(StandardCharsets.UTF_8));check(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt()==1,"Opcode endian");check(DiscordIpc.readFrame(new ByteArrayInputStream(bytes)).data().length==3,"Framing");
  for(int length:new int[]{-1,65537})try{DiscordIpc.readFrame(new ByteArrayInputStream(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putInt(length).array()));throw new AssertionError("Bad size accepted");}catch(IOException expected){}
  try{DiscordIpc.readFrame(new ByteArrayInputStream(new byte[7]));throw new AssertionError("Short header accepted");}catch(EOFException expected){}
  var noZeroRead=new ByteArrayInputStream(DiscordIpc.frame(4,new byte[0])){@Override public int read(byte[] b,int offset,int length){check(length>0,"Zero-byte pipe read can block on Windows");return super.read(b,offset,length);}};
  check(DiscordIpc.readFrame(noZeroRead).data().length==0,"Empty IPC frame");
  AtomicInteger reads=new AtomicInteger();AtomicReference<byte[]> pending=new AtomicReference<>();var raw=new InputStream(){public int available(){byte[] bytes=pending.get();return bytes==null?0:bytes.length;}public int read(){throw new AssertionError("Single-byte raw read");}public int read(byte[] target,int offset,int length){reads.incrementAndGet();byte[] bytes=pending.getAndSet(null);check(bytes!=null&&length>0&&length<=bytes.length,"Blocking read issued with no available data");System.arraycopy(bytes,0,target,offset,length);return length;}};
  var pipeInput=DiscordIpc.windowsInput(raw);var received=new CompletableFuture<Integer>();Thread poll=Thread.ofPlatform().daemon().start(()->{try{received.complete(pipeInput.read(new byte[4],0,4));}catch(Throwable e){received.completeExceptionally(e);}});Thread.sleep(75);check(reads.get()==0,"Reader blocks shared pipe handle while idle");pending.set(new byte[]{1,2,3,4});check(received.get(2,TimeUnit.SECONDS)==4,"Polling read failed");check(pipeInput.read(new byte[0],0,0)==0&&reads.get()==1,"Zero-byte read reached OS");poll.join(1000);
  PipedInputStream serverInput=new PipedInputStream(65536),clientInput=new PipedInputStream(65536);PipedOutputStream clientOutput=new PipedOutputStream(serverInput),serverOutput=new PipedOutputStream(clientInput);
  CountDownLatch joined=new CountDownLatch(1),published=new CountDownLatch(1),cleared=new CountDownLatch(1);AtomicInteger invites=new AtomicInteger();AtomicReference<Throwable> error=new AtomicReference<>();
  Thread fake=Thread.ofPlatform().daemon().name("Fake-Discord-only").start(()->{try{
   var hello=DiscordIpc.readFrame(serverInput);check(hello.op()==0&&json(hello).get("client_id").getAsString().equals(PresenceSettings.APPLICATION_ID),"Handshake");
   send(serverOutput,1,"{\"evt\":\"READY\",\"data\":{}}");
   while(true){var frame=DiscordIpc.readFrame(serverInput);var message=json(frame);String cmd=message.get("cmd").getAsString();if(cmd.equals("SUBSCRIBE")){check(message.get("evt").getAsString().equals("ACTIVITY_JOIN"),"Join subscription");}
    if(cmd.equals("SET_ACTIVITY")){var activity=message.getAsJsonObject("args").get("activity");if(activity.isJsonNull()){cleared.countDown();break;}check(activity.getAsJsonObject().get("details").getAsString().contains("SpringPlus"),"Published state");check(!(activity.getAsJsonObject().has("buttons")&&activity.getAsJsonObject().has("secrets")),"Forbidden RPC combination");published.countDown();send(serverOutput,1,"{\"cmd\":\"SET_ACTIVITY\",\"evt\":null,\"data\":{}}");send(serverOutput,1,"{\"evt\":\"ACTIVITY_JOIN\",\"data\":{\"secret\":\"evil.example\"}}");send(serverOutput,1,"{\"evt\":\"ACTIVITY_JOIN\",\"data\":{\"secret\":\"springplus:join:v1\"}}");}
   }
  }catch(Throwable e){error.set(e);}});
  try(var client=new DiscordIpc(secret->{invites.incrementAndGet();joined.countDown();},()->new DiscordIpc.Endpoint(()->{clientOutput.close();clientInput.close();},DiscordIpc.windowsInput(clientInput),clientOutput))){client.activity(shown);check(published.await(3,TimeUnit.SECONDS),"Activity never published");check(joined.await(3,TimeUnit.SECONDS),"Join not delivered");check(invites.get()==1,"Untrusted destination accepted");}
  check(cleared.await(3,TimeUnit.SECONDS),"Activity not cleared on disable");fake.join(1000);if(error.get()!=null)throw new AssertionError(error.get());
  System.out.println("DiscordPresenceCheck PASS: Windows idle-read regression, no zero-byte reads, privacy, safe join token, bounded framing, fake IPC handshake/subscription/activity/join/clear");
 }
}
