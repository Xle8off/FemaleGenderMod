package net.springplus.screens;

import java.io.*;
import java.util.*;

/** Layout and scoped media launch tickets only. Never transports server secrets or media bytes. */
public final class ScreenProtocol {
 public static final String CHANNEL="springplus:screens";
 public static final int MAX_SCREENS=128,MAX_BYTES=8192;
 public record Screen(UUID id,ScreenGeometry geometry,boolean canUse,boolean mediaActive){public Screen(UUID id,ScreenGeometry geometry){this(id,geometry,false,false);}public Screen(UUID id,ScreenGeometry geometry,boolean canUse){this(id,geometry,canUse,false);}}
 public record Snapshot(long nonce,UUID world,List<Screen> screens,UUID opened,String mediaUrl,int mediaAction){
  public Snapshot{screens=List.copyOf(screens);if(mediaUrl==null)mediaUrl="";}
  public Snapshot(long nonce,UUID world,List<Screen> screens){this(nonce,world,screens,null,"",0);}
  public Snapshot(long nonce,UUID world,List<Screen> screens,UUID opened){this(nonce,world,screens,opened,"",0);}
 }
 public record Request(long nonce,UUID open,boolean watch){public Request(long nonce,UUID open){this(nonce,open,false);}}
 private ScreenProtocol(){}
 public static byte[] request(long nonce)throws IOException{return request(nonce,null);}
 public static byte[] request(long nonce,UUID open)throws IOException{if(nonce<=0)throw new IOException("Nonce");var b=new ByteArrayOutputStream();var out=new DataOutputStream(b);out.writeByte(2);out.writeLong(nonce);if(open!=null)uuid(out,open);return b.toByteArray();}
 public static byte[] watchRequest(long nonce,UUID id)throws IOException{byte[] bytes=request(nonce,Objects.requireNonNull(id));bytes[0]=3;return bytes;}
 public static long readRequest(byte[] bytes)throws IOException{return readRequestData(bytes).nonce;}
 public static Request readRequestData(byte[] bytes)throws IOException{if(bytes.length!=9&&bytes.length!=25)throw new IOException("Request size");var in=new DataInputStream(new ByteArrayInputStream(bytes));int version=in.readUnsignedByte();if(version<1||version>3||version==1&&bytes.length!=9||version==3&&bytes.length!=25)throw new IOException("Version");long nonce=in.readLong();if(nonce<=0)throw new IOException("Nonce");return new Request(nonce,bytes.length==25?uuid(in):null,version==3);}
 private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
 private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
 public static byte[] encode(Snapshot data)throws IOException{
  if(data.nonce<=0||data.screens.size()>MAX_SCREENS)throw new IOException("Snapshot bounds");
  if(data.mediaUrl.length()>3072||data.mediaAction<0||data.mediaAction>2||data.mediaAction!=0&&(data.opened==null||data.mediaUrl.isEmpty()))throw new IOException("Media bounds");
  var b=new ByteArrayOutputStream();var out=new DataOutputStream(b);out.writeByte(4);out.writeLong(data.nonce);uuid(out,data.world);out.writeShort(data.screens.size());out.writeBoolean(data.opened!=null);if(data.opened!=null)uuid(out,data.opened);out.writeByte(data.mediaAction);out.writeUTF(data.mediaUrl);
  var ids=new HashSet<UUID>();for(var s:data.screens){if(!ids.add(s.id))throw new IOException("Duplicate ID");uuid(out,s.id);out.writeBoolean(s.canUse);out.writeBoolean(s.mediaActive);var g=s.geometry;out.writeInt(g.x());out.writeInt(g.y());out.writeInt(g.z());out.writeByte(g.width());out.writeByte(g.height());out.writeByte(g.face().ordinal());}
  byte[] bytes=b.toByteArray();if(bytes.length>MAX_BYTES)throw new IOException("Snapshot size");return bytes;
 }
 public static Snapshot decode(byte[] bytes)throws IOException{
  if(bytes.length>MAX_BYTES)throw new IOException("Snapshot size");
  var in=new DataInputStream(new ByteArrayInputStream(bytes));int version=in.readUnsignedByte();if(version<1||version>4)throw new IOException("Version");long nonce=in.readLong();if(nonce<=0)throw new IOException("Nonce");UUID world=uuid(in);int count=in.readUnsignedShort();if(count>MAX_SCREENS)throw new IOException("Count");UUID opened=version>=2&&in.readBoolean()?uuid(in):null;int action=version>=3?in.readUnsignedByte():0;String media=version>=3?in.readUTF():"";
  if(media.length()>3072||action>2||action!=0&&(opened==null||media.isEmpty()))throw new IOException("Media bounds");
  var result=new ArrayList<Screen>();var ids=new HashSet<UUID>();
  try{for(int i=0;i<count;i++){UUID id=uuid(in);if(!ids.add(id))throw new IOException("Duplicate ID");boolean canUse=version>=2&&in.readBoolean(),active=version>=4&&in.readBoolean();int x=in.readInt(),y=in.readInt(),z=in.readInt(),w=in.readUnsignedByte(),h=in.readUnsignedByte(),face=in.readUnsignedByte();if(face>=ScreenGeometry.Face.values().length)throw new IOException("Face");result.add(new Screen(id,new ScreenGeometry(x,y,z,w,h,ScreenGeometry.Face.values()[face]),canUse,active));}}
  catch(IllegalArgumentException e){throw new IOException("Geometry",e);}
  if(in.available()!=0)throw new IOException("Trailing bytes");if(opened!=null&&result.stream().noneMatch(s->s.id.equals(opened)&&(action!=0||s.canUse||!media.isEmpty())))throw new IOException("Invalid grant");return new Snapshot(nonce,world,result,opened,media,action);
 }
}
