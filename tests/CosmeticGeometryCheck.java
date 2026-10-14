import com.google.gson.*;
import java.util.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import com.mojang.blaze3d.vertex.PoseStack;
public class CosmeticGeometryCheck {
 public static void main(String[] args)throws Exception {
  var fixture=JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(args[1]))).getAsJsonObject();
  var type=Class.forName(args[0]);
  var convert=type.getDeclaredMethod("blockbenchGeometry",JsonObject.class);convert.setAccessible(true);
  var geometry=(JsonObject)convert.invoke(null,fixture.getAsJsonObject("model").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject());
  var definitions=new HashMap<String,JsonObject>();
  for(var element:geometry.getAsJsonArray("bones")){var bone=element.getAsJsonObject();definitions.put(bone.get("name").getAsString(),bone);}
  var mesh=new MeshDefinition();var parts=new HashMap<String,PartDefinition>();
  var add=type.getDeclaredMethod("addBone",String.class,Map.class,Map.class,PartDefinition.class,Set.class);add.setAccessible(true);
  for(var name:definitions.keySet())add.invoke(null,name,definitions,parts,mesh.getRoot(),new HashSet<String>());
  var description=geometry.getAsJsonObject("description");int width=description.get("texture_width").getAsInt(),height=description.get("texture_height").getAsInt();
  var root=LayerDefinition.create(mesh,width,height).bakeRoot();
  var uv=type.getDeclaredMethod("applyFaceUvs",ModelPart.Cube.class,JsonObject.class,int.class,int.class);uv.setAccessible(true);
  var expected=fixture.getAsJsonObject("expected");var visited=new HashSet<String>();
  root.visit(new PoseStack(),(pose,path,index,cube)->{
   var names=path.split("/");String boneName=names[names.length-2],cubeName=names[names.length-1],key=boneName+"/"+cubeName;
   var definition=definitions.get(boneName).getAsJsonArray("cubes").get(Integer.parseInt(cubeName.substring(5))).getAsJsonObject();
   try{uv.invoke(null,cube,definition,width,height);}catch(Exception error){throw new RuntimeException(error);}
   var wanted=expected.getAsJsonArray(key);var matched=new HashSet<Integer>();
   var emitted=new ArrayList<float[]>();
   var consumer=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(CosmeticGeometryCheck.class.getClassLoader(),new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(proxy,method,values)->{
    if(method.getName().equals("addVertex")&&values.length==11){
     if(!values[3].equals(0xFFABCDEF)||!values[6].equals(123)||!values[7].equals(456))throw new AssertionError("Render attributes lost");
     emitted.add(new float[]{(float)values[0]*16f,(float)values[1]*16f,(float)values[2]*16f,(float)values[4],(float)values[5]});return null;
    }
    throw new AssertionError("Unexpected vertex method: "+method);
   });
   com.wildfire.client.render.CosmeticCubeRenderer.compile(cube,pose,consumer,456,123,0xFFABCDEF);
   for(var actual:emitted){
    boolean found=false;
    for(int candidate=0;candidate<wanted.size();candidate++){
     if(matched.contains(candidate))continue;var values=wanted.get(candidate).getAsJsonArray();boolean equal=true;
     for(int axis=0;axis<5;axis++)if(Math.abs(actual[axis]-values.get(axis).getAsFloat())>.001f)equal=false;
     if(equal){matched.add(candidate);found=true;break;}
    }
    if(!found)throw new AssertionError("Native geometry/UV differs from web: "+key+" "+Arrays.toString(actual));
   }
   if(matched.size()!=wanted.size())throw new AssertionError("Missing vertices: "+key);visited.add(key);
  });
  if(visited.size()!=expected.size())throw new AssertionError("Lost cubes");
  System.out.println("Production horns: custom GPU vertex stream matches the web reference ("+visited.size()+" cubes)");
 }
}
