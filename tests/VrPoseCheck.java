import java.nio.ByteBuffer;
import java.util.UUID;
import com.wildfire.client.vr.VrPoseClient;
public final class VrPoseCheck {
 public static void main(String[] args){
  byte[] hello=com.wildfire.client.vr.VivecraftObserver.hello();
  for(float wrist:new float[]{-1,1,-.5f,.5f})for(float x:new float[]{-4,0,4})for(float z:new float[]{-4,4}){
   float y=5;float[] solution=VrPoseClient.solveAttachedArm(x,y,z,wrist);
   if(solution[4]>=0)throw new AssertionError("Elbow bend direction regressed");
   var end=com.wildfire.client.music.MenuArmPose.sample(10,solution[4]);
   var endpoint=new org.joml.Quaternionf(solution[0],solution[1],solution[2],solution[3]).transform(new org.joml.Vector3f(wrist,end.y(),end.z()));
   near(endpoint.x,x);near(endpoint.y,y);near(endpoint.z,z);
   var origin=new org.joml.Quaternionf(solution[0],solution[1],solution[2],solution[3]).transform(new org.joml.Vector3f());
   near(origin.length(),0);
  }
  String helloText=new String(hello,1,hello.length-1,java.nio.charset.StandardCharsets.UTF_8);
  if(hello[0]!=0||!helloText.endsWith("NONVR\n0\n0"))throw new AssertionError("Not a NONVR handshake");
  for(int size:new int[]{112,197,309}){
   var nativePacket=ByteBuffer.allocate(size);nativePacket.put((byte)8).putLong(1).putLong(2);
   for(int part=0;part<3;part++)nativePacket.put((byte)0).putFloat(.2f).putFloat(1.4f).putFloat(.1f).putFloat(1).putFloat(0).putFloat(0).putFloat(0);
   if(size>112){nativePacket.put((byte)(size==197?1:2));nativePacket.position(size-8);}
   nativePacket.putFloat(1).putFloat(1);
   if(!com.wildfire.client.vr.VivecraftObserver.accept(nativePacket.array()))throw new AssertionError("Native VSE pose rejected: "+size);
   byte[] invalidNative=nativePacket.array().clone();ByteBuffer.wrap(invalidNative).putFloat(30,Float.NaN);
   if(com.wildfire.client.vr.VivecraftObserver.accept(invalidNative))throw new AssertionError("Native NaN quaternion accepted");
  }
  if(com.wildfire.client.vr.VivecraftObserver.accept(new byte[]{8}))throw new AssertionError("Truncated native pose accepted");
  var b=ByteBuffer.allocate(90);b.put((byte)1).putLong(1).putLong(2).put((byte)0);
  for(int i=0;i<18;i++)b.putFloat(i%6==1?1.6f:0f);
  if(!VrPoseClient.accept(b.array()))throw new AssertionError("valid pose rejected");
  if(VrPoseClient.accept(new byte[91]))throw new AssertionError("oversize accepted");
  byte[] invalid=b.array().clone();invalid[17]=2;if(VrPoseClient.accept(invalid))throw new AssertionError("flag");
  invalid=b.array().clone();ByteBuffer.wrap(invalid).putFloat(18,Float.NaN);if(VrPoseClient.accept(invalid))throw new AssertionError("NaN");
  invalid=b.array().clone();ByteBuffer.wrap(invalid).putFloat(18,100);if(VrPoseClient.accept(invalid))throw new AssertionError("offset");
  invalid=b.array().clone();invalid[0]=9;if(VrPoseClient.accept(invalid))throw new AssertionError("version");
  for(float body:new float[]{0,90,180,-90,37})for(float pitch:new float[]{0,.7f,-1.2f})for(float yaw:new float[]{0,1.4f,-2.2f})for(float roll:new float[]{0,.8f,-1.1f})for(float wristX:new float[]{-1,1,-.5f,.5f}){
   float x=.35f,y=1.3f,z=.6f;
   float[] t=VrPoseClient.partTransform(x,y,z,pitch,yaw,roll,body,true,wristX);
   var rendered=new org.joml.Quaternionf().rotationZYX(t[5],t[4],t[3]);
   var wrist=rendered.transform(new org.joml.Vector3f(wristX,10,0)).add(t[0],t[1],t[2]);
   if(Math.abs((24-wrist.y)/16-y)>.0001f)throw new AssertionError("wrist drift body="+body+" pitch="+pitch+" yaw="+yaw+" roll="+roll+" transform="+java.util.Arrays.toString(t));
   float a=(float)Math.toRadians(body),c=(float)Math.cos(a),s=(float)Math.sin(a);
   near((wrist.x*c+wrist.z*s)/16,x);near((24-wrist.y)/16,y);near((wrist.x*s-wrist.z*c)/16,z);
   // Local arm +Y must point along the actual world controller forward vector.
   var direction=rendered.transform(new org.joml.Vector3f(0,1,0));
   near(direction.x*c+direction.z*s,-(float)Math.sin(yaw)*(float)Math.cos(pitch));
   near(-direction.y,(float)Math.sin(pitch));
   near(direction.x*s-direction.z*c,(float)Math.cos(yaw)*(float)Math.cos(pitch));
  }
  System.out.println("VR packet validation and wrist/forward transforms passed for body, pitch, yaw, roll and slim/wide arms");
 }
 private static void near(float actual,float expected){if(Math.abs(actual-expected)>.0001f)throw new AssertionError(actual+" != "+expected);}
}
