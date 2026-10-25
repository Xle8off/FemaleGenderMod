package com.wildfire.datagen;

import com.wildfire.api.IGenderArmor;
import com.wildfire.api.data.GenderArmorProvider;
import com.wildfire.api.impl.GenderArmor;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.jetbrains.annotations.ApiStatus.Internal;

@Internal
public class WildfireGenderArmorProvider extends GenderArmorProvider {
   public WildfireGenderArmorProvider(PackOutput output, CompletableFuture<Provider> registries, String modid) {
      super(output, registries, modid);
   }

   @Override
   protected void addDefaults(Provider lookupProvider) {
      this.add(EquipmentAssets.CHAINMAIL, new GenderArmor(0.5F, 0.2F));
      this.add(EquipmentAssets.COPPER, new GenderArmor(1.0F, 0.0F));
      this.add(EquipmentAssets.DIAMOND, new GenderArmor(1.0F, 0.0F));
      this.add(EquipmentAssets.ELYTRA, IGenderArmor.EMPTY);
      this.add(EquipmentAssets.GOLD, new GenderArmor(0.85F, 0.0F, true));
      this.add(EquipmentAssets.IRON, new GenderArmor(1.0F, 0.0F));
      this.add(EquipmentAssets.LEATHER, new GenderArmor(0.3F, 0.5F));
      this.add(EquipmentAssets.NETHERITE, new GenderArmor(1.0F, 0.0F));
   }
}
