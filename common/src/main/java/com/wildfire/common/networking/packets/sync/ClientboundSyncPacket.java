package com.wildfire.common.networking.packets.sync;

import com.wildfire.common.WildfireGender;
import com.wildfire.common.entitydata.PlayerConfig;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import com.wildfire.common.networking.WildfireSync;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record ClientboundSyncPacket(UUID uuid, PlayerConfig config) implements CustomPacketPayload {
   public static final Type<ClientboundSyncPacket> TYPE = WildfireGender.clientBoundPacket("sync");
   public static final StreamCodec<ByteBuf, ClientboundSyncPacket> STREAM_CODEC = StreamCodec.composite(
      UUIDUtil.STREAM_CODEC, p -> p.uuid, PlayerConfig.COMPACT_STREAM_CODEC, p -> p.config, ClientboundSyncPacket::new
   );

   public ClientboundSyncPacket(PlayerConfigHolder plr) {
      this(plr.uuid, plr.config());
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public void handle(UUID contextUUID) {
      if (contextUUID.equals(this.uuid)) {
         WildfireGender.LOGGER.warn(WildfireSync.MARKER, "Ignoring sync packet referring to the client player");
      } else {
         WildfireGender.LOGGER.debug(WildfireSync.MARKER, "Received player data for player {}", this.uuid);
         PlayerConfigHolder plr = WildfireGender.getOrAddPlayerById(this.uuid);
         plr.updateFromPacket(this.config, true);
      }
   }
}
