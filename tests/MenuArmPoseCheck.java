import com.wildfire.client.music.MenuArmPose;
import com.wildfire.client.music.MenuArmBend;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import java.util.*;

public final class MenuArmPoseCheck {
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static void near(float a,float b,float tolerance,String message) { check(Math.abs(a-b)<tolerance,message+": "+a+" / "+b); }
    public static void main(String[] args)throws Exception {
        float angle=-1.7f;
        // Optional 3D meshes have no vanilla cubes: exercise their emitted vertex path.
        float[] emitted=new float[3];
        var sink=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(
            MenuArmPoseCheck.class.getClassLoader(),new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},
            (proxy,method,values)->{
                if(method.getName().equals("addVertex")&&values.length==3){
                    for(int i=0;i<3;i++)emitted[i]=((Number)values[i]).floatValue();
                }
                return proxy;
            });
        var stack=new com.mojang.blaze3d.vertex.PoseStack();
        stack.translate(2,3,4);
        stack.rotate(com.mojang.math.Axis.YP.rotation(.7f));
        var world=new org.joml.Vector3f(0,10f/16f,2f/16f);
        stack.last().pose().transformPosition(world);
        var wrapped=new com.wildfire.client.music.MenuSleeveVertices(sink,stack.last(),angle);
        wrapped.addVertex(world.x,world.y,world.z);
        var expectedBend=MenuArmPose.sample(10f,angle);
        var expected=new org.joml.Vector3f(0,
            (expectedBend.y()-2f*(float)Math.sin(expectedBend.angle()))/16f,
            (expectedBend.z()+2f*(float)Math.cos(expectedBend.angle()))/16f);
        stack.last().pose().transformPosition(expected);
        near(emitted[0],expected.x,.00001f,"3D sleeve world X");
        near(emitted[1],expected.y,.00001f,"3D sleeve world Y");
        near(emitted[2],expected.z,.00001f,"3D sleeve world Z");
        for(boolean slim:new boolean[]{false,true}) {
            var root=net.minecraft.client.model.geom.builders.LayerDefinition.create(
                net.minecraft.client.model.player.PlayerModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(0f),slim),64,64).bakeRoot();
            var arm=root.getChild("right_arm");
            var sleeve=arm.getChild("right_sleeve");
            MenuArmBend.set(arm,angle);
            near(MenuArmBend.angle(arm),angle,.00001f,"reuse applied arm angle without another presence lookup");
            MenuArmBend.sleeve(sleeve,MenuArmBend.angle(arm));
            near(MenuArmBend.angle(sleeve),angle,.00001f,"sleeve shares exactly the arm angle");
            MenuArmBend.set(arm,0f);
            MenuArmBend.sleeve(sleeve,0f);
            near(MenuArmBend.angle(arm),0f,.00001f,"arm resets after gesture");
            near(MenuArmBend.angle(sleeve),0f,.00001f,"sleeve resets after gesture");
            var emptySleeve=new ModelPart(List.of(),Map.of());
            MenuArmBend.set(emptySleeve,0f);
            MenuArmBend.set(arm,angle);
            MenuArmBend.sleeve(emptySleeve,arm,angle);
            var partsField=MenuArmBend.class.getDeclaredField("PARTS");partsField.setAccessible(true);
            var parts=(Map<?,?>)partsField.get(null);
            var fallback=(List<?>)parts.get(emptySleeve);
            check(fallback.size()==1,"empty 3D sleeve gets flat skin fallback during menu");
            var fallbackCube=(ModelPart.Cube)fallback.getFirst();
            near(fallbackCube.maxX-fallbackCube.minX,slim?3f:4f,.00001f,"fallback matches slim/classic base arm width");
            float meshMin=Float.POSITIVE_INFINITY,meshMax=Float.NEGATIVE_INFINITY;
            for(var polygon:fallbackCube.polygons)for(var vertex:polygon.vertices()){
                meshMin=Math.min(meshMin,vertex.x());meshMax=Math.max(meshMax,vertex.x());
            }
            near(meshMax-meshMin,slim?3.5f:4.5f,.00001f,"fallback outer layer inflation");
            MenuArmBend.sleeve(emptySleeve,0f);
            near(MenuArmBend.angle(emptySleeve),0f,.00001f,"fallback deactivates outside gesture");
            MenuArmBend.set(arm,0f);
            check(sleeve.getInitialPose().equals(net.minecraft.client.model.geom.PartPose.ZERO),"sleeve inherits arm transform; never copy parent pose");
        }
        for(float y=-2.25f;y<=10.25f;y+=.125f) {
            var straight=MenuArmPose.sample(y,0);
            near(straight.y(),y,.00001f,"zero bend preserves skin"); near(straight.z(),0,.00001f,"zero bend Z");
            var p=MenuArmPose.sample(y,angle);
            check(Float.isFinite(p.y())&&Float.isFinite(p.z()),"finite bend");
            for(float z:new float[]{-2,0,2}) {
                float sin=(float)Math.sin(p.angle()),cos=(float)Math.cos(p.angle());
                float sy=p.y()-z*sin,sz=p.z()+z*cos;
                float sleeveY=p.y()-(z+.25f)*sin,sleeveZ=p.z()+(z+.25f)*cos;
                near((float)Math.hypot(sy-sleeveY,sz-sleeveZ),.25f,.00001f,"sleeve inflation remains attached");
            }
        }
        for(float boundary:new float[]{3f,5f}) {
            var a=MenuArmPose.sample(boundary-.0001f,angle);var b=MenuArmPose.sample(boundary+.0001f,angle);
            near(a.y(),b.y(),.001f,"continuous elbow Y");near(a.z(),b.z(),.001f,"continuous elbow Z");near(a.angle(),b.angle(),.001f,"continuous tangent");
        }
        var wrist=MenuArmPose.sample(10,angle);
        var elbow=MenuArmPose.sample(5,angle);
        check(wrist.z()<elbow.z()&&wrist.y()<elbow.y(),"forearm reaches forward and rises rather than straight out");
        var bake=MenuArmBend.class.getDeclaredMethod("bake",ModelPart.Cube.class);bake.setAccessible(true);
        for(float width:new float[]{3,4})for(float inflate:new float[]{0,.25f}) {
            var cube=new ModelPart.Cube(40,16,-3,-2,-2,width,12,4,inflate,inflate,inflate,false,64,64,EnumSet.allOf(Direction.class));
            var mesh=(List<?>)bake.invoke(null,cube);
            check(mesh.size()==98,"bounded 98-quad arm mesh");
            for(var quad:mesh) {
                var accessor=quad.getClass().getDeclaredMethod("vertices");accessor.setAccessible(true);
                var points=(ModelPart.Vertex[])accessor.invoke(quad);
                check(points.length==4,"quad topology");
                for(var v:points)check(v.u()>=0&&v.u()<=1&&v.v()>=0&&v.v()<=1,"original normalized UVs preserved");
            }
        }
        System.out.println("Menu arm checks passed: smooth elbow, attached sleeve, slim/wide arms, bounded mesh, skin UVs");
    }
}
