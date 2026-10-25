package com.wildfire.common.networking.packets.hello;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public sealed interface SyncHelloPacket extends CustomPacketPayload permits ClientboundSyncHelloPacket, ServerboundSyncHelloPacket {
   int VERSION = 2;

   int version();
}
