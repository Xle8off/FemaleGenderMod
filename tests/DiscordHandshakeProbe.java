package com.wildfire.client.discord;
import java.util.concurrent.*;import com.google.gson.*;
/** Read-only diagnostic: handshake/subscription/heartbeat, no SET_ACTIVITY or user data output. */
public final class DiscordHandshakeProbe {
 public static void main(String[] args)throws Exception{
  var method=DiscordIpc.class.getDeclaredMethod("connect");method.setAccessible(true);
  if(args.length>0&&args[0].equals("raw"))try(var endpoint=(DiscordIpc.Endpoint)method.invoke(null)){
   System.out.println("Local Discord pipe opened");var hello=new JsonObject();hello.addProperty("v",1);hello.addProperty("client_id",PresenceSettings.APPLICATION_ID);endpoint.write(0,hello);System.out.println("Handshake sent");
   var future=new CompletableFuture<DiscordIpc.Frame>();Thread.ofPlatform().daemon().start(()->{try{future.complete(endpoint.read());}catch(Throwable error){future.completeExceptionally(error);}});
   try{var frame=future.get(5,TimeUnit.SECONDS);var object=JsonParser.parseString(new String(frame.data(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();System.out.println("Reply opcode="+frame.op()+" event="+(object.has("evt")?object.get("evt"):"none"));if(object.has("code"))System.out.println("Error code="+object.get("code"));}catch(TimeoutException e){System.out.println("Handshake reply timed out after 5s");}
  }
  try(var client=new DiscordIpc(secret->{})){boolean connected=false;for(int i=0;i<25;i++){Thread.sleep(1000);boolean ready=client.status().equals("Подключено к Discord");if(connected&&!ready)throw new AssertionError("Runtime connection lost: "+client.status());connected|=ready;if(i==20&&!connected)throw new AssertionError("Runtime handshake/subscription stalled: "+client.status());}if(!connected||client.pongs()==0)throw new AssertionError("Discord not ready or heartbeat unanswered");System.out.println("LIVE DISCORD PASS: READY, subscription and heartbeat PONG verified; no activity published");}
 }
}
