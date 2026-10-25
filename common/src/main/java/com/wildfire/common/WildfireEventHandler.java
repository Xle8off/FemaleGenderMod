package com.wildfire.common;

import com.wildfire.common.entitydata.BreastDataComponent;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import com.wildfire.common.networking.WildfireSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class WildfireEventHandler {
   private WildfireEventHandler() {
      throw new UnsupportedOperationException();
   }

   public static void playerDisconnected(Player player) {
      WildfireGender.CACHE.invalidate(player.getUUID());
   }

   public static void onBeginTracking(Entity tracked, ServerPlayer syncTo) {
      if (tracked instanceof Player toSync) {
         PlayerConfigHolder genderToSync = WildfireGender.getPlayerById(toSync.getUUID());
         if (genderToSync == null) {
            return;
         }

         WildfireSync.sendToClient(syncTo, genderToSync);
      }
   }

   public static void onEquipArmorStand(Player player, ItemStack item) {
      PlayerConfigHolder playerConfig = WildfireGender.getPlayerById(player.getUUID());
      if (playerConfig == null) {
         BreastDataComponent.removeFromStack(item);
      } else {
         BreastDataComponent component = BreastDataComponent.fromPlayer(player, playerConfig);
         if (component != null) {
            component.write(item);
         }
      }
   }
}
