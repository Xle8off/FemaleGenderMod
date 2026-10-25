package com.wildfire.client.vr;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
/** VSE observer only: no Vivecraft channel registration or client impersonation. */
public final class VrPoseClient {
 private record Pose(float[] previous,float[] parts,boolean left,long received){}
 private static final Map<UUID,Pose> poses=new HashMap<>();
 private record NativeRotation(org.joml.Quaternionf[] previous,org.joml.Quaternionf[] current,float scale,float height,long time){}
 private static final Map<UUID,NativeRotation> rotations=new HashMap<>();
 private static final Map<AvatarRenderState,UUID> states=new WeakHashMap<>();
 private static final Set<ModelPart> bentArms=Collections.newSetFromMap(new WeakHashMap<>());
 private static Object connection;
 private static Object world;
 private static final Set<String> diagnostics=new HashSet<>();
 private static void diagnostic(String key,String message){if(diagnostics.size()<512&&diagnostics.add(key))org.slf4j.LoggerFactory.getLogger("SpringPlus-VR").info(message);}
 private static java.util.function.BooleanSupplier nativeRenderer=()->false;
 public static void configure(java.util.function.BooleanSupplier check){nativeRenderer=check;}
 private static void session(){var mc=Minecraft.getInstance();Object current=mc==null?null:mc.getConnection(),level=mc==null?null:mc.level;if(connection!=current||world!=level){connection=current;world=level;poses.clear();rotations.clear();states.clear();diagnostics.clear();}}
 public static boolean acceptNative(byte[] translated,org.joml.Quaternionf[] quaternions,float scale,float height){
  if(!accept(translated))return false;
  var b=ByteBuffer.wrap(translated);b.get();UUID id=new UUID(b.getLong(),b.getLong());
  long now=System.nanoTime();NativeRotation old=rotations.get(id);
  if(rotations.size()>=256&&!rotations.containsKey(id))rotations.entrySet().removeIf(e->now-e.getValue().time>500_000_000L);
  if(rotations.size()>=256&&!rotations.containsKey(id))return false;
  org.joml.Quaternionf[] previous=new org.joml.Quaternionf[3];
  for(int i=0;i<3;i++)previous[i]=old==null?new org.joml.Quaternionf(quaternions[i]):nativeRotation(old,i,now);
  rotations.put(id,new NativeRotation(previous,quaternions,scale,height,now));return true;
 }
 private static org.joml.Quaternionf nativeRotation(NativeRotation pose,int index,long now){return new org.joml.Quaternionf(pose.previous[index]).slerp(pose.current[index],Math.clamp((now-pose.time)/100_000_000f,0,1));}
 public static boolean accept(byte[] bytes){
  session();if(bytes.length!=90)return false;
  var b=ByteBuffer.wrap(bytes);if(b.get()!=1)return false;UUID id=new UUID(b.getLong(),b.getLong());
  int left=b.get()&255;if(left>1)return false;
  float[] parts=new float[18];for(int i=0;i<18;i++){parts[i]=b.getFloat();if(!Float.isFinite(parts[i])||Math.abs(parts[i])>(i%6<3?8:32))return false;}
  if(poses.size()>=256&&!poses.containsKey(id))poses.entrySet().removeIf(e->System.nanoTime()-e.getValue().received>500_000_000L);
  if(poses.size()>=256&&!poses.containsKey(id))return false;
  Pose old=poses.get(id);long now=System.nanoTime();
  poses.put(id,new Pose(old==null||now-old.received>500_000_000L?parts:sample(old,now),parts,left==1,now));
  diagnostic("receive:"+id,"[vr] First pose received for "+id);return true;
 }
 public static void bind(UUID id,AvatarRenderState state){session();states.put(state,id);}
 public static void apply(AvatarRenderState state,net.minecraft.client.model.HumanoidModel<?> model){
  for(ModelPart arm:new ModelPart[]{model.rightArm,model.leftArm})if(bentArms.remove(arm))com.wildfire.client.music.MenuArmBend.set(arm,0);
  if(model instanceof PlayerModel playerModel){
   com.wildfire.client.music.MenuArmBend.sleeve(playerModel.rightSleeve,0);
   com.wildfire.client.music.MenuArmBend.sleeve(playerModel.leftSleeve,0);
  }
  session();if(nativeRenderer.getAsBoolean())return;
  // Deferred rendering may use another state instance: resolve the stable entity ID too.
  var level=Minecraft.getInstance().level;
  var entity=level==null?null:level.getEntity(state.id);
  UUID id=entity==null?states.get(state):entity.getUUID();Pose pose=poses.get(id);
  if(pose==null||System.nanoTime()-pose.received>500_000_000L)return;
  var mc=Minecraft.getInstance();if(mc==null||mc.level==null||mc.level.getPlayerByUUID(id)==null)return;
  float[] p=sample(pose,System.nanoTime());
  diagnostic("apply:"+id+":"+model.getClass().getSimpleName(),"[vr] Applying tracked pose for "+id+" to "+model.getClass().getSimpleName());
  NativeRotation nativePose=rotations.get(id);
  if(nativePose!=null){
   nativePart(model.head,p,0,state.bodyRot,false,0,nativePose);
  }else part(model.head,p,0,state.bodyRot,false,0);
  // Model pivots are not the centre of the arm mesh (including the slim model).
  float wristX=model instanceof PlayerModel&&state.skin.model()==net.minecraft.world.entity.player.PlayerModelType.SLIM?.5f:1f;
  if(nativePose!=null){
   nativePart(model.rightArm,p,pose.left?12:6,state.bodyRot,true,-wristX,nativePose);
   nativePart(model.leftArm,p,pose.left?6:12,state.bodyRot,true,wristX,nativePose);
   if(model instanceof PlayerModel playerModel){
    com.wildfire.client.music.MenuArmBend.sleeve(playerModel.rightSleeve,playerModel.rightArm,com.wildfire.client.music.MenuArmBend.angle(playerModel.rightArm));
    com.wildfire.client.music.MenuArmBend.sleeve(playerModel.leftSleeve,playerModel.leftArm,com.wildfire.client.music.MenuArmBend.angle(playerModel.leftArm));
   }
  }else{
   part(model.rightArm,p,pose.left?12:6,state.bodyRot,true,-wristX);
   part(model.leftArm,p,pose.left?6:12,state.bodyRot,true,wristX);
  }
  // Sleeves in 26.2 are arm children: no second copy of the parent transform.
 }
 private static float[] sample(Pose pose,long now){
  float t=Math.clamp((now-pose.received)/100_000_000f,0,1);float[] result=new float[18];
  for(int i=0;i<18;i++){float delta=pose.parts[i]-pose.previous[i];
   if(i%6>=3)delta=(float)Math.atan2(Math.sin(delta),Math.cos(delta));
   result[i]=pose.previous[i]+delta*t;
  }return result;
 }
 private static void part(ModelPart part,float[] p,int at,float body,boolean arm,float wristX){
  float[] transform=partTransform(p[at],p[at+1],p[at+2],p[at+3],p[at+4],p[at+5],body,arm,wristX);
  part.x=transform[0];part.y=transform[1];part.z=transform[2];
  part.xRot=transform[3];part.yRot=transform[4];part.zRot=transform[5];
 }
 private static void nativePart(ModelPart part,float[] p,int at,float body,boolean arm,float wristX,NativeRotation pose){
  float yaw=(float)Math.toRadians(body),c=(float)Math.cos(yaw),s=(float)Math.sin(yaw);
  float unit=.9375f*pose.height,scale=16/(unit*pose.scale);
  var position=new org.joml.Vector3f((p[at]*c+p[at+2]*s)*scale,1.501f*16-p[at+1]*scale,(p[at]*s-p[at+2]*c)*scale);
  var orientation=new org.joml.Quaternionf().rotationX((float)Math.PI).rotateY(yaw).mul(nativeRotation(pose,at/6,System.nanoTime()));
  if(arm){
   // A controller is a target for the wrist, NOT the attachment point of the arm.
   // Keep the original shoulder pivot; solve the bent mesh towards that target.
   var shoulder=part.getInitialPose();
   var delta=position.sub(shoulder.x(),shoulder.y(),shoulder.z());
   float[] solution=solveAttachedArm(delta.x,delta.y,delta.z,wristX);
   orientation.set(solution[0],solution[1],solution[2],solution[3]);
   com.wildfire.client.music.MenuArmBend.set(part,solution[4]);
   bentArms.add(part);
   position.set(shoulder.x(),shoulder.y(),shoulder.z());
  }
  else{orientation.rotateZ((float)Math.PI);position.y+=3.2f;}
  var m=new org.joml.Matrix3f().rotation(orientation);
  float sy=Math.clamp(-m.m02(),-1,1);
  part.x=position.x;part.y=position.y;part.z=position.z;
  if(Math.abs(sy)>.999999f){part.yRot=Math.copySign((float)Math.PI/2,sy);part.xRot=(float)Math.atan2(-m.m21(),m.m11());part.zRot=0;}
  else{part.yRot=(float)Math.asin(sy);part.xRot=(float)Math.atan2(m.m12(),m.m22());part.zRot=(float)Math.atan2(m.m01(),m.m00());}
 }
 /** Solve the existing curved elbow mesh without ever translating its shoulder. */
 public static float[] solveAttachedArm(float x,float y,float z,float wristX){
  var target=new org.joml.Vector3f(x,y,z);float distance=target.length();
  float lo=0,hi=2.6f;
  for(int i=0;i<24;i++){
   float mid=(lo+hi)/2;var end=com.wildfire.client.music.MenuArmPose.sample(10,mid);
   float length=(float)Math.sqrt(wristX*wristX+end.y()*end.y()+end.z()*end.z());
   if(length>distance)lo=mid;else hi=mid;
  }
  float bend=-(lo+hi)/2;var end=com.wildfire.client.music.MenuArmPose.sample(10,bend);
  var local=new org.joml.Vector3f(wristX,end.y(),end.z()).normalize();
  var q=distance<.001f?new org.joml.Quaternionf():new org.joml.Quaternionf().rotationTo(local,target.normalize());
  return new float[]{q.x,q.y,q.z,q.w,bend};
 }
 /** World offset -> model frame, then controller-local roll and wrist anchoring. */
 public static float[] partTransform(float x,float y,float z,float pitch,float yaw,float roll,float body,boolean arm,float wristX){
  float bodyRadians=(float)Math.toRadians(body);
  float cos=(float)Math.cos(bodyRadians),sin=(float)Math.sin(bodyRadians);
  // LivingEntityRenderer rotates by 180-body and flips model X/Y.
  var position=new org.joml.Vector3f((x*cos+z*sin)*16,24-y*16,(x*sin-z*cos)*16);
  // Roll belongs to the controller's local axis, not the model's global Z axis.
  var rotation=new org.joml.Quaternionf().rotationY(yaw-bodyRadians).rotateX(-pitch).rotateZ(-roll);
  if(arm){
   rotation.rotateX(-(float)Math.PI/2);
   position.sub(rotation.transform(new org.joml.Vector3f(wristX,10,0)));
  }else position.y+=3.2f;
  // ModelPart renders Rz * Ry * Rx. Decompose the complete orientation once.
  var matrix=new org.joml.Matrix3f().rotation(rotation);
  float sy=Math.clamp(-matrix.m02(),-1,1),rx,ry,rz;
  if(Math.abs(sy)>.999999f){
   ry=Math.copySign((float)Math.PI/2,sy);
   rx=(float)Math.atan2(-matrix.m21(),matrix.m11());rz=0;
  }else{
   ry=(float)Math.asin(sy);
   rx=(float)Math.atan2(matrix.m12(),matrix.m22());
   rz=(float)Math.atan2(matrix.m01(),matrix.m00());
  }
  return new float[]{position.x,position.y,position.z,rx,ry,rz};
 }
}
