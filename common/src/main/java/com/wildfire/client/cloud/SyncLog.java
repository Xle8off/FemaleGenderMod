package com.wildfire.client.cloud;

import com.wildfire.client.config.ClientConfig;
import com.wildfire.common.WildfireLang;
import com.wildfire.common.config.enums.SyncVerbosity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public final class SyncLog {
   public static final List<SyncLog.Entry> SYNC_LOG = new ArrayList<>();

   public static SyncVerbosity verbosity() {
      return ClientConfig.config().cloudSync().logVerbosity().get();
   }

   public static void add(WildfireLang langEntry, SyncVerbosity verbosity) {
      if (verbosity().ordinal() >= verbosity.ordinal()) {
         add(langEntry);
      }
   }

   public static void add(WildfireLang langEntry) {
      SYNC_LOG.add(new SyncLog.Entry(langEntry.translate(), Instant.now()));
      if (SYNC_LOG.size() > 6) {
         SYNC_LOG.removeFirst();
      }
   }

   public record Entry(Component text, Instant timestamp) {
      public static final int NEW_COLOR = -16711936;
      public static final int OLD_COLOR = -13328128;

      public int color() {
         long secondsPassed = Instant.now().getEpochSecond() - this.timestamp.getEpochSecond();
         float delta = Math.clamp((float)secondsPassed / 60.0F, 0.0F, 1.0F);
         return ARGB.linearLerp(delta, -16711936, -13328128);
      }
   }
}
