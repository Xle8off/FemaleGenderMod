package dev.aeltorx.inflow.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import com.mojang.blaze3d.platform.InputConstants;

/** Manual camera flight; only the server can authorize contact and execute a ban. */
public final class DronePilotScreen extends Screen {
    private final ModerationService.Target target;
    private final Set<Integer> keys=new HashSet<>();
    private Entity oldCamera,camera;private double x,y,z;private float yaw,pitch;
    private net.minecraft.client.CameraType oldCameraType;
    private int ticks;private boolean closed;private final Object connection;
    private double mouseX=Double.NaN,mouseY;
    private boolean serverEnded;
    public void serverEnded(){serverEnded=true;onClose();}
    public DronePilotScreen(ModerationService.Target target) {
        super(Component.literal("Дрон — ручное управление"));this.target=target;
        var mc=net.minecraft.client.Minecraft.getInstance();connection=mc.getConnection();
    }
    @Override protected void init() {
        if(camera!=null)return;
        var eye=minecraft.player.getEyePosition();
        x=eye.x;y=eye.y+.25;z=eye.z;yaw=minecraft.player.getYRot();pitch=Math.clamp(minecraft.player.getXRot(),-80,80);
        oldCamera=minecraft.getCameraEntity();
        oldCameraType=minecraft.options.getCameraType();minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        camera=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("armor_stand"))
            .create(minecraft.level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if(camera==null){onClose();return;}
        camera.setInvisible(true);camera.setPos(x,y-camera.getEyeHeight(),z);camera.setYRot(yaw);camera.setXRot(pitch);
        camera.setOldPosAndRot();minecraft.setCameraEntity(camera);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick() {
        if(camera==null)return;
        if(!ModerationService.allowed()||minecraft.getConnection()!=connection||!ModerationService.online(target)||ticks++>1200){onClose();return;}
        if(keys.contains(InputConstants.KEY_LEFT))yaw-=3;
        if(keys.contains(InputConstants.KEY_RIGHT))yaw+=3;
        if(keys.contains(InputConstants.KEY_UP))pitch-=2;
        if(keys.contains(InputConstants.KEY_DOWN))pitch+=2;
        pitch=Math.clamp(pitch,-80,80);
        double a=Math.toRadians(yaw),p=Math.toRadians(pitch),speed=.2;
        int forward=(keys.contains(InputConstants.KEY_W)?1:0)-(keys.contains(InputConstants.KEY_S)?1:0);
        int strafe=(keys.contains(InputConstants.KEY_D)?1:0)-(keys.contains(InputConstants.KEY_A)?1:0);
        double norm=Math.max(1,Math.hypot(forward,strafe));
        x+=(-Math.sin(a)*forward*Math.cos(p)-Math.cos(a)*strafe)*speed/norm;
        z+=(Math.cos(a)*forward*Math.cos(p)-Math.sin(a)*strafe)*speed/norm;
        y+=-Math.sin(p)*forward*speed/norm;
        if(keys.contains(InputConstants.KEY_SPACE))y+=speed;
        if(keys.contains(InputConstants.KEY_LSHIFT))y-=speed;
        camera.setOldPosAndRot();camera.setPos(x,y-camera.getEyeHeight(),z);camera.setYRot(yaw);camera.setXRot(pitch);
        if(ticks%3==0)minecraft.getConnection().send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
            new DroneMovePayload(x,y,z,(yaw%360+540)%360-180,pitch)));
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(event.key()==InputConstants.KEY_ESCAPE){onClose();return true;}
        keys.add(event.key());return true;
    }
    @Override public boolean keyReleased(KeyEvent event){keys.remove(event.key());return true;}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){if(event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT){setDragging(true);return true;}return false;}
    @Override public boolean mouseReleased(MouseButtonEvent event){setDragging(false);return true;}
    @Override public void mouseMoved(double mx,double my) {
        if(Double.isFinite(mouseX)){yaw+=(mx-mouseX)*.22f;pitch=Math.clamp(pitch+(float)(my-mouseY)*.22f,-80,80);}
        mouseX=mx;mouseY=my;
        if(mx<width*.2||mx>width*.8||my<height*.2||my>height*.8){
            mouseX=width/2.0;mouseY=height/2.0;
            var window=minecraft.getWindow();
            org.lwjgl.sdl.SDLMouse.SDL_WarpMouseInWindow(window.handle(),window.getScreenWidth()/2f,window.getScreenHeight()/2f);
        }
        if(camera!=null){camera.setYRot(yaw);camera.setXRot(pitch);camera.yRotO=yaw;camera.xRotO=pitch;}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(width/2-5,height/2,width/2+5,height/2+1,0xCCFFFFFF);
        g.fill(width/2,height/2-5,width/2+1,height/2+5,0xCCFFFFFF);
        g.centeredText(font,"Дрон → "+target.name(),width/2,12,0xFFFFFFFF);
        g.centeredText(font,"WASD · Space/Shift · мышь — обзор · Esc — отмена",width/2,height-24,0xFFFFFFFF);
        g.centeredText(font,"Бан — только после подтверждённого сервером попадания",width/2,height-12,0xFFB1C8FF);
    }
    @Override public void onClose(){minecraft.gui.setScreen(null);}
    @Override public void removed() {
        if(closed)return;closed=true;keys.clear();
        if(!serverEnded&&minecraft.getConnection()==connection&&ModerationService.commandAvailable("wcanim"))minecraft.getConnection().sendCommand("wcanim cancel");
        if(minecraft.getCameraEntity()==camera)minecraft.setCameraEntity(minecraft.level==null?minecraft.player:oldCamera);
        if(oldCameraType!=null)minecraft.options.setCameraType(oldCameraType);
    }
}
