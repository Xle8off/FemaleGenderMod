package com.wildfire.client.vr;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Receive-only NONVR participant in VSE's native vivecraft:data protocol.
 * Wire reference: VivecraftMod UberPacketPayloadS2C, VrPlayerState and Pose.
 * Never sends controller poses, movement, teleport requests or VR=true.
 */
public final class VivecraftObserver {
 private static Consumer<byte[]> send;
 private static BooleanSupplier available=()->false;
 private static Object connection,world;
 private static long lastHello;
 private static boolean acknowledged;
 private static int attempts;
 public static void configure(Consumer<byte[]> sender,BooleanSupplier channel){send=sender;available=channel;}
 public static void reset(){lastHello=0;acknowledged=false;attempts=0;}
 public static byte[] hello(){
  byte[] text="Vivecraft 26.3-1.3.15 SpringPlus NONVR\n0\n0".getBytes(StandardCharsets.UTF_8);
  byte[] bytes=new byte[text.length+1];System.arraycopy(text,0,bytes,1,text.length);return bytes;
 }
 public static void tick(){
  var mc=net.minecraft.client.Minecraft.getInstance();
  if(connection!=mc.getConnection()||world!=mc.level){connection=mc.getConnection();world=mc.level;reset();}
  if(mc.player==null||world==null||send==null||acknowledged||attempts>=3||!available.getAsBoolean())return;
  long now=System.nanoTime();if(lastHello!=0&&now-lastHello<5_000_000_000L)return;
  lastHello=now;attempts++;send.accept(hello());
 }
 public static boolean accept(byte[] bytes){
  if(bytes.length==0||bytes.length>2048)return false;
  int type=bytes[0]&255;
  if(type==0){acknowledged=true;return true;}
  if(type!=8)return false; // Ignore server settings; this is not a VR client.
  try{
   if(bytes.length!=112&&bytes.length!=197&&bytes.length!=309)return false;
   var b=ByteBuffer.wrap(bytes);b.get();UUID id=new UUID(b.getLong(),b.getLong());
   var translated=ByteBuffer.allocate(90);translated.put((byte)1).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).put((byte)0);
   int handed=0;Quaternionf[] orientations=new Quaternionf[3];
   for(int part=0;part<3;part++){
    int flag=b.get()&255;if(flag>1)return false;if(part==1)handed=flag;
    float x=b.getFloat(),y=b.getFloat(),z=b.getFloat();
    float w=b.getFloat(),qx=b.getFloat(),qy=b.getFloat(),qz=b.getFloat();
    float norm=w*w+qx*qx+qy*qy+qz*qz;
    if(!Float.isFinite(norm)||norm<.5f||norm>1.5f)return false;
    var q=new Quaternionf(qx,qy,qz,w).normalize();
    orientations[part]=q;
    var dir=q.transform(new Vector3f(0,0,-1));
    float pitch=(float)Math.asin(Math.clamp(dir.y,-1,1));
    float yaw=(float)Math.atan2(-dir.x,dir.z);
    float roll=-(float)Math.atan2(2*(q.x*q.y+q.w*q.z),q.w*q.w-q.x*q.x+q.y*q.y-q.z*q.z);
    translated.putFloat(x).putFloat(y).putFloat(z).putFloat(pitch).putFloat(yaw).putFloat(roll);
   }
   if(bytes.length>112){int fbt=b.get()&255;if(fbt!= (bytes.length==197?1:2))return false;b.position(b.limit()-8);}
   float scale=b.getFloat(),height=b.getFloat();
   if(!Float.isFinite(scale)||!Float.isFinite(height)||scale<=0||height<=0||scale>100||height>10)return false;
   translated.put(17,(byte)handed);
   return VrPoseClient.acceptNative(translated.array(),orientations,scale,height);
  }catch(java.nio.BufferUnderflowException ex){return false;}
 }
}
