package com.wildfire.client.music;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Bends emitted geometry, including optional 3D skin meshes, in the sleeve's local space. */
public final class MenuSleeveVertices implements VertexConsumer {
    final VertexConsumer delegate;
    private final Matrix4f forward,inverse;
    private final Matrix3f normal,inverseNormal;
    private final float angle;
    private final Vector3f vector=new Vector3f();
    private float vertexAngle;
    public MenuSleeveVertices(VertexConsumer delegate,PoseStack.Pose pose,float angle){
        this.delegate=delegate;
        this.angle=angle;
        forward=new Matrix4f(pose.pose());inverse=new Matrix4f(forward).invert();
        normal=new Matrix3f(pose.normal());inverseNormal=new Matrix3f(normal).invert();
    }
    public static VertexConsumer wrap(ModelPart part,PoseStack pose,VertexConsumer delegate){
        float angle=MenuArmBend.angle(part);
        if(!SpringPlusSession.enabled()||Math.abs(angle)<=.001f)return delegate;
        pose.pushPose();
        try{
            part.translateAndRotate(pose);
            return new MenuSleeveVertices(delegate,pose.last(),angle);
        }finally{pose.popPose();}
    }
    @Override public VertexConsumer addVertex(float x,float y,float z){
        inverse.transformPosition(vector.set(x,y,z));
        var bend=MenuArmPose.sample(vector.y*16f,angle);
        vertexAngle=bend.angle();
        float sin=(float)Math.sin(vertexAngle),cos=(float)Math.cos(vertexAngle),localZ=vector.z*16f;
        vector.set(vector.x,(bend.y()-localZ*sin)/16f,(bend.z()+localZ*cos)/16f);
        forward.transformPosition(vector);
        delegate.addVertex(vector.x,vector.y,vector.z);
        return this;
    }
    @Override public VertexConsumer setNormal(float x,float y,float z){
        inverseNormal.transform(vector.set(x,y,z));
        float sin=(float)Math.sin(vertexAngle),cos=(float)Math.cos(vertexAngle),localY=vector.y,localZ=vector.z;
        vector.set(vector.x,localY*cos-localZ*sin,localY*sin+localZ*cos);
        normal.transform(vector).normalize();
        delegate.setNormal(vector.x,vector.y,vector.z);return this;
    }
    @Override public VertexConsumer setColor(int r,int g,int b,int a){delegate.setColor(r,g,b,a);return this;}
    @Override public VertexConsumer setColor(int color){delegate.setColor(color);return this;}
    @Override public VertexConsumer setUv(float u,float v){delegate.setUv(u,v);return this;}
    @Override public VertexConsumer setUv1(int u,int v){delegate.setUv1(u,v);return this;}
    @Override public VertexConsumer setUv2(int u,int v){delegate.setUv2(u,v);return this;}
    @Override public VertexConsumer setLineWidth(float width){delegate.setLineWidth(width);return this;}
}
