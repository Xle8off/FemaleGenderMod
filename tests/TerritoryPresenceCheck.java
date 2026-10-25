package com.wildfire.client.towns;
import java.io.*;
import java.util.concurrent.atomic.*;
public final class TerritoryPresenceCheck {
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 static byte[] reply(String token,String name,boolean capital,String avatar)throws Exception{
  var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
  out.writeByte(127);out.writeUTF(token);out.writeBoolean(name!=null);
  if(name!=null){out.writeUTF(name);out.writeBoolean(capital);out.writeUTF(avatar);}
  return bytes.toByteArray();
 }
 public static void main(String[] args)throws Exception{
  var time=new AtomicLong(10000);TerritoryPresence.clock=time::get;
  var token=new AtomicReference<String>();var world=new Object();
  check(TerritoryPresence.at(world,-1,2,token::set)==null,"No fabricated membership");
  String old=token.get();TerritoryPresence.accept(reply(old,"Город",true,"a".repeat(64)));
  check(TerritoryPresence.at(world,-1,2,token::set).capital(),"Capital received");
  check(TerritoryPresence.at(world,0,2,token::set)==null,"Chunk transition clears immediately");
  TerritoryPresence.accept(reply(old,"Старый",false,""));
  check(TerritoryPresence.at(world,0,2,token::set)==null,"Late old reply ignored");
  time.addAndGet(3000);TerritoryPresence.at(world,0,2,token::set);
  TerritoryPresence.accept(reply(token.get(),"Новый",false,""));
  check(TerritoryPresence.at(world,0,2,token::set).name().equals("Новый"),"New territory");
  time.addAndGet(8000);check(TerritoryPresence.at(world,0,2,token::set)==null,"Expired data hidden");
  TerritoryPresence.accept(reply(token.get(),null,false,""));
  check(TerritoryPresence.at(world,0,2,token::set)==null,"Wilderness clears");
  TerritoryPresence.accept(reply(token.get(),"Fake",true,""));
  check(TerritoryPresence.at(new Object(),0,2,token::set)==null,"World transition");
  try{TerritoryPresence.accept(reply("p1","Bad",false,"https://evil.test"));throw new AssertionError("Unsafe avatar");}catch(IOException expected){}
  TerritoryPresence.reset();check(TerritoryPresence.at(new Object(),0,0,token::set)==null,"Disconnect reset");
  System.out.println("TerritoryPresenceCheck PASS: transitions, late replies, expiry, wilderness, world, privacy bounds");
 }
}
