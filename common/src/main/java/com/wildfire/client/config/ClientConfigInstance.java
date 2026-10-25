package com.wildfire.client.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildfire.common.config.enums.ShowPlayerListMode;
import com.wildfire.common.config.value.ConfigKey;
import com.wildfire.common.config.value.ConfigValue;

public record ClientConfigInstance(
   ConfigValue<Boolean> firstTimeLoad,
   ConfigValue<Boolean> showToast,
   ConfigValue<Boolean> armorStat,
   ConfigValue<ShowPlayerListMode> playerListMode,
   ConfigValue<Boolean> hideOwnContributorTag,
   CloudSyncConfig cloudSync,
   ConfigOverrides overrides
) {
   public static final ConfigKey<Boolean> FIRST_TIME_LOAD = ConfigKey.DEFAULT_TRUE;
   public static final ConfigKey<Boolean> SHOW_TOAST = ConfigKey.DEFAULT_TRUE;
   public static final ConfigKey<ShowPlayerListMode> PLAYER_LIST_MODE = new ConfigKey<>(
      ShowPlayerListMode.MOD_UI_ONLY, ShowPlayerListMode.CODEC_OR_LEGACY, ShowPlayerListMode.STREAM_CODEC
   );
   public static final ConfigKey<Boolean> ARMOR_STAT = ConfigKey.DEFAULT_TRUE;
   public static final ConfigKey<Boolean> HIDE_OWN_CONTRIBUTOR_TAG = ConfigKey.DEFAULT_FALSE;
   static final Codec<ClientConfigInstance> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            FIRST_TIME_LOAD.codecOrDefault("firstTimeLoad").forGetter(config -> config.firstTimeLoad.get()),
            SHOW_TOAST.codecOrDefault("showToast").forGetter(config -> config.showToast.get()),
            ARMOR_STAT.codecOrDefault("armor_stat").forGetter(config -> config.armorStat.get()),
            PLAYER_LIST_MODE.codecOrDefault("alwaysShowList").forGetter(config -> config.playerListMode.get()),
            HIDE_OWN_CONTRIBUTOR_TAG.codecOrDefault("hide_own_contributor_nametag").forGetter(config -> config.hideOwnContributorTag.get()),
            CloudSyncConfig.CODEC.forGetter(ClientConfigInstance::cloudSync),
            ConfigOverrides.CODEC.forGetter(ClientConfigInstance::overrides)
         )
         .apply(instance, ClientConfigInstance::new)
   );

   private ClientConfigInstance(
      boolean firstTimeLoad,
      boolean showToast,
      boolean armorStat,
      ShowPlayerListMode playerListMode,
      boolean hideOwnContributorTag,
      CloudSyncConfig cloudSync,
      ConfigOverrides overrides
   ) {
      this(
         FIRST_TIME_LOAD.createValueHandler(firstTimeLoad),
         SHOW_TOAST.createValueHandler(showToast),
         ARMOR_STAT.createValueHandler(armorStat),
         PLAYER_LIST_MODE.createValueHandler(playerListMode),
         HIDE_OWN_CONTRIBUTOR_TAG.createValueHandler(hideOwnContributorTag),
         cloudSync,
         overrides
      );
   }
}
