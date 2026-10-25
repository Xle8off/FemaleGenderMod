package com.wildfire.api.data;

import com.wildfire.api.IGenderArmor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.PackOutput.PathProvider;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.EquipmentAsset;

public abstract class GenderArmorProvider implements DataProvider {
   public static final String PREFIX = "female_gender_mod/armor_data";
   private final Map<Identifier, IGenderArmor> armorConfigs = new HashMap<>();
   private final CompletableFuture<Provider> registries;
   private final PathProvider pathProvider;
   private final String modId;

   protected GenderArmorProvider(PackOutput output, CompletableFuture<Provider> registries, String modId) {
      this.registries = registries;
      this.pathProvider = output.createPathProvider(Target.RESOURCE_PACK, "female_gender_mod/armor_data");
      this.modId = modId;
   }

   public final String getName() {
      return "Gender Armor Provider: " + this.modId;
   }

   public final CompletableFuture<?> run(CachedOutput cachedOutput) {
      return this.registries.thenCompose(lookupProvider -> {
         this.addDefaults(lookupProvider);
         List<CompletableFuture<?>> list = new ArrayList<>(this.armorConfigs.size());

         for (Entry<Identifier, IGenderArmor> entry : this.armorConfigs.entrySet()) {
            list.add(DataProvider.saveStable(cachedOutput, lookupProvider, IGenderArmor.CODEC, entry.getValue(), this.pathProvider.json(entry.getKey())));
         }

         return CompletableFuture.allOf(list.toArray(new CompletableFuture[0]));
      });
   }

   protected abstract void addDefaults(Provider var1);

   protected void add(ArmorMaterial armorMaterial, IGenderArmor armor) {
      this.add(armorMaterial.assetId(), armor);
   }

   protected void add(ResourceKey<EquipmentAsset> armorMaterial, IGenderArmor armor) {
      this.add(armorMaterial.identifier(), armor);
   }

   protected void add(Identifier material, IGenderArmor armor) {
      if (this.armorConfigs.put(material, armor) != null) {
         throw new IllegalArgumentException("Attempted to register multiple entries for " + material);
      }
   }
}
