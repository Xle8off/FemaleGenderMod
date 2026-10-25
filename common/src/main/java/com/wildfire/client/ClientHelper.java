package com.wildfire.client;

import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.wildfire.api.Gender;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.client.render.GenderRenderState;
import com.wildfire.common.WildfireHelper;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.Nullable;

public interface ClientHelper {
   ClientHelper INSTANCE = WildfireHelper.getService(ClientHelper.class);

   Holder<SoundEvent> femaleHurt();

   default @Nullable Holder<SoundEvent> hurtSound(Gender gender) {
      if (ClientConfig.config().overrides().disableSoundReplacement().get()) {
         return null;
      }

      return switch (gender) {
         case FEMALE, OTHER -> this.femaleHurt();
         case MALE -> null;
      };
   }

   @Nullable GenderRenderState getRenderState(HumanoidRenderState var1);

   boolean validateSessionUrl(YggdrasilMinecraftSessionService var1, String var2);
}
