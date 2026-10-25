package com.wildfire.gui;
import com.google.gson.JsonObject;
import java.util.concurrent.*;
/** Single in-flight short-lived grant. No persistent credentials or shared service tokens. */
public final class PlayerAssetUpload {
 private static CompletableFuture<String> pending;
 private static String kind="";
 private static long lastRequested;
 private PlayerAssetUpload(){}
 public static synchronized void cancel(){if(pending!=null&&!pending.isDone())pending.cancel(false);}
 public static synchronized CompletableFuture<String> request(String asset,java.util.function.Consumer<String> sender){
  if(pending!=null&&!pending.isDone())return CompletableFuture.failedFuture(new IllegalStateException("Загрузка уже выполняется"));
  kind=asset;pending=new CompletableFuture<>();var result=pending;
  long now=System.currentTimeMillis(),delay=Math.max(0,3100-(now-lastRequested));lastRequested=now+delay;
  Runnable send=()->{synchronized(PlayerAssetUpload.class){if(pending!=result||result.isDone())return;
   try{sender.accept(asset);}catch(Exception error){result.completeExceptionally(error);}}};
  if(delay==0)send.run();
  else CompletableFuture.delayedExecutor(delay,TimeUnit.MILLISECONDS).execute(()->
   net.minecraft.client.Minecraft.getInstance().execute(send));
  return result.orTimeout(19,TimeUnit.SECONDS);
 }
 public static synchronized void ticket(JsonObject object){
  if(pending==null||pending.isDone()||!object.has("kind")||!kind.equals(object.get("kind").getAsString()))return;
  if(object.has("token")&&object.get("token").getAsString().matches("[a-f0-9]{64}"))pending.complete(object.get("token").getAsString());
  else pending.completeExceptionally(new IllegalStateException("Обновите SkinSystem и сайт: загрузка недоступна"));
 }
}
