package com.wildfire.common.entitydata;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.wildfire.client.ClientHelper;
import com.wildfire.client.cloud.CloudSync;
import com.wildfire.client.cloud.SyncLog;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.client.gui.screen.BaseWildfireScreen;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireLang;
import com.wildfire.common.config.Configuration;
import com.wildfire.common.config.value.ConfigValue;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus.Internal;

public class PlayerConfigHolder extends EntityConfigHolder<PlayerConfig> {
   public boolean needsSync;
   public boolean needsCloudSync;
   public PlayerConfigHolder.SyncStatus syncStatus;
   private final Configuration<PlayerConfig> cfgFile;

   public PlayerConfigHolder(UUID uuid) {
      this.cfgFile = new Configuration<>(uuid.toString(), PlayerConfig.CODEC);
      super(uuid, PlayerConfig.createDefault());
      this.syncStatus = PlayerConfigHolder.SyncStatus.UNKNOWN;
   }

   @Override
   public void readFromStack(ItemStack chestplate) {
   }

   @Override
   public boolean hasJacketLayer() {
      throw new UnsupportedOperationException("PlayerConfig does not support #hasJacketLayer(); use Player#isModelPartShown instead");
   }

   public PlayerConfigHolder.SyncStatus getSyncStatus() {
      return this.syncStatus;
   }

   @Internal
   public void attemptCloudSync() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && this.uuid.equals(client.player.getUUID())) {
         if (this.needsCloudSync) {
            if (!(client.gui.screen() instanceof BaseWildfireScreen)) {
               if (ClientConfig.config().cloudSync().automatic().get()) {
                  if (!CloudSync.syncOnCooldown()) {
                     CompletableFuture.runAsync(() -> {
                        try {
                           CloudSync.sync(this).join();
                           WildfireGender.LOGGER.info("Synced player data to the cloud");
                        } catch (Exception e) {
                           WildfireGender.LOGGER.error("Failed to sync player data", e);
                           SyncLog.add(WildfireLang.SYNC_LOG_FAILED);
                        }
                     });
                     this.needsCloudSync = false;
                  }
               }
            }
         }
      }
   }

   public boolean hasLocalConfig() {
      return this.cfgFile.exists();
   }

   public void loadFromDisk(boolean markForSync) {
      this.syncStatus = PlayerConfigHolder.SyncStatus.CACHED;
      this.config = this.cfgFile.load();
      if (markForSync) {
         this.needsSync = true;
      }
   }

   public void save() {
      this.cfgFile.save(this.config);
      this.needsSync = true;
      this.needsCloudSync = true;
   }

   public JsonElement toJson() {
      return (JsonElement)PlayerConfig.CODEC.encodeStart(JsonOps.INSTANCE, this.config).resultOrPartial().orElseGet(JsonObject::new);
   }

   public void updateFromJson(JsonElement serialized) {
      PlayerConfig.CODEC.parse(JsonOps.INSTANCE, serialized).ifSuccess(parsed -> this.config = parsed);
      this.syncStatus = PlayerConfigHolder.SyncStatus.SYNCED;
   }

   public void updateFromPacket(PlayerConfig config, boolean fromServer) {
      this.config = config;
      if (fromServer) {
         this.syncStatus = PlayerConfigHolder.SyncStatus.SYNCED;
      }
   }

   @Override
   public List<String> getDebugInfo() {
      List<String> lines = super.getDebugInfo();
      lines.add(1, "Sync status: " + this.getSyncStatus());
      lines.add("Female hurt sounds: " + this.sounds().hurt());
      lines.add("Show in armor: " + this.showBreastsInArmor());
      return lines;
   }

   public void tryPlayHurtSound(Player player) {
      if (this.sounds().hurt().get()) {
         Holder<SoundEvent> hurtSound = ClientHelper.INSTANCE.hurtSound(this.gender().get());
         if (hurtSound != null) {
            float pitchVariation = (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.2F;
            player.playSound((SoundEvent)hurtSound.value(), 1.0F, pitchVariation + this.sounds().voicePitch().get());
         }
      }
   }

   public final Sounds sounds() {
      return this.config.sounds;
   }

   public final ConfigValue<Boolean> showBreastsInArmor() {
      return this.config.showBreastsInArmor;
   }

   public enum SyncStatus {
      CACHED,
      SYNCED,
      UNKNOWN;
   }
}
