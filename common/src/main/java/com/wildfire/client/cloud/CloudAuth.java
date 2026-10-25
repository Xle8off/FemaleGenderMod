package com.wildfire.client.cloud;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.minecraft.client.Minecraft;

record CloudAuth(boolean success, String token, UUID account, Instant expires) {
   static final Duration AUTH_INVALIDATION_ADJUSTMENT = Duration.ofSeconds(30L);

   boolean isExpired() {
      return this.expires.minus(AUTH_INVALIDATION_ADJUSTMENT).isBefore(Instant.now());
   }

   boolean isInvalidForClientPlayer() {
      Minecraft client = Minecraft.getInstance();
      return client.player == null || !this.account.equals(client.player.getUUID());
   }
}
