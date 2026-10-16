import com.mojang.blaze3d.vertex.PoseStack;
import com.wildfire.client.render.TorsoAttachment;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

public final class TorsoAttachmentCheck {
    public static final class FakeBone {
        public final Vector3f position=new Vector3f(),rotation=new Vector3f();
        public float bend;
    }
    public static final class FakeManager {
        public final FakeBone body=new FakeBone(),torso=new FakeBone();
        public boolean active=true;
        public int refreshes;
        public void handleAnimations(float partial,boolean tick,boolean firstPerson) {
            if(tick||firstPerson)throw new AssertionError("Bridge must not advance ticks or switch rendering pass");
            refreshes++;
        }
        public boolean isActive() { return active; }
        public FakeBone get3DTransform(String name) { return name.equals("body")?body:torso; }
    }
    public static void main(String[] args) {
        var root = new ModelPart(List.of(), Map.of());
        var body = new ModelPart(List.of(), Map.of());
        root.x = 3; root.y = -5; root.z = 7;
        root.xRot = .3f; root.yRot = -.4f; root.zRot = .2f;
        root.xScale = .8f; root.yScale = 1.2f; root.zScale = .9f;
        float[][] poses = {{0,0,0,0,0,0,1,1,1}, {8,-11,4,.9f,-.6f,.4f,1.3f,.7f,1.1f},
            {-5,16,-9,-1.4f,.8f,2.1f,.6f,1.5f,.8f}};
        for (var values : poses) {
            body.x=values[0];body.y=values[1];body.z=values[2];
            body.xRot=values[3];body.yRot=values[4];body.zRot=values[5];
            body.xScale=values[6];body.yScale=values[7];body.zScale=values[8];
            var pose = new PoseStack(); TorsoAttachment.apply(pose, root, body);
            var expected = new Matrix4f()
                .translate(root.x/16f,root.y/16f,root.z/16f).rotateZYX(root.zRot,root.yRot,root.xRot)
                .scale(root.xScale,root.yScale,root.zScale)
                .translate(body.x/16f,body.y/16f,body.z/16f).rotateZYX(body.zRot,body.yRot,body.xRot)
                .scale(body.xScale,body.yScale,body.zScale);
            if (!pose.last().pose().equals(expected, .00001f)) throw new AssertionError("Torso child pose mismatch");
            // Attachment point stays identical to body-local origin for all animated poses.
            if (pose.last().pose().transformPosition(new Vector3f()).distance(expected.transformPosition(new Vector3f())) > .00001f)
                throw new AssertionError("Attachment detached from body");
        }
        System.out.println("Torso attachment checks passed: translation, rotation, nonuniform scale, root hierarchy");
        var id = UUID.randomUUID();
        com.wildfire.client.render.TorsoMotionPhysics.clear();
        com.wildfire.client.render.TorsoMotionPhysics.sample(id, root, body, 100f);
        body.xRot += .8f;
        var impulse = com.wildfire.client.render.TorsoMotionPhysics.sample(id, root, body, 101f);
        if (Math.abs(impulse.pitch()) < .01f) throw new AssertionError("Animated torso produces no physics");
        var duplicate = com.wildfire.client.render.TorsoMotionPhysics.sample(id, root, body, 101f);
        if (!impulse.equals(duplicate)) throw new AssertionError("Two sides must not advance spring twice");
        for(int frame=102;frame<180;frame++){
            var motion=com.wildfire.client.render.TorsoMotionPhysics.sample(id,root,body,frame);
            if(Math.abs(motion.pitch())>3.001f||Math.abs(motion.roll())>3.001f)throw new AssertionError("Unbounded torso motion");
        }
        var reset=com.wildfire.client.render.TorsoMotionPhysics.sample(id,root,body,200f);
        if(reset.pitch()!=0||reset.roll()!=0)throw new AssertionError("Stale animation must reset");
        System.out.println("Torso physics checks passed: animation impulse, frame deduplication, bounded motion, stale reset");
        var turnId = new UUID(0L, 0L);
        var otherId = new UUID(0L, 1L);
        com.wildfire.client.render.IndependentBreastMotion.clear();
        com.wildfire.client.render.IndependentBreastMotion.sample(turnId,0,0,0,0,0,0,0,0);
        com.wildfire.client.render.IndependentBreastMotion.sample(otherId,0,0,0,0,0,0,0,0);
        var turn=com.wildfire.client.render.IndependentBreastMotion.sample(turnId,1,.6f,.2f,0,0,0,0,0);
        if(Math.abs(turn.left().x())<.001f || turn.left().equals(turn.right()))
            throw new AssertionError("Turn must drive independent springs");
        var sameFrame=com.wildfire.client.render.IndependentBreastMotion.sample(turnId,1,.6f,.2f,0,0,0,0,0);
        if(!sameFrame.equals(turn))throw new AssertionError("Spring advanced twice in one frame");
        var other=com.wildfire.client.render.IndependentBreastMotion.sample(otherId,1,0,0,0,0,0,0,0);
        if(other.left().x()!=0||other.right().x()!=0)throw new AssertionError("Player physics leaked");
        for(int frame=2;frame<200;frame++) {
            var m=com.wildfire.client.render.IndependentBreastMotion.sample(turnId,frame,.6f,.2f,0,0,0,0,0);
            for(var side:List.of(m.left(),m.right()))
                if(!Float.isFinite(side.x())||!Float.isFinite(side.y())||Math.abs(side.x())>1.001f||Math.abs(side.y())>1.101f)
                    throw new AssertionError("Unbounded independent motion");
            if(frame==199 && (Math.abs(m.left().x())>.001f||Math.abs(m.right().x())>.001f))
                throw new AssertionError("Independent motion did not settle");
        }
        var stale=com.wildfire.client.render.IndependentBreastMotion.sample(turnId,210,0,0,0,0,0,0,0);
        if(stale.left().x()!=0||stale.right().y()!=0)throw new AssertionError("Stale springs not reset");
        var invalid=com.wildfire.client.render.IndependentBreastMotion.sample(turnId,211,Float.NaN,0,0,0,0,0,0);
        if(invalid.left().x()!=0||invalid.right().y()!=0)throw new AssertionError("Nonfinite input not guarded");
        System.out.println("Independent motion checks passed: turn response, asymmetry, frame deduplication, player isolation, bounds, settling, reset");
        var animationId = new UUID(0L, 2L);
        com.wildfire.client.render.IndependentBreastMotion.sample(animationId,0,0,0,0,0,0,0,0,0,0);
        var translated=com.wildfire.client.render.IndependentBreastMotion.sample(animationId,1,0,0,0,0,0,0,0,1,2);
        if(Math.abs(translated.left().y())<.001f || translated.left().equals(translated.right()))
            throw new AssertionError("Animation translation must excite independent springs");
        System.out.println("Animation translation spring check passed");
        var manager=new FakeManager();
        var pose0=com.wildfire.client.render.EmoteMotionBridge.readManager(manager,.25f);
        manager.body.position.set(1f,2f,0f);
        manager.body.rotation.set(.3f,.4f,.2f);
        manager.torso.bend=.5f;
        var pose1=com.wildfire.client.render.EmoteMotionBridge.readManager(manager,.5f);
        if(pose1==null||Math.abs(pose1.pitch()-.8f)>.0001f||pose1.x()!=1f||pose1.y()!=-2f)
            throw new AssertionError("PAL body parent, bend, or Y convention missing");
        if(manager.body.position.y!=2f||manager.torso.bend!=.5f)
            throw new AssertionError("Bridge mutated animation bones");
        var remote=new UUID(0L,3L);
        com.wildfire.client.render.IndependentBreastMotion.sample(remote,0,pose0.yaw(),pose0.pitch(),pose0.roll(),0,0,0,0,pose0.x(),pose0.y());
        var remoteMotion=com.wildfire.client.render.IndependentBreastMotion.sample(remote,1,pose1.yaw(),pose1.pitch(),pose1.roll(),0,0,0,0,pose1.x(),pose1.y());
        if(Math.abs(remoteMotion.left().y())<.001f||remoteMotion.left().equals(remoteMotion.right()))
            throw new AssertionError("Parent/bend-only remote animation must excite independent springs");
        manager.active=false;
        if(com.wildfire.client.render.EmoteMotionBridge.readManager(manager,.75f)!=null)
            throw new AssertionError("Inactive animation must use vanilla fallback");
        if(com.wildfire.client.render.EmoteMotionBridge.readManager(null,0)!=null)
            throw new AssertionError("Missing PAL manager must be safe");
        System.out.println("PAL bridge contract checks passed: parent-only motion, bend, axes, read-only bones, remote springs, inactive fallback");
    }
}
