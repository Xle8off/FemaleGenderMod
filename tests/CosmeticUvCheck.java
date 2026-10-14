import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import java.util.EnumSet;
public class CosmeticUvCheck{
 private static void checkHeadphones(Class<?> type,String fixture)throws Exception {
  var pack=JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(fixture))).getAsJsonObject();
  var convert=type.getDeclaredMethod("blockbenchGeometry",com.google.gson.JsonObject.class);convert.setAccessible(true);
  var detect=type.getDeclaredMethod("isBedrock",com.google.gson.JsonObject.class,com.google.gson.JsonObject.class);detect.setAccessible(true);
  var original=pack.getAsJsonObject("model").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
  original.getAsJsonObject("description").addProperty("identifier","geometry.HP");
  if(!(boolean)detect.invoke(null,pack,original))throw new AssertionError("Plain Blockbench Box UV must use Bedrock coordinates");
  original.getAsJsonObject("description").addProperty("identifier","geometry.springplus.legacy_hat");
  if((boolean)detect.invoke(null,pack,original))throw new AssertionError("Preserve legacy generated geometry");
  pack.addProperty("coordinateSpace","bedrock");
  if(!(boolean)detect.invoke(null,pack,original))throw new AssertionError("Explicit convention overrides legacy name");
  var geometry=(com.google.gson.JsonObject)convert.invoke(null,pack.getAsJsonObject("model").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject());
  var definitions=new java.util.HashMap<String,com.google.gson.JsonObject>();
  for(var element:geometry.getAsJsonArray("bones")){var bone=element.getAsJsonObject();definitions.put(bone.get("name").getAsString(),bone);}
  var mesh=new net.minecraft.client.model.geom.builders.MeshDefinition();
  var empty=net.minecraft.client.model.geom.builders.CubeListBuilder.create();
  for(var pair:new String[][]{{"head","hat"},{"body","jacket"},{"left_arm","left_sleeve"},{"right_arm","right_sleeve"},{"left_leg","left_pants"},{"right_leg","right_pants"}})
   mesh.getRoot().addOrReplaceChild(pair[0],empty,net.minecraft.client.model.geom.PartPose.ZERO).addOrReplaceChild(pair[1],empty,net.minecraft.client.model.geom.PartPose.ZERO);
  var cosmetic=mesh.getRoot().addOrReplaceChild("cosmetic",empty,net.minecraft.client.model.geom.PartPose.ZERO);
  var add=type.getDeclaredMethod("addBone",String.class,java.util.Map.class,java.util.Map.class,net.minecraft.client.model.geom.builders.PartDefinition.class,java.util.Set.class);add.setAccessible(true);
  var parts=new java.util.HashMap<String,net.minecraft.client.model.geom.builders.PartDefinition>();
  for(var name:definitions.keySet())add.invoke(null,name,definitions,parts,cosmetic,new java.util.HashSet<String>());
  var root=net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh,32,32).bakeRoot();
  var cosmeticType=Class.forName(type.getName()+"$CosmeticModel");
  var constructor=cosmeticType.getDeclaredConstructor(ModelPart.class,java.util.Map.class,boolean.class,boolean.class,float.class,com.google.gson.JsonObject.class,boolean.class);constructor.setAccessible(true);
  var model=(net.minecraft.client.model.player.PlayerModel)constructor.newInstance(root,definitions,false,true,0f,null,true);
  model.setupAnim(new net.minecraft.client.renderer.entity.state.AvatarRenderState());
  var bounds=new float[]{Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
  var count=new int[1];
  root.visit(new com.mojang.blaze3d.vertex.PoseStack(),(pose,path,index,cube)->{
   count[0]++;
   for(var polygon:cube.polygons)for(var vertex:polygon.vertices()){
    var point=pose.pose().transformPosition(new org.joml.Vector3f(vertex.x()/16f,vertex.y()/16f,vertex.z()/16f)).mul(16f);
    for(int axis=0;axis<3;axis++){float v=point.get(axis);bounds[axis]=Math.min(bounds[axis],v);bounds[axis+3]=Math.max(bounds[axis+3],v);}
   }
  });
  if(count[0]!=5)throw new AssertionError("Headphones lost cubes");
  for(int axis=0;axis<6;axis++)check(bounds[axis],new float[]{-6.12f,-9.18f,-2.04f,6.12f,-2.04f,2.04f}[axis]);
  System.out.println("Production headphones: native bounds match web fitting, all five cubes retained");
  var animation=JsonParser.parseString("{\"animations\":{\"test\":{\"bones\":{\"bone\":{\"rotation\":[30,40,50]}}}}}").getAsJsonObject();
  var animated=(net.minecraft.client.model.player.PlayerModel)constructor.newInstance(root,definitions,false,true,0f,animation,true);
  animated.setupAnim(new net.minecraft.client.renderer.entity.state.AvatarRenderState());
  var rotating=root.getChild("cosmetic").getChild("fitting_root").getChild("bone");
  check(rotating.xRot,(float)Math.toRadians(30));check(rotating.yRot,(float)Math.toRadians(-40));check(rotating.zRot,(float)Math.toRadians(-50));
 }
 private static void checkRoundedBreasts(boolean neo)throws Exception {
  String renderer = neo ? "com.wildfire.client.render.WildfireModelRenderer" : "com.wildfire.render.WildfireModelRenderer";
  var weight = Class.forName(renderer).getMethod("deformationWeight",float.class,float.class);
  for(float depthValue : new float[]{0f,.01f,.5f,3f}) {
   if((float)weight.invoke(null,0f,depthValue)!=0f
      ||(float)weight.invoke(null,1.5f,depthValue)!=0f)
    throw new AssertionError("Torso root must remain fixed during deformation");
   float d=Math.max(.01f,depthValue);
   if(Math.abs((float)weight.invoke(null,-d,d)-1f)>.0001f
      ||Math.abs((float)weight.invoke(null,-d*.5f,d)-.25f)>.0001f)
    throw new AssertionError("Smooth bounded outer-surface deformation");
  }
  Class<?> rounded = Class.forName(renderer + "$RoundedBreastModelBox");
  Class<?> shapeType = Class.forName("com.wildfire.client.appearance.BreastShape");
  var profile = rounded.getDeclaredMethod("profile",float.class,float.class,float.class,float.class,shapeType);
  profile.setAccessible(true);
  for(Object shape : shapeType.getEnumConstants()) for(float size : new float[]{0,.2f,.8f})
   for(float center : new float[]{-2,2}) for(float inflate : new float[]{0,.035f,.12f}) {
    Object p = profile.invoke(null,center,size,inflate,0f,shape);
    var xm=p.getClass().getDeclaredMethod("x");xm.setAccessible(true);
    var dm=p.getClass().getDeclaredMethod("depth");dm.setAccessible(true);
    float[] x=(float[])xm.invoke(p);float[][] depth=(float[][])dm.invoke(p);
    boolean block=shape.toString().equals("LEGACY")||shape.toString().contains("BLOCKS");
     if(x.length!=(block?5:13))throw new AssertionError("Breast subdivision: "+shape);
    for(int c=1;c<x.length;c++)if(x[c]<=x[c-1])throw new AssertionError("Ordered breast vertices");
    for(float[] row:depth)for(float d:row)if(!Float.isFinite(d)||d<inflate||d>4)throw new AssertionError("Finite bounded breast profile");
   }
  Class<?> inbox=Class.forName(neo?"com.wildfire.neoforge.appearance.NotificationInbox":"com.wildfire.main.networking.appearance.NotificationInbox");
  var sender=inbox.getMethod("sender",String.class,String.class,String.class);
  if(!sender.invoke(null,"ЛС","Xle8","hello").equals("Xle8")
   ||!sender.invoke(null,"Друзья","Друзья","nansyholod вошла в сеть").equals("nansyholod")
   ||!sender.invoke(null,"Друзья","Друзья","Заявка от Pattrik").equals("Pattrik")
   ||!sender.invoke(null,"Музыка","Xle8","Xle8 вошла в сеть").equals(""))
   throw new AssertionError("Only known player notification templates identify senders");
  System.out.println("Rounded breast profiles and notification identity checks passed");
 }
 public static void main(String[]args)throws Exception{
  checkRoundedBreasts(args[0].contains("neoforge"));
  Class<?> type=Class.forName(args[0]);
  checkHeadphones(type,args[1]);
  var convert=type.getDeclaredMethod("blockbenchGeometry",com.google.gson.JsonObject.class);convert.setAccessible(true);
  var source=JsonParser.parseString("{\"bones\":[{\"name\":\"visor\",\"pivot\":[2,3,4],\"rotation\":[30,40,50],\"cubes\":[{\"origin\":[3,5,7],\"size\":[2,4,6],\"uv\":{\"up\":{\"uv\":[36,36],\"uv_size\":[-12,-5]}}}]}]}").getAsJsonObject();
  var converted=(com.google.gson.JsonObject)convert.invoke(null,source);
  var implicit=JsonParser.parseString("{\"bones\":[{\"name\":\"test\",\"pivot\":[7,24,1],\"cubes\":[{\"origin\":[0,0,0],\"size\":[1,1,1],\"rotation\":[30,0,0],\"uv\":[0,0]}]}]}").getAsJsonObject();
  var implicitResult=(com.google.gson.JsonObject)convert.invoke(null,implicit);
  var implicitPivot=implicitResult.getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonArray("pivot");
  for(var value:implicitPivot)check(value.getAsFloat(),0);
  var bone=converted.getAsJsonArray("bones").get(0).getAsJsonObject();
  check(bone.getAsJsonArray("pivot").get(0).getAsFloat(),-2);
  check(bone.getAsJsonArray("rotation").get(0).getAsFloat(),-30);
  check(bone.getAsJsonArray("rotation").get(1).getAsFloat(),-40);
  var c=bone.getAsJsonArray("cubes").get(0).getAsJsonObject();check(c.getAsJsonArray("origin").get(0).getAsFloat(),-5);
  check(c.getAsJsonObject("uv").getAsJsonObject("up").getAsJsonArray("uv").get(0).getAsFloat(),24);
  check(c.getAsJsonObject("uv").getAsJsonObject("up").getAsJsonArray("uv_size").get(1).getAsFloat(),5);
  var method=type.getDeclaredMethod("applyFaceUvs",ModelPart.Cube.class,com.google.gson.JsonObject.class,int.class,int.class);
  method.setAccessible(true);
  for(float inflate:new float[]{0,0.5f}){
   var cube=new ModelPart.Cube(0,0,-4,-4,-4,8,8,8,inflate,inflate,inflate,false,64,64,EnumSet.allOf(Direction.class));
   var definition=JsonParser.parseString("{\"size\":[8,8,8],\"uv\":{\"north\":{\"uv\":[4,8],\"uv_size\":[8,8]},\"east\":{\"uv\":[20,20],\"uv_size\":[-8,8]},\"up\":{\"uv\":[36,36],\"uv_size\":[-12,-5],\"uv_rotation\":90}}}").getAsJsonObject();
   definition.addProperty("inflate",inflate);method.invoke(null,cube,definition,64,64);
   int visible=0;
   for(var polygon:cube.polygons){
    if(polygon.vertices().length==0)continue;visible++;
    var normal=polygon.normal();
    for(var v:polygon.vertices()){
     if(normal.z()<-.5f){check(v.u()*64,v.x()>0?12:4);check(v.v()*64,v.y()<0?8:16);}
     if(normal.x()>.5f){check(v.u()*64,v.z()>0?12:20);check(v.v()*64,v.y()<0?20:28);}
     if(normal.y()<-.5f){check(v.u()*64,v.z()>0?24:36);check(v.v()*64,v.x()>0?31:36);}
    }
   }
   if(visible!=3)throw new AssertionError("Omitted faces must be invisible");
  }
  System.out.println("Native per-face UV checks passed: "+args[0]);
  var layer=Class.forName(args[0].replace("DynamicCosmetic","CosmeticLayer"));
  var create=layer.getDeclaredMethod("createChibiModel",boolean.class);create.setAccessible(true);
  for(boolean slim:new boolean[]{false,true}){
   var model=(net.minecraft.client.model.player.PlayerModel)create.invoke(null,slim);
   var poseAs=model.getClass().getDeclaredMethod("poseAs",int.class,int.class);poseAs.setAccessible(true);
   for(int partner:new int[]{-1,0,1}){
    poseAs.invoke(model,partner,2);model.setupAnim(new net.minecraft.client.renderer.entity.state.AvatarRenderState());
    for(var outer:new ModelPart[]{model.hat,model.jacket,model.leftSleeve,model.rightSleeve,model.leftPants,model.rightPants}){
     check(outer.x,0);check(outer.y,0);check(outer.z,0);check(outer.xRot,0);check(outer.yRot,0);check(outer.zRot,0);
     check(outer.xScale,1);check(outer.yScale,1);check(outer.zScale,1);
    }
    if(model.leftArm.getChild("left_sleeve")!=model.leftSleeve||model.rightArm.getChild("right_sleeve")!=model.rightSleeve)throw new AssertionError("Expected child overlays");
   }
  }
  System.out.println("Chibi child-layer checks passed for wide/slim and paired poses");
 }
 static void check(float actual,float expected){if(Math.abs(actual-expected)>.001)throw new AssertionError(actual+" != "+expected);}
}
