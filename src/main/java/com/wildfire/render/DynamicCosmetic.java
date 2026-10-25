package com.wildfire.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;

/** Bounded Bedrock box/per-face UV geometry, using the original texture atlas. */
public final class DynamicCosmetic {
    public final PlayerModel model;
    public final Model.Simple guiModel;
    public final Identifier texture;
    public final String hash;

    private DynamicCosmetic(PlayerModel model,Identifier texture,String hash){
        this.model=model;this.guiModel=new Model.Simple(((CosmeticModel)model).previewRoot(),RenderTypes::entityCutout);
        this.texture=texture;this.hash=hash;
    }
    private static float value(JsonArray array,int i){return array.get(i).getAsFloat();}
    private static JsonArray vector(JsonObject object,String name,float x,float y,float z){
        if(object.has(name))return object.getAsJsonArray(name);
        JsonArray result=new JsonArray();result.add(x);result.add(y);result.add(z);return result;
    }
    public static DynamicCosmetic create(int networkId,String hash,JsonObject pack,boolean headwear)throws Exception{
        JsonObject geometry=pack.getAsJsonObject("model").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description=geometry.getAsJsonObject("description");
        String geometryId=description.has("identifier")?description.get("identifier").getAsString():"";
        boolean bedrock=isBedrock(pack,geometry);
        if(bedrock)geometry=blockbenchGeometry(geometry);
        if(headwear&&geometryId.contains("dog_ears")){
            // The two tiny dangling tips and thin front strips make these look
            // like detached spikes at chibi preview scale. Keep the joined base
            // and the two broad, drooping sections on each side.
            for(var element:geometry.getAsJsonArray("bones")){
                JsonObject bone=element.getAsJsonObject();
                if(!bone.has("cubes")||bone.getAsJsonArray("cubes").size()!=10)continue;
                JsonArray original=bone.getAsJsonArray("cubes"),simple=new JsonArray();
                for(int index:new int[]{0,1,2,5,6,7})simple.add(original.get(index));
                bone.add("cubes",simple);
            }
        }
        int width=description.get("texture_width").getAsInt(),height=description.get("texture_height").getAsInt();
        if(width<16||height<16||width>2048||height>2048)throw new IllegalArgumentException("Invalid atlas");
        MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();
        CubeListBuilder empty=CubeListBuilder.create();
        PartDefinition head=root.addOrReplaceChild(PartNames.HEAD,empty,PartPose.ZERO);
        head.addOrReplaceChild(PartNames.HAT,empty,PartPose.ZERO);
        PartDefinition body=root.addOrReplaceChild(PartNames.BODY,empty,PartPose.ZERO);
        body.addOrReplaceChild("jacket",empty,PartPose.ZERO);
        root.addOrReplaceChild(PartNames.LEFT_ARM,empty,PartPose.offset(5,2,0)).addOrReplaceChild("left_sleeve",empty,PartPose.ZERO);
        root.addOrReplaceChild(PartNames.RIGHT_ARM,empty,PartPose.offset(-5,2,0)).addOrReplaceChild("right_sleeve",empty,PartPose.ZERO);
        root.addOrReplaceChild(PartNames.LEFT_LEG,empty,PartPose.offset(1.9f,12,0)).addOrReplaceChild("left_pants",empty,PartPose.ZERO);
        root.addOrReplaceChild(PartNames.RIGHT_LEG,empty,PartPose.offset(-1.9f,12,0)).addOrReplaceChild("right_pants",empty,PartPose.ZERO);
        // Pets are independent models. Keeping them below the avatar body makes
        // PlayerModel.setupAnim bend and scale the entire pet with the player.
        // The layer already applies the avatar head transform. Parenting to
        // body applies swimming/gliding body rotation a second time.
        PartDefinition cosmetic=root.addOrReplaceChild("cosmetic",empty,PartPose.ZERO);
        Map<String,JsonObject> definitions=new HashMap<>();
        for(var element:geometry.getAsJsonArray("bones")){
            JsonObject bone=element.getAsJsonObject();definitions.put(bone.get("name").getAsString(),bone);
        }
        Map<String,PartDefinition> parts=new HashMap<>();
        for(String name:definitions.keySet())addBone(name,definitions,parts,cosmetic,new java.util.HashSet<>());
        boolean chibi=description.has("identifier")
            && description.get("identifier").getAsString().contains("springplus.chibi");
        float minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        if(headwear)for(var element:geometry.getAsJsonArray("bones")){
            JsonObject bone=element.getAsJsonObject();
            if(!bone.has("cubes"))continue;
            for(var cubeElement:bone.getAsJsonArray("cubes")){
                JsonObject cube=cubeElement.getAsJsonObject();
                float y=value(cube.getAsJsonArray("origin"),1);
                minY=Math.min(minY,y);maxY=Math.max(maxY,y+value(cube.getAsJsonArray("size"),1));
            }
        }
        float headwearDrop=0f;
        if(headwear&&!geometryId.contains(".fitted.")){
            if(geometryId.contains("dog_ears"))headwearDrop=0.35f;
            else if(geometryId.contains("_ears"))headwearDrop=0.45f;
            else if(geometryId.contains("devil_horns"))headwearDrop=0.2f;
            else if(geometryId.contains("deer_antlers"))headwearDrop=-0.2f;
            else if(geometryId.contains("panama_hat")||geometryId.contains("farmer_straw_hat")
                ||geometryId.contains("propeller_cap")||geometryId.contains("propeller_hat"))headwearDrop=-1.5f;
        }
        ModelPart baked=LayerDefinition.create(mesh,width,height).bakeRoot();
        ModelPart bakedCosmetic=baked.getChild("cosmetic");
        for(var entry:definitions.entrySet()){
            JsonArray cubes=entry.getValue().getAsJsonArray("cubes");if(cubes==null)continue;
            ModelPart bonePart=CosmeticModel.findPart(entry.getKey(),definitions,new HashMap<>(),bakedCosmetic);
            for(int i=0;i<cubes.size();i++){
                JsonObject definition=cubes.get(i).getAsJsonObject();
                if(!definition.get("uv").isJsonObject())continue;
                bonePart.getChild("cube_"+i).visit(new PoseStack(),(pose,path,index,cube)->applyFaceUvs(cube,definition,width,height));
            }
        }
        PlayerModel model=new CosmeticModel(baked,definitions,chibi,headwear,headwearDrop,
            pack.has("animation")&&!pack.get("animation").isJsonNull()?pack.getAsJsonObject("animation"):null,bedrock);
        byte[] png=Base64.getDecoder().decode(pack.get("texture").getAsString());
        NativeImage image=NativeImage.read(png);
        Identifier id=Identifier.fromNamespaceAndPath("appearance_sync","cosmetic/"+hash);
        DynamicTexture texture=new DynamicTexture(()->"SpringPlus cosmetic "+networkId,image);
        Minecraft.getInstance().getTextureManager().register(id,texture);texture.upload();
        return new DynamicCosmetic(model,id,hash);
    }
    private static PartDefinition addBone(String name,Map<String,JsonObject> definitions,
                                          Map<String,PartDefinition> parts,PartDefinition cosmetic,
                                          java.util.Set<String> visiting){
        PartDefinition cached=parts.get(name);if(cached!=null)return cached;
        if(!visiting.add(name))throw new IllegalArgumentException("Bone cycle");
        JsonObject bone=definitions.get(name);
        String parentName=bone.has("parent")?bone.get("parent").getAsString():null;
        PartDefinition parent=parentName==null?cosmetic:addBone(parentName,definitions,parts,cosmetic,visiting);
        JsonArray pivot=vector(bone,"pivot",0,0,0);
        JsonArray parentPivot=parentName==null?null:vector(definitions.get(parentName),"pivot",0,0,0);
        float px=value(pivot,0),py=value(pivot,1),pz=value(pivot,2);
        float x=px-(parentPivot==null?0:value(parentPivot,0));
        float y=(parentPivot==null?24:value(parentPivot,1))-py;
        float z=pz-(parentPivot==null?0:value(parentPivot,2));
        JsonArray rotation=vector(bone,"rotation",0,0,0);
        PartDefinition part=parent.addOrReplaceChild(name,CubeListBuilder.create(),
            PartPose.offsetAndRotation(x,y,z,(float)Math.toRadians(-value(rotation,0)),
                (float)Math.toRadians(value(rotation,1)),(float)Math.toRadians(-value(rotation,2))));
        parts.put(name,part);
        if(bone.has("cubes")){
            int index=0;
            for(var element:bone.getAsJsonArray("cubes")){
                JsonObject cube=element.getAsJsonObject();
                JsonArray origin=cube.getAsJsonArray("origin"),size=cube.getAsJsonArray("size");
                JsonArray uv=cube.get("uv").isJsonArray()?cube.getAsJsonArray("uv"):vector(new JsonObject(),"uv",0,0,0);
                JsonArray cubePivot=vector(cube,"pivot",px,py,pz);
                float cx=value(cubePivot,0),cy=value(cubePivot,1),cz=value(cubePivot,2);
                JsonArray cubeRot=vector(cube,"rotation",0,0,0);
                float inflate=cube.has("inflate")?cube.get("inflate").getAsFloat()
                    :bone.has("inflate")?bone.get("inflate").getAsFloat():0f;
                CubeListBuilder box=CubeListBuilder.create().texOffs((int)value(uv,0),(int)value(uv,1))
                    .addBox(value(origin,0)-cx,cy-value(origin,1)-value(size,1),value(origin,2)-cz,
                        value(size,0),value(size,1),value(size,2),new CubeDeformation(inflate));
                part.addOrReplaceChild("cube_"+index++,box,PartPose.offsetAndRotation(cx-px,py-cy,cz-pz,
                    (float)Math.toRadians(-value(cubeRot,0)),(float)Math.toRadians(value(cubeRot,1)),
                    (float)Math.toRadians(-value(cubeRot,2))));
            }
        }
        visiting.remove(name);return part;
    }
    private static boolean isBedrock(JsonObject pack,JsonObject geometry){
        if(pack.has("coordinateSpace"))return "bedrock".equals(pack.get("coordinateSpace").getAsString());
        JsonObject description=geometry.getAsJsonObject("description");
        String id=description.has("identifier")?description.get("identifier").getAsString():"";
        if(id.contains(".bedrock_v2."))return true;
        for(var bone:geometry.getAsJsonArray("bones")){
            JsonArray cubes=bone.getAsJsonObject().getAsJsonArray("cubes");if(cubes==null)continue;
            for(var cube:cubes)if(cube.getAsJsonObject().get("uv").isJsonObject())return true;
        }
        // Original generated models use the historical SpringPlus convention.
        // A standard Blockbench Bedrock export is not identified by its UV mode.
        return pack.getAsJsonObject("model").has("format_version")&&!id.startsWith("geometry.springplus.");
    }
    private static JsonArray tuple(float... values){JsonArray result=new JsonArray();for(float v:values)result.add(v);return result;}
    private static void face(JsonObject faces,String name,float u,float v,float w,float h){
        JsonObject face=new JsonObject();face.add("uv",tuple(u,v));face.add("uv_size",tuple(w,h));faces.add(name,face);
    }
    private static JsonObject blockbenchGeometry(JsonObject source){
        JsonObject geometry=source.deepCopy();
        for(var element:geometry.getAsJsonArray("bones")){
            JsonObject bone=element.getAsJsonObject();
            if(!bone.has("cubes"))continue;
            for(var item:bone.getAsJsonArray("cubes")){
                JsonObject cube=item.getAsJsonObject();JsonArray size=cube.getAsJsonArray("size");
                if(cube.get("uv").isJsonArray()){
                    JsonArray uv=cube.getAsJsonArray("uv");float u=value(uv,0),v=value(uv,1),w=value(size,0),h=value(size,1),d=value(size,2);
                    JsonObject faces=new JsonObject();
                    face(faces,"east",u,v+d,d,h);face(faces,"west",u+d+w,v+d,d,h);
                    face(faces,"north",u+d,v+d,w,h);face(faces,"south",u+2*d+w,v+d,w,h);
                    face(faces,"up",u+d+w,v+d,-w,-d);face(faces,"down",u+d+2*w,v,-w,d);
                    boolean mirror=cube.has("mirror")?cube.get("mirror").getAsBoolean():bone.has("mirror")&&bone.get("mirror").getAsBoolean();
                    if(mirror){
                        for(var entry:faces.entrySet()){JsonObject f=entry.getValue().getAsJsonObject();JsonArray a=f.getAsJsonArray("uv"),b=f.getAsJsonArray("uv_size");
                            f.add("uv",tuple(value(a,0)+value(b,0),value(a,1)));f.add("uv_size",tuple(-value(b,0),value(b,1)));}
                        JsonElement east=faces.get("east");faces.add("east",faces.get("west"));faces.add("west",east);
                    }
                    cube.add("uv",faces);
                }
            }
        }
        return geometry;
    }
    private static void applyFaceUvs(ModelPart.Cube cube,JsonObject definition,int width,int height){
        com.wildfire.client.render.CosmeticCubeRenderer.mark(cube);
        JsonObject faces=definition.getAsJsonObject("uv");
        JsonArray size=definition.getAsJsonArray("size");
        float inflate=definition.has("inflate")?definition.get("inflate").getAsFloat():0;
        for(int i=0;i<cube.polygons.length;i++){
            var polygon=cube.polygons[i];var normal=polygon.normal();
            String face=normal.x()>0.5f?"west":normal.x()<-0.5f?"east":
                normal.y()<-0.5f?"up":normal.y()>0.5f?"down":normal.z()<0?"north":"south";
            if(!faces.has(face)){
                cube.polygons[i]=new ModelPart.Polygon(new ModelPart.Vertex[0],normal);continue;
            }
            JsonObject mapping=faces.getAsJsonObject(face);
            JsonArray uv=mapping.getAsJsonArray("uv");
            float defaultU=(face.equals("east")||face.equals("west"))?value(size,2):value(size,0);
            float defaultV=(face.equals("up")||face.equals("down"))?value(size,2):value(size,1);
            JsonArray extent=mapping.has("uv_size")?mapping.getAsJsonArray("uv_size"):null;
            float du=extent==null?defaultU:value(extent,0),dv=extent==null?defaultV:value(extent,1);
            int rotation=mapping.has("uv_rotation")?mapping.get("uv_rotation").getAsInt():0;
            ModelPart.Vertex[] vertices=polygon.vertices().clone();
            for(int j=0;j<vertices.length;j++){
                var vertex=vertices[j];
                float x=(vertex.x()-cube.minX+inflate)/(cube.maxX-cube.minX+2*inflate);
                float y=(vertex.y()-cube.minY+inflate)/(cube.maxY-cube.minY+2*inflate);
                float z=(vertex.z()-cube.minZ+inflate)/(cube.maxZ-cube.minZ+2*inflate);
                float u=switch(face){case "east"->1-z;case "west"->z;case "south"->1-x;default->x;};
                float v=switch(face){case "up"->1-z;case "down"->z;default->y;};
                for(int turn=0;turn<Math.floorMod(rotation,360)/90;turn++){float old=u;u=1-v;v=old;}
                vertices[j]=vertex.remap((value(uv,0)+u*du)/width,(value(uv,1)+v*dv)/height);
            }
            cube.polygons[i]=new ModelPart.Polygon(vertices,normal);
        }
    }
    private static final class CosmeticModel extends PlayerModel {
        private final ModelPart cosmetic;
        private final boolean chibi;
        private final float headwearDrop;
        private final ModelPart previewRoot;
        private final java.util.List<AnimatedBone> animated=new ArrayList<>();
        private final java.util.List<ScaledBone> scaled=new ArrayList<>();
        private final float duration;
        private final float animationAxes;
        private record Frame(float time,float x,float y,float z){}
        private record AnimatedBone(ModelPart part,float baseX,float baseY,float baseZ,java.util.List<Frame> frames){}
        private record ScaledBone(ModelPart part,float x,float y,float z){}
        CosmeticModel(ModelPart root,Map<String,JsonObject> definitions,boolean chibi,boolean headwear,float headwearDrop,JsonObject animation,boolean bedrock){
            super(root,false);cosmetic=root.getChild("cosmetic");this.chibi=chibi;this.headwearDrop=headwearDrop;
            this.previewRoot=cosmetic;this.animationAxes=1f;
            for(var entry:definitions.entrySet()){
                JsonObject definition=entry.getValue();
                if(!definition.has("scale"))continue;
                JsonArray scale=definition.getAsJsonArray("scale");
                scaled.add(new ScaledBone(findPart(entry.getKey(),definitions,new HashMap<>(),cosmetic),
                    value(scale,0),value(scale,1),value(scale,2)));
            }
            float length=1;
            if(animation!=null&&animation.has("animations"))try{
                JsonObject animations=animation.getAsJsonObject("animations");
                if(!animations.entrySet().isEmpty()){
                    JsonObject first=animations.entrySet().iterator().next().getValue().getAsJsonObject();
                    if(first.has("animation_length"))length=Math.max(0.05f,Math.min(3600f,first.get("animation_length").getAsFloat()));
                    JsonObject bones=first.getAsJsonObject("bones");
                    if(bones!=null)for(var entry:bones.entrySet()){
                        JsonObject definition=definitions.get(entry.getKey());
                        if(definition==null||!entry.getValue().isJsonObject())continue;
                        JsonObject controls=entry.getValue().getAsJsonObject();
                        if(!controls.has("rotation"))continue;
                        var frames=parseFrames(controls.get("rotation"));
                        if(frames.isEmpty())continue;
                        ModelPart part=findPart(entry.getKey(),definitions,new HashMap<>(),cosmetic);
                        JsonArray rotation=vector(definition,"rotation",0,0,0);
                        animated.add(new AnimatedBone(part,(float)Math.toRadians(-value(rotation,0)),
                            (float)Math.toRadians(value(rotation,1)),(float)Math.toRadians(-value(rotation,2)),frames));
                    }
                }
            }catch(RuntimeException ignored){}
            duration=length;
        }
        ModelPart previewRoot(){return previewRoot;}
        private static ModelPart findPart(String name,Map<String,JsonObject> definitions,
                                          Map<String,ModelPart> cache,ModelPart root){
            if(cache.containsKey(name))return cache.get(name);
            JsonObject definition=definitions.get(name);
            ModelPart parent=definition.has("parent")
                ?findPart(definition.get("parent").getAsString(),definitions,cache,root):root;
            ModelPart part=parent.getChild(name);cache.put(name,part);return part;
        }
        private static java.util.List<Frame> parseFrames(JsonElement element){
            var frames=new ArrayList<Frame>();
            if(element.isJsonArray()){
                JsonArray vector=element.getAsJsonArray();
                if(vector.size()==3)frames.add(new Frame(0,value(vector,0),value(vector,1),value(vector,2)));
            }else if(element.isJsonObject()){
                for(var entry:element.getAsJsonObject().entrySet())try{
                    float time=Float.parseFloat(entry.getKey());
                    JsonElement vector=entry.getValue();
                    if(vector.isJsonObject())vector=vector.getAsJsonObject().get("vector");
                    if(vector!=null&&vector.isJsonArray()&&vector.getAsJsonArray().size()==3){
                        JsonArray values=vector.getAsJsonArray();
                        frames.add(new Frame(time,value(values,0),value(values,1),value(values,2)));
                    }
                }catch(RuntimeException ignored){}
            }
            frames.sort(Comparator.comparingDouble(Frame::time));
            if(frames.size()>64)frames.subList(64,frames.size()).clear();
            return frames;
        }
        @Override public void setupAnim(AvatarRenderState state){
            super.setupAnim(state);
            cosmetic.y=headwearDrop;
            cosmetic.xScale=chibi?0.85f:1f;
            for(ScaledBone bone:scaled){
                bone.part.xScale=bone.x;bone.part.yScale=bone.y;bone.part.zScale=bone.z;
            }
            float time=(state.ageInTicks/20f)%duration;
            for(AnimatedBone bone:animated){
                Frame before=bone.frames.getFirst(),after=before;
                for(Frame frame:bone.frames){if(frame.time<=time)before=frame;else{after=frame;break;}}
                if(after==before)after=before;
                float blend=after.time>before.time?(time-before.time)/(after.time-before.time):0;
                float x=before.x+(after.x-before.x)*blend;
                float y=before.y+(after.y-before.y)*blend;
                float z=before.z+(after.z-before.z)*blend;
                bone.part.xRot=bone.baseX+(float)Math.toRadians(-x*animationAxes);
                bone.part.yRot=bone.baseY+(float)Math.toRadians(y*animationAxes);
                bone.part.zRot=bone.baseZ+(float)Math.toRadians(-z);
            }
        }
        @Override public void renderToBuffer(PoseStack pose,VertexConsumer vertices,int light,int overlay,int color){
            cosmetic.render(pose,vertices,light,overlay,color);
        }
    }
}
