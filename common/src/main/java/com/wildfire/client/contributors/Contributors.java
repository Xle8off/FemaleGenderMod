package com.wildfire.client.contributors;

import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.wildfire.client.cloud.CloudSync;
import com.wildfire.common.WildfireGender;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.Optionull;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

public final class Contributors {
   @Language("RegExp")
   private static final String UUID_PATTERN = "(?i)[a-z0-9]{8}-[a-z0-9]{4}-4[0-9a-z]{3}-[a-z0-9]{4}-[a-z0-9]{12}";
   public static final UUID CREATOR_UUID = UUID.fromString("23b6feed-2dfe-4f2e-9429-863fd4adb946");
   private static final Map<UUID, Contributor> CONTRIBUTORS = new LinkedHashMap<>();
   private static final Supplier<CompletableFuture<Map<UUID, Contributor>>> MERGED_CONTRIBUTORS = Suppliers.memoize(() -> CompletableFuture.supplyAsync(() -> {
      Map<UUID, Contributor> contributors;
      try {
         contributors = CloudSync.getContributors().join();
      } catch (Exception e) {
         WildfireGender.LOGGER.error("Failed to retrieve contributors", e);
         return CONTRIBUTORS;
      }

      WildfireGender.LOGGER.debug("Retrieved contributor map from Cloud Sync: {}", contributors);
      return merge(contributors);
   }));

   private Contributors() {
      throw new UnsupportedOperationException();
   }

   public static @Unmodifiable Map<UUID, Contributor> getContributors() {
      return Collections.unmodifiableMap(MERGED_CONTRIBUTORS.get().getNow(CONTRIBUTORS));
   }

   public static @Unmodifiable Set<UUID> getContributorUUIDs() {
      return getContributors().keySet();
   }

   private static <T> @Nullable T map(UUID uuid, Function<Contributor, @Nullable T> mapping) {
      return (T)Optionull.map(getContributors().get(uuid), mapping);
   }

   public static Contributor.@Nullable Role getRole(UUID uuid) {
      return map(uuid, Contributor::getRole);
   }

   public static @Nullable Component getNametag(UUID uuid) {
      return map(uuid, Contributor::asText);
   }

   public static @Nullable TextColor getColor(UUID uuid) {
      return map(uuid, Contributor::getColor);
   }

   private static void addContributor(
      @Pattern("(?i)[a-z0-9]{8}-[a-z0-9]{4}-4[0-9a-z]{3}-[a-z0-9]{4}-[a-z0-9]{12}") String uuid, String name, Contributor.Role role, boolean showInCredits
   ) {
      UUID parsedUuid = UUID.fromString(uuid);
      Preconditions.checkArgument(!CONTRIBUTORS.containsKey(parsedUuid), "Contributor with UUID '%s' is already present", uuid);
      CONTRIBUTORS.put(parsedUuid, new Contributor(role.bit(), null, name, showInCredits));
   }

   private static void addContributor(
      @Pattern("(?i)[a-z0-9]{8}-[a-z0-9]{4}-4[0-9a-z]{3}-[a-z0-9]{4}-[a-z0-9]{12}") String uuid, String name, Contributor.Role role
   ) {
      addContributor(uuid, name, role, true);
   }

   private static SequencedMap<UUID, Contributor> merge(Map<UUID, Contributor> toMerge) {
      LinkedHashMap<UUID, Contributor> merged = new LinkedHashMap<>(CONTRIBUTORS);

      for (Entry<UUID, Contributor> entry : toMerge.entrySet()) {
         merged.merge(entry.getKey(), entry.getValue(), (a, b) -> (Contributor)(a.name() != null && b.name() == null ? a : b));
      }

      return merged;
   }

   static {
      addContributor("23b6feed-2dfe-4f2e-9429-863fd4adb946", "WildfireFGM", Contributor.Role.MOD_CREATOR);
      addContributor("4bceded7-9a58-4362-95e5-62c75b92f1d4", "Xle8", Contributor.Role.DEVELOPER);
      addContributor("70336328-0de7-430e-8cba-2779e2a05ab5", "celeste", Contributor.Role.FABRIC_MAINTAINER);
      addContributor("64e57307-72e5-4f43-be9c-181e8e35cc9b", "pupnewfster", Contributor.Role.NEOFORGE_MAINTAINER);
      addContributor("ad8ee68c-0aa1-47f9-b29f-f92fa1ef66dc", "DiaDemiEmi", Contributor.Role.DEVELOPER);
      addContributor("3f36f7e9-7459-43fe-87ce-4e8a5d47da80", "IzzyBizzy45", Contributor.Role.DEVELOPER);
      addContributor("ad3cb52d-524b-41b4-b9d6-b91ec440811d", "Crosby", Contributor.Role.DEVELOPER);
      addContributor("618a8390-51b1-43b2-a53a-ab72c1bbd8bd", "Kichura", Contributor.Role.CI_MAINTAINER);
      addContributor("9a60e979-c890-4b43-a4c0-32d8a9f6b6b9", "SavLeftUs", Contributor.Role.VOICE_ACTOR_FEMALE);
      addContributor("525b0455-15e9-49b7-b61d-f291e8ee6c5b", "Powerless001", Contributor.Role.GENERIC);
      addContributor("33feda66-c706-4725-8983-f62e5e6cbee7", "Bluelight", Contributor.Role.TRANSLATOR);
      addContributor("8fb5e95d-7f41-4b4c-b8c5-4f15ea3fa2c1", "ArcticWah", Contributor.Role.TRANSLATOR);
      addContributor("e31edb15-d8bd-44ac-8ec3-b54114e9d595", "PinguinLars", Contributor.Role.TRANSLATOR);
      addContributor("242c1a3a-83ee-4aa6-a3de-568cdac082a4", "le0n_lol", Contributor.Role.TRANSLATOR);
      addContributor("4c3e3225-aec0-499c-b563-2b17cdb017f8", "Betawolfy", Contributor.Role.TRANSLATOR);
      addContributor("07ee0495-90ae-4138-9343-9c270020196b", "vyxiepie_", Contributor.Role.TRANSLATOR);
      addContributor("372271ab-28f2-44bd-b585-95f43e010c22", "KeiraFGM", Contributor.Role.MASCOT, false);
   }
}
