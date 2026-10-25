package dev.aeltorx.inflow.client;

import com.mojang.math.Transformation;
import dev.aeltorx.inflow.client.mixin.BlockDisplayAccess;
import dev.aeltorx.inflow.client.mixin.DisplayAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Transient client-only display entities. No command, packet, damage, or real explosion. */
public final class LocalModerationAnimation {
    public enum Style {
        NONE("Без эффекта"), VAN("Бусик"), MINIONS("Миньоны"), DRONE("Шахед"),
        SPIDER("Человек-паук"), DYNAMITE("Динамит"), WARDEN("Варден"),
        TANK("Миньон на танке"), NUKE("Ядерный эффект"),
        UFO("Похищение НЛО"), PORTAL("Портал"), ANVIL("Наковальня"),
        PATRICK("Патрик и дом-камень"), SHREK("Шрек"), LUIGI("Пылесос Луиджи"), POKEBALL("Покебол"),
        REAPER("Смерть с косой"), COURIER("Посылка"), JUDGE("Судья"), HAND("Огромная рука"), MAGICIAN("Фокусник"), FISHER("Рыбалка");
        public final String label;
        Style(String label) { this.label = label; }
    }
    private record Part(Display.BlockDisplay entity, Vec3 offset, int group,float w,float h,float d) { }
    private static final List<Part> parts = new ArrayList<>();
    private static ClientLevel level;
    private static Vec3 origin;
    private static Style style = Style.NONE;
    private static long started;
    private static java.util.UUID targetId;
    private static double facing;
    private static boolean boom;
    private static net.minecraft.world.entity.monster.warden.Warden warden;
    private static boolean roared;
    private static net.minecraft.world.entity.Pose victimPose;
    private static boolean ateSoul;
    private static net.minecraft.world.entity.decoration.Mannequin visualVictim;
    private static boolean originalInvisible,effectSound;
    private static boolean hiddenVictim;
    private record Label(Display.TextDisplay entity,dev.aeltorx.inflow.common.CinematicScenes.Caption caption){}
    private static final List<Label> labels=new ArrayList<>();
    private static int cueIndex,burstIndex;
    private static int nextId = -1_900_000_000;
    private static int particleFrame = -1;
    private static int cloudFrame=-1;
    private LocalModerationAnimation() { }
    public static boolean start(ModerationService.Target target, Style selected) {
        clear();
        var mc = Minecraft.getInstance();
        if (selected == Style.NONE || mc.level == null || mc.player == null) return false;
        var victim = mc.level.getPlayerByUUID(target.id());
        if (victim == null || mc.player.distanceToSqr(victim) > 96 * 96) {
            ModerationService.notice("Локальная анимация: игрок вне загруженной области."); return false;
        }
        level = mc.level; origin = victim.position(); targetId=target.id(); facing=Math.toRadians(-victim.getYRot());
        style = selected; started = System.nanoTime(); boom=false;roared=false;ateSoul=false;effectSound=false;cueIndex=0;burstIndex=0;
        for(var caption:dev.aeltorx.inflow.common.CinematicScenes.captions(style.name(),"—",target.name())){
            var text=new Display.TextDisplay(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("text_display")),level);
            while(level.getEntity(nextId)!=null)nextId--;text.setId(nextId--);text.setNoGravity(true);
            var access=(dev.aeltorx.inflow.client.mixin.TextDisplayAccess)text;access.windowsmenu$text(net.minecraft.network.chat.Component.literal(caption.text()).withStyle(net.minecraft.ChatFormatting.BLACK));
            access.windowsmenu$background(0);access.windowsmenu$lineWidth(160);
            ((DisplayAccess)text).windowsmenu$viewRange(2);((DisplayAccess)text).windowsmenu$width(8);((DisplayAccess)text).windowsmenu$height(8);
            text.setPos(origin);text.setOldPosAndRot();level.addEntity(text);labels.add(new Label(text,caption));
        }
        if(style==Style.WARDEN) {
            var entity=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("warden"))
                .create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if(!(entity instanceof net.minecraft.world.entity.monster.warden.Warden real)){clear();return false;}
            warden=real;while(level.getEntity(nextId)!=null)nextId--;warden.setId(nextId--);
            warden.setNoAi(true);warden.setNoGravity(true);warden.setInvulnerable(true);warden.setSilent(true);
            warden.setPos(origin.x,origin.y,origin.z-3.4);warden.setOldPosAndRot();level.addEntity(warden);
        }
        for(var part:dev.aeltorx.inflow.common.SceneModel.build(style.name()))
            box(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(part.material().toLowerCase(java.util.Locale.ROOT))),
                part.x(),part.y(),part.z(),part.w(),part.h(),part.d(),part.group());
        tick(); return true;
    }
    private static void box(Block block, double x, double y, double z, float w, float h, float d, int group) {
        var entity = new Display.BlockDisplay(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace("block_display")), level);
        while (level.getEntity(nextId) != null) nextId--;
        entity.setId(nextId--); entity.setNoGravity(true); entity.setInvulnerable(true);
        ((BlockDisplayAccess) entity).windowsmenu$block(block.defaultBlockState());
        var access = (DisplayAccess) entity;
        access.windowsmenu$transform(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(w, h, d), new Quaternionf()));
        access.windowsmenu$viewRange(2); access.windowsmenu$width(8); access.windowsmenu$height(8);
        entity.setPos(origin.x + x, origin.y + y, origin.z + z); entity.setOldPosAndRot();
        level.addEntity(entity); parts.add(new Part(entity, new Vec3(x, y, z), group,w,h,d));
    }
    public static void tick() {
        if (level == null) return;
        var mc = Minecraft.getInstance();
        double t = (System.nanoTime() - started) / 1e9;
        if (mc.level != level || !ModerationService.allowed() || t > dev.aeltorx.inflow.common.SceneTimeline.length(style.name())) { clear(); return; }
        var victim=level.getPlayerByUUID(targetId);
        if(victim!=null&&visualVictim==null)origin=victim.position();
        double cos=Math.cos(facing),sin=Math.sin(facing);
        var cues=dev.aeltorx.inflow.common.CinematicScenes.cues(style.name());
        while(cueIndex<cues.size()&&t>=cues.get(cueIndex).time()){
            var cue=cues.get(cueIndex++);mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.withDefaultNamespace(cue.sound())),cue.pitch(),cue.volume()));
        }
        var bursts=dev.aeltorx.inflow.common.CinematicScenes.bursts(style.name());
        while(burstIndex<bursts.size()&&t>=bursts.get(burstIndex).time()){
            var b=bursts.get(burstIndex++);var particle=net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(b.particle()));
            if(particle instanceof net.minecraft.core.particles.SimpleParticleType simple)for(int i=0;i<b.count();i++){
                double a=i*2.39996,r=.2+(i%4)*.09;level.addParticle(simple,
                    origin.x+b.x()*cos+b.z()*sin+Math.cos(a)*r,origin.y+b.y()+i%3*.08,origin.z+b.z()*cos-b.x()*sin+Math.sin(a)*r,Math.cos(a)*.015,.025,Math.sin(a)*.015);
            }
        }
        for(var label:labels){var c=label.caption();var p=dev.aeltorx.inflow.common.SceneTimeline.pose(style.name(),c.group(),t);
            var j=dev.aeltorx.inflow.common.SceneRig.joint(new dev.aeltorx.inflow.common.SceneModel.Part("",c.x(),c.y(),c.z(),0,0,0,c.group()),style.name(),t);
            double pc=Math.cos(p.yaw()),ps=Math.sin(p.yaw()),x=j.x()*pc+j.z()*ps+p.x(),z=j.z()*pc-j.x()*ps+p.z();
            label.entity().setOldPosAndRot();label.entity().setPos(origin.x+x*cos+z*sin,origin.y+j.y()+p.y(),origin.z+z*cos-x*sin);
            label.entity().setYRot((float)Math.toDegrees(-facing-p.yaw()));label.entity().setXRot((float)Math.toDegrees(j.pitch()));
            ((DisplayAccess)label.entity()).windowsmenu$transform(new Transformation(new Vector3f(),new Quaternionf().rotateZ((float)j.roll()),new Vector3f((float)(c.scale()*p.scale())),new Quaternionf()));
        }
        if(dev.aeltorx.inflow.common.SceneTimeline.hasDouble(style.name())&&t>=1.9&&victim!=null){
            if(visualVictim==null){
                var entity=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("mannequin"))
                    .create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                if(!(entity instanceof net.minecraft.world.entity.decoration.Mannequin mannequin)){clear();return;}
                visualVictim=mannequin;
                ((dev.aeltorx.inflow.client.mixin.MannequinAccess)visualVictim).windowsmenu$hideDescription(true);
                var head=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
                head.set(net.minecraft.core.component.DataComponents.PROFILE,net.minecraft.world.item.component.ResolvableProfile.createResolved(victim.getGameProfile()));
                visualVictim.applyComponentsFromItemStack(head);
                while(level.getEntity(nextId)!=null)nextId--;visualVictim.setId(nextId--);
                visualVictim.setNoGravity(true);visualVictim.setInvulnerable(true);visualVictim.setSilent(true);
                for(var slot:java.util.List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.entity.EquipmentSlot.OFFHAND))visualVictim.setItemSlot(slot,victim.getItemBySlot(slot).copy());
                visualVictim.setPos(origin);visualVictim.setOldPosAndRot();level.addEntity(visualVictim);
                originalInvisible=victim.isInvisible();hiddenVictim=true;
            }
            victim.setInvisible(true);
            var p=dev.aeltorx.inflow.common.SceneTimeline.victim(style.name(),t);
            visualVictim.setOldPosAndRot();visualVictim.setPos(origin.x+p.x()*cos+p.z()*sin,origin.y+p.y(),origin.z+p.z()*cos-p.x()*sin);
            float yaw=(float)Math.toDegrees(-facing-p.yaw());visualVictim.setYRot(yaw);visualVictim.setYHeadRot(yaw);visualVictim.setYBodyRot(yaw);
            visualVictim.setInvisible(p.scale()<.07);
            var scale=visualVictim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);if(scale!=null)scale.setBaseValue(Math.max(.0625,p.scale()));
            visualVictim.setPose(net.minecraft.world.entity.Pose.valueOf(dev.aeltorx.inflow.common.SceneTimeline.victimPose(style.name(),t)));
            if(p.scale()<.07&&level.getEntity(visualVictim.getId())==visualVictim)level.removeEntity(visualVictim.getId(),Entity.RemovalReason.DISCARDED);
        }
        for(Part part:parts) {
            if(level.getEntity(part.entity.getId())!=part.entity)continue;
            var pose=dev.aeltorx.inflow.common.SceneTimeline.pose(style.name(),part.group,t);
            var joint=dev.aeltorx.inflow.common.SceneRig.joint(new dev.aeltorx.inflow.common.SceneModel.Part("",part.offset.x,part.offset.y,part.offset.z,part.w,part.h,part.d,part.group),style.name(),t);
            double pc=Math.cos(pose.yaw()),ps=Math.sin(pose.yaw());
            double x=joint.x()*pc+joint.z()*ps+pose.x(),z=joint.z()*pc-joint.x()*ps+pose.z();
            part.entity.setOldPosAndRot();
            part.entity.setPos(origin.x+x*cos+z*sin,origin.y+joint.y()+pose.y(),origin.z+z*cos-x*sin);
            part.entity.setYRot((float)Math.toDegrees(-facing-pose.yaw()));
            part.entity.setXRot((float)Math.toDegrees(joint.pitch()));
            ((DisplayAccess)part.entity).windowsmenu$transform(new Transformation(new Vector3f(),new Quaternionf().rotateZ((float)joint.roll()),
                new Vector3f(part.w,part.h,part.d).mul((float)pose.scale()),new Quaternionf()));
        }
        if(warden!=null) {
            var pose=dev.aeltorx.inflow.common.SceneTimeline.pose("WARDEN",0,t);
            warden.setOldPosAndRot();warden.setPos(origin.x+pose.x()*cos+pose.z()*sin,origin.y,origin.z+pose.z()*cos-pose.x()*sin);
            float yaw=(float)Math.toDegrees(-facing);warden.setYRot(yaw);warden.setYBodyRot(yaw);warden.setYHeadRot(yaw);
            if(t>=1.3&&!roared) {
                roared=true;warden.setPose(net.minecraft.world.entity.Pose.ROARING);
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.withDefaultNamespace("entity.warden.roar")),1,.8f));
            }
            if(t>=6.3)warden.setPose(net.minecraft.world.entity.Pose.SNIFFING);
            else if(t>=4.4){warden.setPose(net.minecraft.world.entity.Pose.STANDING);warden.roarAnimationState.stop();}
            if(t>=3&&victim!=null){if(victimPose==null)victimPose=victim.getPose();victim.setPose(net.minecraft.world.entity.Pose.SWIMMING);}
            if(t>=3&&t<6.3&&cloudFrame!=(int)(t*20)){
                cloudFrame=(int)(t*20);var soul=dev.aeltorx.inflow.common.SceneTimeline.pose("WARDEN",90,t);
                level.addParticle(ParticleTypes.SOUL,origin.x+soul.z()*sin,origin.y+soul.y()+.45,origin.z+soul.z()*cos,0,.015,0);
            }
            if(t>=6.3&&!ateSoul){ateSoul=true;
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.withDefaultNamespace("entity.generic.eat")),.65f,.8f));
            }
        }
        double impact=dev.aeltorx.inflow.common.SceneTimeline.banAt(style.name());
        if(!effectSound&&((style==Style.UFO&&t>=1.6)||(style==Style.PORTAL&&t>=1.3)||(style==Style.ANVIL&&t>=2.4))){
            effectSound=true;String sound=style==Style.UFO?"block.beacon.activate":style==Style.PORTAL?"block.portal.trigger":"block.anvil.land";
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.withDefaultNamespace(sound)),style==Style.ANVIL?.8f:1.2f,.8f));
        }
        if(cloudFrame!=(int)(t*20)){
            if(style==Style.UFO&&t>=1.6&&t<6.2){cloudFrame=(int)(t*20);for(int i=0;i<3;i++){double a=t*3+i*2.1;
                level.addParticle(ParticleTypes.END_ROD,origin.x+Math.cos(a)*.55,origin.y+.4+i*1.3,origin.z+Math.sin(a)*.55,0,.035,0);}}
            if(style==Style.PORTAL&&t<5.8){cloudFrame=(int)(t*20);for(int i=0;i<6;i++){double a=t*4+i*Math.PI/3;
                level.addParticle(ParticleTypes.PORTAL,origin.x-2.6*sin+Math.cos(a)*.7,origin.y+1.3+Math.sin(a),origin.z-2.6*cos,0,0,.03);}}
            if(style==Style.ANVIL&&t>=2.4&&t<3.1){cloudFrame=(int)(t*20);for(int i=0;i<3;i++)
                level.addParticle(ParticleTypes.POOF,origin.x+Math.sin(t*15+i)*.5,origin.y+.15,origin.z+Math.cos(t*15+i)*.5,0,.015,0);}
        }
        if(!boom&&style==Style.MINIONS&&t>=impact){boom=true;
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.withDefaultNamespace("entity.player.attack.knockback")),1,.6f));
            for(int i=0;i<8;i++)level.addParticle(ParticleTypes.CRIT,origin.x,origin.y+1.5,origin.z,0,.02,0);
        }
        if(!boom && t>=impact && dev.aeltorx.inflow.common.SceneTimeline.explosion(style.name())) {
            boom=true;
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.withDefaultNamespace("entity.generic.explode")),
                style==Style.NUKE?.55f:1f,.7f));
            level.addParticle(ParticleTypes.EXPLOSION,origin.x,origin.y+.8,origin.z,0,0,0);
            for(int i=0;i<60;i++) {
                double angle=i*2.39996,r=.6+(i%7)*.18;
                level.addParticle(i%3==0?ParticleTypes.FLAME:ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    origin.x,origin.y+.8,origin.z,Math.cos(angle)*r*.18,.05+(i%5)*.04,Math.sin(angle)*r*.18);
            }
        }
        if(boom && t-impact<1.2 && particleFrame!=(int)(t*20)) {
            particleFrame=(int)(t*20);double r=(t-impact)*4;
            for(int i=0;i<12;i++){double a=i*Math.PI/6;
                level.addParticle(ParticleTypes.POOF,origin.x+Math.cos(a)*r,origin.y+.15,origin.z+Math.sin(a)*r,0,.015,0);}
        }
        if(style==Style.NUKE&&boom&&cloudFrame!=(int)(t*20)) {
            cloudFrame=(int)(t*20);double age=t-impact;
            for(int i=0;i<8;i++) {
                double a=t*8+i*2.39996,r=Math.sqrt((i+.5)/8)*Math.min(3,age*2);
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,origin.x+Math.cos(a)*r,origin.y+1+age*1.4,
                    origin.z+Math.sin(a)*r,0,.055,0);
                if(i<2)level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,origin.x,origin.y+.5+i*age,
                    origin.z,0,.08,0);
            }
        }
    }
    public static void clear() {
        if(level!=null)for(var label:labels)if(level.getEntity(label.entity().getId())==label.entity())level.removeEntity(label.entity().getId(),Entity.RemovalReason.DISCARDED);
        labels.clear();cueIndex=0;burstIndex=0;
        if(level!=null&&hiddenVictim){var target=level.getPlayerByUUID(targetId);if(target!=null)target.setInvisible(originalInvisible);}
        hiddenVictim=false;
        if(level!=null&&visualVictim!=null&&level.getEntity(visualVictim.getId())==visualVictim)level.removeEntity(visualVictim.getId(),Entity.RemovalReason.DISCARDED);
        visualVictim=null;
        if(level!=null&&victimPose!=null){var target=level.getPlayerByUUID(targetId);if(target!=null)target.setPose(victimPose);}
        victimPose=null;
        if(level!=null&&warden!=null&&level.getEntity(warden.getId())==warden)
            level.removeEntity(warden.getId(),Entity.RemovalReason.DISCARDED);
        warden=null;
        if (level != null) for (Part part : parts)
            if (level.getEntity(part.entity.getId()) == part.entity)
                level.removeEntity(part.entity.getId(), Entity.RemovalReason.DISCARDED);
        parts.clear(); level = null; origin = null; style = Style.NONE; particleFrame = -1;cloudFrame=-1;
    }
}
