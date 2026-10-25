package com.wildfire.client.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildfire.common.config.enums.SyncVerbosity;
import com.wildfire.common.config.value.ConfigKey;
import com.wildfire.common.config.value.ConfigValue;
import net.minecraft.network.codec.ByteBufCodecs;

public record CloudSyncConfig(ConfigValue<Boolean> enabled, ConfigValue<Boolean> automatic, ConfigValue<String> server, ConfigValue<SyncVerbosity> logVerbosity) {
   public static final ConfigKey<Boolean> CLOUD_SYNC_ENABLED = ConfigKey.DEFAULT_FALSE;
   public static final ConfigKey<Boolean> AUTOMATIC_CLOUD_SYNC = ConfigKey.DEFAULT_FALSE;
   public static final ConfigKey<String> CLOUD_SERVER = new ConfigKey<>("", Codec.STRING, ByteBufCodecs.STRING_UTF8);
   public static final ConfigKey<SyncVerbosity> SYNC_VERBOSITY = new ConfigKey<>(
      SyncVerbosity.DEFAULT, SyncVerbosity.CODEC_OR_LEGACY, SyncVerbosity.STREAM_CODEC
   );
   public static final MapCodec<CloudSyncConfig> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            CLOUD_SYNC_ENABLED.codecOrDefault("cloud_sync").forGetter(config -> config.enabled.get()),
            AUTOMATIC_CLOUD_SYNC.codecOrDefault("sync_player_data").forGetter(config -> config.automatic.get()),
            CLOUD_SERVER.codecOrDefault("cloud_server").forGetter(config -> config.server.get()),
            SYNC_VERBOSITY.codecOrDefault("sync_log_verbosity").forGetter(config -> config.logVerbosity.get())
         )
         .apply(instance, CloudSyncConfig::new)
   );

   public CloudSyncConfig(boolean enabled, boolean automatic, String server, SyncVerbosity logVerbosity) {
      this(
         CLOUD_SYNC_ENABLED.createValueHandler(enabled),
         AUTOMATIC_CLOUD_SYNC.createValueHandler(automatic),
         CLOUD_SERVER.createValueHandler(server),
         SYNC_VERBOSITY.createValueHandler(logVerbosity)
      );
   }
}
