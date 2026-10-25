package com.wildfire.common.networking;

import com.wildfire.common.WildfireGender;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import com.wildfire.common.networking.packets.sync.ClientboundSyncPacket;
import com.wildfire.common.networking.packets.sync.ServerboundSyncPacket;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public final class WildfireSync {
   public static final Marker MARKER = MarkerFactory.getMarker("SYNC");

   private WildfireSync() {
      throw new UnsupportedOperationException();
   }

   public static void sendToAllClients(ServerPlayer toSync, PlayerConfigHolder playerConfig) {
      int sent = 0;

      for (ServerPlayer player : WildfireNetworking.INSTANCE.playersTracking(toSync)) {
         if (!player.equals(toSync) && WildfireNetworking.INSTANCE.canSyncToPlayer(player)) {
            sent++;
            WildfireNetworking.INSTANCE.syncToPlayer(player, new ClientboundSyncPacket(playerConfig));
         }
      }

      if (sent > 0) {
         WildfireGender.LOGGER.debug(MARKER, "Sent sync packet for {} to {} connected player(s)", toSync, sent);
      }
   }

   public static void sendToClient(ServerPlayer sendTo, PlayerConfigHolder toSync) {
      if (WildfireNetworking.INSTANCE.canSyncToPlayer(sendTo)) {
         WildfireGender.LOGGER.debug(MARKER, "Sending profile for {} to other player {}", toSync.uuid, sendTo.getUUID());
         WildfireNetworking.INSTANCE.syncToPlayer(sendTo, new ClientboundSyncPacket(toSync));
      }
   }

   public static void sendToServer(Connection connection, PlayerConfigHolder plr) {
      if (plr.needsSync && WildfireNetworking.INSTANCE.canSyncToServer(connection)) {
         WildfireGender.LOGGER.debug(MARKER, "Sending player data to server");
         WildfireNetworking.INSTANCE.syncToServer(new ServerboundSyncPacket(plr.config()));
         plr.needsSync = false;
      }
   }
}
