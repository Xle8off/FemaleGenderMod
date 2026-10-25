package com.wildfire.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireHelper;
import com.wildfire.common.WildfireLang;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus.Internal;

public interface WildfireKeyBindings {
   Identifier CATEGORY_ID = WildfireGender.id("generic");
   WildfireKeyBindings INSTANCE = WildfireHelper.getService(WildfireKeyBindings.class);

   @Internal
   static KeyMapping createConfigKeyBind(Category category) {
      return new KeyMapping(WildfireLang.KEY_CONFIG.getTranslationKey(), 72, category);
   }

   @Internal
   static KeyMapping createToggleKeybind(Category category) {
      return new KeyMapping(WildfireLang.KEY_TOGGLE.getTranslationKey(), InputConstants.UNKNOWN.getValue(), category);
   }

   Category category();

   KeyMapping configKey();

   KeyMapping toggleKey();
}
