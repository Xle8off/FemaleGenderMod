package com.wildfire.common.networking.packets.hello;

import com.wildfire.common.WildfireGender;
import com.wildfire.common.networking.WildfireSync;
import io.netty.buffer.ByteBuf;
import java.util.function.IntConsumer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record ServerboundSyncHelloPacket(int version) implements SyncHelloPacket {
   public static final Type<ServerboundSyncHelloPacket> TYPE = WildfireGender.serverBoundPacket("hello");
   public static final StreamCodec<ByteBuf, ServerboundSyncHelloPacket> STREAM_CODEC = ByteBufCodecs.VAR_INT
      .map(ServerboundSyncHelloPacket::new, SyncHelloPacket::version);

   public ServerboundSyncHelloPacket() {
      this(2);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public void handle(IntConsumer versionSetter) {
      versionSetter.accept(this.version);
      if (this.version == 2) {
         WildfireGender.LOGGER.info(WildfireSync.MARKER, "Received hello response from client with protocol version {}", this.version);
      } else {
         WildfireGender.LOGGER
            .warn(WildfireSync.MARKER, "Client reported an unsupported sync protocol version! Client supports version {} but we expect {}", this.version, 2);
      }
   }
}
