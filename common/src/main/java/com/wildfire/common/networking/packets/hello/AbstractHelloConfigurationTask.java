package com.wildfire.common.networking.packets.hello;

import com.wildfire.common.WildfireGender;
import com.wildfire.common.networking.WildfireSync;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ConfigurationTask.Type;

public abstract class AbstractHelloConfigurationTask implements ConfigurationTask {
   private static final Identifier ID = WildfireGender.id("hello");
   public static final Type TYPE = new Type(ID.toString());

   protected ClientboundSyncHelloPacket createPacket() {
      WildfireGender.LOGGER.debug(WildfireSync.MARKER, "Sending hello packet to client");
      return new ClientboundSyncHelloPacket();
   }

   public final Type type() {
      return TYPE;
   }
}
