package com.wildfire.common.networking;

import com.wildfire.common.WildfireHelper;
import com.wildfire.common.networking.packets.sync.ClientboundSyncPacket;
import com.wildfire.common.networking.packets.sync.ServerboundSyncPacket;
import java.util.Collection;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public interface WildfireNetworking {
   WildfireNetworking INSTANCE = WildfireHelper.getService(WildfireNetworking.class);

   boolean canSyncToPlayer(ServerPlayer var1);

   boolean canSyncToServer(Connection var1);

   boolean versionMatches(Connection var1);

   void syncToPlayer(ServerPlayer var1, ClientboundSyncPacket var2);

   void syncToServer(ServerboundSyncPacket var1);

   Collection<ServerPlayer> playersTracking(Entity var1);
}
