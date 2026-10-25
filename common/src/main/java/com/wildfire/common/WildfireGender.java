package com.wildfire.common;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.logging.LogUtils;
import com.wildfire.client.WildfireGenderClient;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.time.Duration;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class WildfireGender {
   public static final Logger LOGGER = LogUtils.getLogger();
   public static final LoadingCache<UUID, PlayerConfigHolder> CACHE = Util.make(() -> {
      CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
      if (LoaderAgnostics.INSTANCE.onClient()) {
         builder.expireAfterAccess(Duration.ofMinutes(15L));
      }

      return builder.build(CacheLoader.<UUID,PlayerConfigHolder>from(key -> {
         PlayerConfigHolder config = new PlayerConfigHolder(key);
         if (LoaderAgnostics.INSTANCE.onClient()) {
            WildfireGenderClient.loadGenderInfo(config, true, false);
         }

         return config;
      }));
   });

   public static @Nullable PlayerConfigHolder getPlayerById(UUID id) {
      return (PlayerConfigHolder)CACHE.getIfPresent(id);
   }

   public static PlayerConfigHolder getOrAddPlayerById(UUID id) {
      return (PlayerConfigHolder)CACHE.getUnchecked(id);
   }

   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("female_gender_mod", path);
   }

   public static <T extends CustomPacketPayload> Type<T> clientBoundPacket(String path) {
      return packet("clientbound/" + path);
   }

   public static <T extends CustomPacketPayload> Type<T> serverBoundPacket(String path) {
      return packet("serverbound/" + path);
   }

   private static <T extends CustomPacketPayload> Type<T> packet(String path) {
      return new Type(id(path));
   }
}
