package com.wildfire.client.towns;

import java.io.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Optional backwards-compatible action-9 extension; never substitutes membership for location. */
public final class TerritoryPresence {
 public record Territory(String name,boolean capital,String avatar){}
 private static Object world; private static int x,z; private static long next,expires,sequence;
 private static String pending=""; private static Territory current;
 static LongSupplier clock=System::currentTimeMillis;
 private TerritoryPresence(){}
 public static void reset(){world=null;current=null;pending="";next=expires=0;}
 public static Territory at(Object level,int chunkX,int chunkZ,Consumer<String> request){
  long now=clock.getAsLong();
  if(world!=level||x!=chunkX||z!=chunkZ){world=level;x=chunkX;z=chunkZ;current=null;pending="";expires=0;}
  if(now>=next){next=now+3000;pending="p"+Long.toUnsignedString(++sequence,36);request.accept(pending);}
  return now<expires?current:null;
 }
 public static void accept(byte[] bytes)throws IOException{
  if(bytes.length>512)throw new IOException("Territory size");
  var in=new DataInputStream(new ByteArrayInputStream(bytes));
  if(in.readUnsignedByte()!=127)throw new IOException("Territory version");
  String token=in.readUTF();boolean present=in.readBoolean();Territory value=null;
  if(present){String name=in.readUTF();boolean capital=in.readBoolean();String avatar=in.readUTF();
   if(name.isBlank()||name.length()>64||name.codePoints().anyMatch(Character::isISOControl)||(!avatar.isEmpty()&&!avatar.matches("[a-f0-9]{64}")))throw new IOException("Territory data");
   value=new Territory(name,capital,avatar);
  }
  if(in.available()!=0)throw new IOException("Territory trailing bytes");
  if(!pending.isEmpty()&&token.equals(pending)){current=value;expires=clock.getAsLong()+7000;pending="";}
 }
}
