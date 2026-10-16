package com.wildfire.client.music;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import java.util.*;

/** Only marked player arm cubes are bent. Original mesh/UVs remain untouched and reusable. */
public final class MenuArmBend {
    private record Quad(ModelPart.Vertex[] vertices, float nx, float ny, float nz) {}
    private static final class Entry { float angle; final List<Quad> mesh; Entry(ModelPart.Cube cube) { mesh=bake(cube); } }
    private static final Map<ModelPart.Cube,Entry> CUBES=new WeakHashMap<>();
    private static final Map<ModelPart,List<ModelPart.Cube>> PARTS=new WeakHashMap<>();
    private static final Map<ModelPart,Float> SLEEVES=new WeakHashMap<>();
    private static final Map<ModelPart,Float> ANGLES=new WeakHashMap<>();
    private static boolean loggedSleevePose,loggedSleeveRender;
    private MenuArmBend() {}
    public static void sleeve(ModelPart part,ModelPart arm,float angle){
        // Optional mesh layers (and armor models) can leave the sleeve with no cubes.
        // Use the skin's flat outer layer only for the active gesture, never mutate the 3D mesh.
        if(Math.abs(angle)>.001f&&PARTS.getOrDefault(part,List.of()).isEmpty()){
            var armCubes=PARTS.getOrDefault(arm,List.of());
            if(armCubes.isEmpty())return;
            var source=armCubes.getFirst();
            var cube=new ModelPart.Cube(40,32,source.minX,source.minY,source.minZ,
                source.maxX-source.minX,source.maxY-source.minY,source.maxZ-source.minZ,
                .25f,.25f,.25f,false,64,64,EnumSet.allOf(net.minecraft.core.Direction.class));
            PARTS.put(part,List.of(cube));
        }
        sleeve(part,angle);
    }
    public static void sleeve(ModelPart part,float angle){
        if(Math.abs(angle)>.001f){
            if(!loggedSleevePose){
                loggedSleevePose=true;
                org.slf4j.LoggerFactory.getLogger("SpringPlus-menu-arm").info("Bent sleeve local pose={} initial={} angle={}",part.storePose(),part.getInitialPose(),angle);
            }
            // A sleeve is a child of the arm in 26.2; other animation integrations
            // may leave a copied parent pose here. Do not apply that transform twice.
            part.loadPose(part.getInitialPose());
        }
        set(part,angle);
        if(Math.abs(angle)>.001f)SLEEVES.put(part,angle);else SLEEVES.remove(part);
    }
    public static boolean renderSleeve(ModelPart part,PoseStack pose,VertexConsumer vertices,int light,int overlay,int color){
        if(!SpringPlusSession.enabled()||!SLEEVES.containsKey(part))return false;
        if(!loggedSleeveRender){
            loggedSleeveRender=true;
            org.slf4j.LoggerFactory.getLogger("SpringPlus-menu-arm").info("Bent sleeve render reached: visible={} skipDraw={} cubes={} localPose={}",part.visible,part.skipDraw,PARTS.getOrDefault(part,List.of()).size(),part.storePose());
        }
        if(!part.visible)return true;
        pose.pushPose();
        try{
            part.translateAndRotate(pose);
            if(!part.skipDraw)for(var cube:PARTS.getOrDefault(part,List.of()))
                render(cube,pose.last(),vertices,light,overlay,color);
        }finally{pose.popPose();}
        return true;
    }
    public static void hand(ModelPart arm,PoseStack pose) {
        if(!SpringPlusSession.enabled())return;
        var cubes=PARTS.get(arm); if(cubes==null)return;
        for(var cube:cubes) {
            var entry=CUBES.get(cube); if(entry==null || Math.abs(entry.angle)<.001f)continue;
            var elbow=MenuArmPose.sample(5f,entry.angle);
            float cos=(float)Math.cos(entry.angle),sin=(float)Math.sin(entry.angle);
            pose.translate(0,(elbow.y()-5f*cos)/16f,(elbow.z()-5f*sin)/16f);
            pose.mulPose(com.mojang.math.Axis.XP.rotation(entry.angle));
            return;
        }
    }
    public static void set(ModelPart part,float angle) {
        if(Math.abs(angle)>.001f)ANGLES.put(part,angle);else ANGLES.remove(part);
        List<ModelPart.Cube> cubes=PARTS.computeIfAbsent(part,p->{
            List<ModelPart.Cube> result=new ArrayList<>();
            p.visit(new PoseStack(),(pose,path,index,cube)->{ if(path.isEmpty())result.add(cube); });
            return List.copyOf(result);
        });
        for(var cube:cubes) {
            Entry entry=CUBES.get(cube);
            if(entry==null && Math.abs(angle)>.001f) { entry=new Entry(cube); CUBES.put(cube,entry); }
            if(entry!=null)entry.angle=angle;
        }
    }
    /** The angle already applied this frame, independent of presence expiry and mesh availability. */
    public static float angle(ModelPart part) {
        return ANGLES.getOrDefault(part,0f);
    }
    public static boolean render(ModelPart.Cube cube,PoseStack.Pose pose,VertexConsumer vertices,int light,int overlay,int color) {
        Entry entry=CUBES.get(cube);
        if(entry==null || Math.abs(entry.angle)<.001f || !SpringPlusSession.enabled())return false;
        // The subdivided vanilla mesh is already bent here; never bend it twice.
        if(vertices instanceof MenuSleeveVertices sleeve)vertices=sleeve.delegate;
        for(var quad:entry.mesh)for(var v:quad.vertices) {
            var bend=MenuArmPose.sample(v.y(),entry.angle);
            float sin=(float)Math.sin(bend.angle()), cos=(float)Math.cos(bend.angle());
            vertices.addVertex(pose,v.x()/16f,(bend.y()-v.z()*sin)/16f,(bend.z()+v.z()*cos)/16f)
                .setColor(color).setUv(v.u(),v.v()).setOverlay(overlay).setLight(light)
                .setNormal(pose,quad.nx,quad.ny*cos-quad.nz*sin,quad.ny*sin+quad.nz*cos);
        }
        return true;
    }
    private static List<Quad> bake(ModelPart.Cube cube) {
        List<Quad> result=new ArrayList<>();
        for(var polygon:cube.polygons) {
            var v=polygon.vertices(); var normal=polygon.normal();
            boolean first=Math.abs(v[1].y()-v[0].y())>.001f, second=Math.abs(v[3].y()-v[0].y())>.001f;
            int segments=first||second?24:1;
            for(int i=0;i<segments;i++) {
                float a=i/(float)segments,b=(i+1)/(float)segments;
                ModelPart.Vertex[] points=first ? new ModelPart.Vertex[]{lerp(v[0],v[1],a),lerp(v[0],v[1],b),lerp(v[3],v[2],b),lerp(v[3],v[2],a)}
                    : second ? new ModelPart.Vertex[]{lerp(v[0],v[3],a),lerp(v[1],v[2],a),lerp(v[1],v[2],b),lerp(v[0],v[3],b)} : v;
                result.add(new Quad(points,normal.x(),normal.y(),normal.z()));
            }
        }
        return List.copyOf(result);
    }
    private static ModelPart.Vertex lerp(ModelPart.Vertex a,ModelPart.Vertex b,float t) {
        return new ModelPart.Vertex(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t,a.u()+(b.u()-a.u())*t,a.v()+(b.v()-a.v())*t);
    }
}
