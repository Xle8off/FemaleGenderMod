package com.wildfire.client.config;

import com.wildfire.common.WildfireHelper;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public interface ClientConfig {
   ClientConfig INSTANCE = Objects.requireNonNullElseGet(WildfireHelper.getOptionalService(ClientConfig.class), JsonClientConfig::new);

   static ClientConfigInstance config() {
      return INSTANCE.current();
   }

   ClientConfigInstance current();

   void load(@Nullable Object var1);

   void save();

   boolean displayOwnNameTag();
}
