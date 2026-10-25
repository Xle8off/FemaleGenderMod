package com.wildfire.client.music;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HumanoidModel;
import java.util.UUID;

/** Decorative world panel: a fixed thirteen-quad mesh, no text, textures or live UI. */
public final class MenuWorldRenderer {
    private MenuWorldRenderer() {}
    private static MenuPresence.View view(UUID owner) {
        var client=Minecraft.getInstance();
        if(SpringPlusSession.enabled() && client.player!=null && owner.equals(client.player.getUUID())
            && client.gui.screen() instanceof MenuPreviewSource menu) MenuPresence.put(owner,menu.worldMenuCategory());
        return MenuPresence.view(owner);
    }
    public static boolean gesture(UUID owner,AvatarRenderState state,HumanoidModel<?> model) {
        var view=view(owner);
        if(view==null||state.isInvisible||state.isInvisibleToPlayer||state.pose==net.minecraft.world.entity.Pose.SLEEPING||state.isPassenger
            ||state.swingAnimation>0||state.isUsingItem||com.wildfire.client.render.EmoteMotionBridge.blocksGesture(state))return false;
        ModelPart arm=model.rightArm;
        float weight=view.amount();
        arm.xRot=arm.xRot+(-.45f-arm.xRot)*weight;
        arm.yRot=arm.yRot+(-.12f-arm.yRot)*weight;
        arm.zRot=arm.zRot+(-.12f-arm.zRot)*weight;
        MenuArmBend.set(arm,-1.70f*weight);
        return true;
    }
    public static void submit(UUID owner,AvatarRenderState state,PoseStack pose,SubmitNodeCollector out,CameraRenderState camera) {
        var view=view(owner);
        if(view==null||state.isInvisible||state.isInvisibleToPlayer||state.isDiscrete||state.distanceToCameraSq>576)return;
        var client=Minecraft.getInstance();
        if(client.level==null||camera!=client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState)return;
        if(client.player!=null&&owner.equals(client.player.getUUID())&&client.options.getCameraType().isFirstPerson())return;
        final float amount=view.amount();final int category=view.category();
        pose.pushPose();
        try {
            pose.rotate(Axis.YP.rotationDegrees(180f-state.bodyRot));
            // Outside the torso silhouette: the owner can inspect it in rear-view F5.
            pose.translate(.58,1.25,-.65);
            pose.rotate(Axis.XP.rotationDegrees(-12f));
            float side=Math.cos(Math.toRadians(camera.yRot-state.bodyRot))>=0?1f:-1f;
            pose.scale(.009f*amount,-.009f*amount,.009f*side);
            out.submitCustomGeometry(pose,RenderTypes.debugQuads(),(matrix,vertices)->{
                rect(matrix,vertices,-40,-24,40,24,0,alpha(0x202733,amount*.9f));
                rect(matrix,vertices,-39,-23,-37,23,.02f,alpha(0xB681D4,amount));
                rect(matrix,vertices,-32,-17,32,-14,.02f,alpha(0x8893A5,amount*.8f));
                for(int i=0;i<4;i++)rect(matrix,vertices,-32,-9+i*7,-27,-4+i*7,.02f,
                    alpha(i==category?0xB681D4:0x596577,amount));
                for(int i=0;i<3;i++) {
                    rect(matrix,vertices,-20,-8+i*9,32,-1+i*9,.02f,alpha(0x303B4B,amount));
                    rect(matrix,vertices,-17,-6+i*9,17-i*4,-4+i*9,.03f,alpha(0x93A0B2,amount*.7f));
                }
            });
        } finally {pose.popPose();}
    }
    private static int alpha(int rgb,float opacity) {return Math.round(Math.clamp(opacity,0f,1f)*255)<<24|rgb;}
    private static void rect(PoseStack.Pose matrix,VertexConsumer v,float x1,float y1,float x2,float y2,float z,int color) {
        v.addVertex(matrix,x1,y1,z).setColor(color).setLight(0xF000F0);
        v.addVertex(matrix,x1,y2,z).setColor(color).setLight(0xF000F0);
        v.addVertex(matrix,x2,y2,z).setColor(color).setLight(0xF000F0);
        v.addVertex(matrix,x2,y1,z).setColor(color).setLight(0xF000F0);
        // DEBUG_QUADS is two-sided; submit each quad only once.
    }
}
