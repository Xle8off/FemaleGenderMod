package dev.aeltorx.inflow.client;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/** Native NeoForge bootstrap. Server permissions remain authoritative. */
public final class InFlowClient {
 private static boolean initialized;
 private static final KeyMapping WHEEL=new KeyMapping("key.inflow.wheel",com.mojang.blaze3d.platform.InputConstants.KEY_F9,KeyMapping.Category.MISC);
 private static final KeyMapping PANEL=new KeyMapping("key.inflow.panel",com.mojang.blaze3d.platform.InputConstants.KEY_F10,KeyMapping.Category.MISC);
 public static void register(IEventBus bus){
  if(initialized)return;initialized=true;ModerationRules.load();
  bus.addListener(RegisterKeyMappingsEvent.class,e->{e.register(WHEEL);e.register(PANEL);});
  bus.addListener(RegisterPayloadHandlersEvent.class,e->{
   var p=e.registrar("springplus_inflow").optional();
   p.playToServer(ModerationRequestPayload.TYPE,ModerationRequestPayload.CODEC,(v,c)->{});
   p.playToServer(DroneMovePayload.TYPE,DroneMovePayload.CODEC,(v,c)->{});
   p.playToClient(ModerationReplyPayload.TYPE,ModerationReplyPayload.CODEC,(v,c)->c.enqueueWork(()->ModerationWorkspace.receivePayload(v)));
   p.playToClient(DroneStatePayload.TYPE,DroneStatePayload.CODEC,(v,c)->c.enqueueWork(()->{
    if(v.ended()&&Minecraft.getInstance().gui.screen() instanceof DronePilotScreen s)s.serverEnded();
   }));
  });
  NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,e->{
   var mc=Minecraft.getInstance();LocalModerationAnimation.tick();ModerationService.tick();ModerationWorkspace.tick();
   while(PANEL.consumeClick())InFlowApi.openPanel(mc.gui.screen());
   while(WHEEL.consumeClick()){
    if(ModerationService.cancelPending())ModerationService.notice("Отложенное наказание отменено.");
    else if(mc.gui.screen() instanceof DronePilotScreen s)s.onClose();
    else if(mc.gui.screen() instanceof ModerationWheelScreen s)s.onClose();
    else if(mc.gui.screen()==null)InFlowApi.openWheel();
   }
  });
 }
}
