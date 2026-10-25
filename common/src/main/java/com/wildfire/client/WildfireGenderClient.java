package com.wildfire.client;

import com.google.gson.JsonObject;
import com.wildfire.client.appearance.AppearanceState;
import com.wildfire.client.cloud.CloudSync;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.client.contributors.Contributors;
import com.wildfire.common.LoaderAgnostics;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class WildfireGenderClient {
   private static final Executor LOAD_EXECUTOR = Util.ioPool().forName("female_gender_mod$loadPlayerData");

   public static void tryMigrate() {
      tryMigrate("WildfireGender", "FemaleGenderMod");
      tryMigrate("wildfire_gender.json", "female_gender_mod.json");
   }

   private static void tryMigrate(String oldPath, String newPath) {
      Path oldFile = LoaderAgnostics.INSTANCE.getConfigDir().resolve(oldPath);
      Path newFile = LoaderAgnostics.INSTANCE.getConfigDir().resolve(newPath);
      if (Files.notExists(oldFile)) {
         WildfireGender.LOGGER.debug("{} doesn't exist, nothing to migrate", oldPath);
      } else if (Files.exists(oldFile) && Files.exists(newFile)) {
         WildfireGender.LOGGER.warn("Cannot migrate {} to {} as both exist", oldPath, oldPath);
      } else {
         try {
            Files.move(oldFile, newFile);
            WildfireGender.LOGGER.info("Migrated {} to '{}'", oldPath, newFile);
         } catch (IOException e) {
            WildfireGender.LOGGER.error("Failed to move {} to {}", new Object[]{oldPath, newFile, e});
         }
      }
   }

   public static CompletableFuture<@Nullable PlayerConfigHolder> loadGenderInfo(UUID uuid, boolean markForSync, boolean bypassQueue) {
      PlayerConfigHolder cache = WildfireGender.getPlayerById(uuid);
      return cache == null ? CompletableFuture.completedFuture(null) : loadGenderInfo(cache, markForSync, bypassQueue);
   }

   public static CompletableFuture<PlayerConfigHolder> loadGenderInfo(PlayerConfigHolder player, boolean markForSync, boolean bypassQueue) {
      return AppearanceState.isActive() ? CompletableFuture.completedFuture(player) : CompletableFuture.supplyAsync(() -> {
         UUID uuid = player.uuid;
         if (player.hasLocalConfig()) {
            player.loadFromDisk(markForSync);
         } else if (player.syncStatus == PlayerConfigHolder.SyncStatus.UNKNOWN) {
            JsonObject data;
            try {
               CompletableFuture<JsonObject> future = bypassQueue ? CloudSync.getProfile(uuid) : CloudSync.queueFetch(uuid);
               data = future.join();
            } catch (Exception e) {
               WildfireGender.LOGGER.error("Failed to fetch profile from sync server", e);
               throw e;
            }

            if (data != null && player.syncStatus == PlayerConfigHolder.SyncStatus.UNKNOWN) {
               player.updateFromJson(data);
               if (markForSync) {
                  player.needsSync = true;
               }
            }
         }

         return player;
      }, LOAD_EXECUTOR);
   }

   public static @Nullable Component getNametag(UUID uuid) {
      if (ClientConfig.config().hideOwnContributorTag().get()) {
         LocalPlayer clientPlayer = Minecraft.getInstance().player;
         if (clientPlayer != null && uuid.equals(clientPlayer.getUUID())) {
            return null;
         }
      }

      return Contributors.getNametag(uuid);
   }
}
