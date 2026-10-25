package com.wildfire.client.resources;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.wildfire.api.IGenderArmor;
import com.wildfire.common.WildfireGender;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

public final class GenderArmorResourceManager extends SimpleJsonResourceReloadListener<IGenderArmor> {
   private static final FileToIdConverter LEGACY_PATH_CONVERTER = FileToIdConverter.json("wildfire_gender_data");
   public static final Identifier ID = WildfireGender.id("armor_data");
   public static final GenderArmorResourceManager INSTANCE = new GenderArmorResourceManager();
   private @Unmodifiable Map<Identifier, IGenderArmor> configs = Map.of();

   private GenderArmorResourceManager() {
      super(IGenderArmor.CODEC, FileToIdConverter.json("female_gender_mod/armor_data"));
   }

   protected Map<Identifier, IGenderArmor> prepare(ResourceManager manager, ProfilerFiller profiler) {
      Map<Identifier, IGenderArmor> result = super.prepare(manager, profiler);
      this.handleLegacyFiles(manager, result);
      return result;
   }

   @Deprecated(forRemoval = true)
   private void handleLegacyFiles(ResourceManager manager, Map<Identifier, IGenderArmor> result) {
      int newSize = result.size();
      Set<IGenderArmor> legacyElements = new ReferenceOpenHashSet();
      scanDirectory(manager, LEGACY_PATH_CONVERTER, JsonOps.INSTANCE, IGenderArmor.CODEC.validate(armor -> {
         legacyElements.add(armor);
         return DataResult.success(armor);
      }), result);
      if (newSize != result.size()) {
         for (Entry<Identifier, IGenderArmor> entry : result.entrySet()) {
            if (legacyElements.contains(entry.getValue())) {
               WildfireGender.LOGGER
                  .warn("Gender Armor config: '{}' should be moved to the new folder path: '{}'", entry.getKey(), "female_gender_mod/armor_data");
            }
         }
      }
   }

   protected void apply(Map<Identifier, IGenderArmor> prepared, ResourceManager manager, ProfilerFiller profiler) {
      this.configs = Collections.unmodifiableMap(prepared);
   }

   public static @Nullable IGenderArmor get(Identifier model) {
      return INSTANCE.configs.get(model);
   }

   public static Optional<IGenderArmor> get(ItemStack item) {
      return Optional.ofNullable((Equippable)item.get(DataComponents.EQUIPPABLE))
         .flatMap(Equippable::assetId)
         .<Identifier>map(ResourceKey::identifier)
         .map(GenderArmorResourceManager::get);
   }
}
