package com.wildfire.common.networking.packets.hello;

import com.wildfire.common.WildfireGender;
import com.wildfire.common.networking.WildfireSync;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record ClientboundSyncHelloPacket(int version) implements SyncHelloPacket {
   public static final Type<ClientboundSyncHelloPacket> TYPE = WildfireGender.clientBoundPacket("hello");
   public static final StreamCodec<ByteBuf, ClientboundSyncHelloPacket> STREAM_CODEC = ByteBufCodecs.VAR_INT
      .map(ClientboundSyncHelloPacket::new, SyncHelloPacket::version);

   public ClientboundSyncHelloPacket() {
      this(2);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public void handle(Consumer<ServerboundSyncHelloPacket> replySender, IntConsumer versionSetter) {
      replySender.accept(new ServerboundSyncHelloPacket());
      versionSetter.accept(this.version);
      if (this.version == 2) {
         WildfireGender.LOGGER.info(WildfireSync.MARKER, "Received hello packet from server with protocol version {}", this.version);
      } else {
         WildfireGender.LOGGER
            .warn(WildfireSync.MARKER, "Server is using an unsupported sync protocol version! Server supports version {} but we expect {}", this.version, 2);
      }
   }
}
