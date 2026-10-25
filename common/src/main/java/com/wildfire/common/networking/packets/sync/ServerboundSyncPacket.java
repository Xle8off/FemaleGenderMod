package com.wildfire.common.networking.packets.sync;

import com.wildfire.common.WildfireGender;
import com.wildfire.common.entitydata.PlayerConfig;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import com.wildfire.common.networking.WildfireSync;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public record ServerboundSyncPacket(PlayerConfig config) implements CustomPacketPayload {
   public static final Type<ServerboundSyncPacket> TYPE = WildfireGender.serverBoundPacket("sync");
   public static final StreamCodec<ByteBuf, ServerboundSyncPacket> STREAM_CODEC = PlayerConfig.COMPACT_STREAM_CODEC
      .map(ServerboundSyncPacket::new, ServerboundSyncPacket::config);

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public void handle(MinecraftServer server, ServerPlayer player) {
      WildfireGender.LOGGER.debug(WildfireSync.MARKER, "Received player data from player {}", player);
      PlayerConfigHolder plr = WildfireGender.getOrAddPlayerById(player.getUUID());
      if (!server.isSingleplayerOwner(player.nameAndId())) {
         plr.updateFromPacket(this.config, false);
      }

      WildfireSync.sendToAllClients(player, plr);
   }
}
