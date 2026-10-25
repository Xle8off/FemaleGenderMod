package com.wildfire.client.cloud;

import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.services.MinecraftServicesSessionService;
import com.wildfire.client.ClientHelper;
import com.wildfire.common.WildfireGender;
import net.minecraft.client.Minecraft;

public final class CloudUtils {
   private static boolean loggedSessionTamperWarning = false;
   private static final String EXPECTED_YGGDRASIL_BASE_URL = "https://sessionserver.mojang.com/session/minecraft/";

   private CloudUtils() {
      throw new UnsupportedOperationException();
   }

   static SessionService getSessionService() {
      return Minecraft.getInstance().services().sessionService();
   }

   static boolean hasTheSessionServiceBeenTamperedWith() {
      SessionService sessionService = getSessionService();
      if (sessionService.getClass() != MinecraftServicesSessionService.class) {
         logSessionTamperWarning(
            "Detected likely session service tampering; got {} instead of the expected Yggdrasil session service", sessionService.getClass()
         );
         return true;
      } else if (!ClientHelper.INSTANCE
         .validateSessionUrl((MinecraftServicesSessionService)sessionService, "https://sessionserver.mojang.com/session/minecraft/")) {
         logSessionTamperWarning("Detected likely session service tampering; Yggdrasil base URL is not the expected Mojang-provided value");
         return true;
      } else {
         return false;
      }
   }

   private static void logSessionTamperWarning(String message, Object... args) {
      if (!loggedSessionTamperWarning) {
         WildfireGender.LOGGER.warn(message, args);
         WildfireGender.LOGGER.warn("Cloud sync will be unavailable for this session");
         loggedSessionTamperWarning = true;
      }
   }
}
