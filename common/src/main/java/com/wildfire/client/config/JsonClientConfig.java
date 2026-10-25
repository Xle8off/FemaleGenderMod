package com.wildfire.client.config;

import com.mojang.serialization.JsonOps;
import com.wildfire.common.config.Configuration;
import org.jspecify.annotations.Nullable;

class JsonClientConfig implements ClientConfig {
   private final Configuration<ClientConfigInstance> cfgFile = new Configuration<>(".", "female_gender_mod", ClientConfigInstance.CODEC);
   private ClientConfigInstance config = (ClientConfigInstance)ClientConfigInstance.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.emptyMap()).getOrThrow();
   private boolean displayOwnNametag = false;

   JsonClientConfig() {
      if (!this.cfgFile.exists()) {
         this.save();
      }
   }

   @Override
   public ClientConfigInstance current() {
      return this.config;
   }

   @Override
   public void load(@Nullable Object ignored) {
      this.config = this.cfgFile.load();
   }

   @Override
   public void save() {
      this.cfgFile.save(this.config);
   }

   @Override
   public boolean displayOwnNameTag() {
      return this.displayOwnNametag;
   }
}
