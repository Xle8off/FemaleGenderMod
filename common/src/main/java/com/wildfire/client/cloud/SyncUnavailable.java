package com.wildfire.client.cloud;

import com.wildfire.common.WildfireLang;
import net.minecraft.network.chat.Component;

public enum SyncUnavailable {
   INVALID_ACCOUNT(WildfireLang.CLOUD_UNAVAILABLE_INVALID_ACC),
   OFFLINE_SERVER(WildfireLang.CLOUD_UNAVAILABLE_OFFLINE_SERVER);

   private final WildfireLang name;

   SyncUnavailable(WildfireLang name) {
      this.name = name;
   }

   public Component text() {
      return this.name.translate();
   }
}
