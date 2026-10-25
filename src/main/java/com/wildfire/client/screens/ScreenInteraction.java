package com.wildfire.client.screens;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import net.springplus.screens.*;

/** Local affordances use cached permissions; opening always waits for a fresh server grant. */
public final class ScreenInteraction {
 public static final double RANGE=128;
 private static UUID visibleId;private static long visibleAt,retryAt;private static Object autoConnection;private static boolean askedForEngine;
 private ScreenInteraction() {}
 public static List<ScreenProtocol.Screen> nearby(){
  var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return List.of();
  return ScreenClient.screens(mc.level).stream().filter(s->s.canUse()&&s.geometry().distanceSquared(mc.player.getX(),mc.player.getY(),mc.player.getZ())<=RANGE*RANGE)
   .sorted(Comparator.comparingDouble(s->s.geometry().distanceSquared(mc.player.getX(),mc.player.getY(),mc.player.getZ()))).toList();
 }
 public static void openNearest(Screen parent){var screens=nearby();if(!screens.isEmpty())open(screens.getFirst().id(),parent);}
 public static void open(UUID id,Screen parent){
  var mc=Minecraft.getInstance();if(nearby().stream().noneMatch(s->s.id().equals(id)))return;
  ScreenClient.requestOpen(id,approved->{
   if(mc.gui.screen()!=parent||nearby().stream().noneMatch(s->s.id().equals(approved)))return;
   String media=ScreenClient.mediaUrl(approved);if(media.isEmpty()){mc.gui.chatListener().handleSystemMessage(Component.literal("Общий проигрыватель не настроен на сервере. Проверьте актуальность CoreLite и раздел cinema."),false);return;}
   BrowserRuntime.select(approved);if(BrowserRuntime.running())BrowserRuntime.navigate(media);mc.gui.setScreen(new BrowserScreen(parent));
  });
 }
 public static void mediaOffer(ScreenProtocol.Snapshot offer){
  if(!ScreenQuality.enabled())return;
  var mc=Minecraft.getInstance();if(mc.player==null||offer.opened()==null)return;
  var target=offer.screens().stream().filter(s->s.id().equals(offer.opened())).findFirst();if(target.isEmpty()||target.get().geometry().distanceSquared(mc.player.getX(),mc.player.getY(),mc.player.getZ())>RANGE*RANGE)return;
  try{BrowserEngine.webUri(offer.mediaUrl());}catch(RuntimeException invalid){return;}
  if(offer.mediaAction()==2){if(offer.opened().equals(BrowserRuntime.selected()))BrowserRuntime.renewMedia(offer.mediaUrl());return;}
  var parent=mc.gui.screen();mc.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes->{mc.gui.setScreen(parent);if(yes){BrowserRuntime.select(offer.opened());mc.gui.setScreen(new BrowserScreen(parent));}},Component.literal("Открыть проигрыватель экрана?"),Component.literal("Экран можно смотреть прямо в мире. Это меню нужно только для управления просмотром.")));
 }
 public static void visible(UUID id){
  visibleId=id;visibleAt=System.nanoTime();
 }
 public static void tickAutoView(){
  if(!ScreenQuality.enabled())return;
  UUID candidate=System.nanoTime()-visibleAt<500_000_000L?visibleId:null;
  var game=Minecraft.getInstance();
  if(candidate==null&&game.player!=null&&game.level!=null)candidate=ScreenClient.screens(game.level).stream().filter(s->s.mediaActive()&&ScreenAudioMath.distance(s.geometry(),game.player.getX(),game.player.getY(),game.player.getZ())<48).min(Comparator.comparingDouble(s->ScreenAudioMath.distance(s.geometry(),game.player.getX(),game.player.getY(),game.player.getZ()))).map(ScreenProtocol.Screen::id).orElse(null);
  final UUID id=candidate;
  var mc=Minecraft.getInstance();if(autoConnection!=mc.getConnection()){autoConnection=mc.getConnection();askedForEngine=false;retryAt=0;}
  if(id==null||!BrowserEngine.supported()||mc.gui.screen()!=null||BrowserRuntime.busy())return;
  if(id.equals(BrowserRuntime.selected())&&BrowserRuntime.running())return;
  if(System.currentTimeMillis()<retryAt)return;retryAt=System.currentTimeMillis()+5000;
  if(!BrowserEngine.installed(mc.gameDirectory.toPath())){
   if(askedForEngine)return;askedForEngine=true;
   mc.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes->{mc.gui.setScreen(null);if(yes)watchVisible(id,true);},Component.literal("Загрузить движок игровых экранов?"),Component.literal("Один раз потребуется скачать Chromium, около 151 МБ. После установки видимые экраны в пределах 8 чанков будут подключаться автоматически.")));return;
  }
  watchVisible(id,false);
 }
 private static void watchVisible(UUID id,boolean afterConsent){
  var mc=Minecraft.getInstance();ScreenClient.requestWatch(id,approved->{
   if(mc.level==null||mc.player==null||mc.gui.screen()!=null)return;
   boolean visible=approved.equals(visibleId)&&System.nanoTime()-visibleAt<=500_000_000L;
   boolean audible=ScreenClient.screens(mc.level).stream().anyMatch(s->s.id().equals(approved)&&s.mediaActive()&&ScreenAudioMath.distance(s.geometry(),mc.player.getX(),mc.player.getY(),mc.player.getZ())<48);
   if(!afterConsent&&!visible&&!audible)return;
   String url=ScreenClient.mediaUrl(approved);try{BrowserEngine.webUri(url);}catch(RuntimeException error){return;}
   BrowserRuntime.select(approved);BrowserRuntime.start(mc.gameDirectory.toPath(),url);
  });
 }
 public static boolean use(BlockHitResult hit){
  var mc=Minecraft.getInstance();if(mc.player==null||mc.gui.screen()!=null)return false;
  var pos=hit.getBlockPos();var face=ScreenGeometry.Face.valueOf(hit.getDirection().name());
  var target=ScreenClient.screens(mc.level).stream().filter(s->s.geometry().contains(pos.getX(),pos.getY(),pos.getZ(),face)).findFirst();
  if(target.isEmpty())return false;
  if(target.get().canUse())open(target.get().id(),null);
  else if(target.get().mediaActive())ScreenClient.requestWatch(target.get().id(),approved->{BrowserRuntime.select(approved);mc.gui.setScreen(new BrowserScreen(null));});
  else mc.gui.chatListener().handleSystemMessage(Component.literal("Нет права на управление этим экраном"),true);
  return true;
 }
}
