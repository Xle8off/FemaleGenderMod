package com.wildfire.client;

import com.wildfire.api.IGenderArmor;
import com.wildfire.client.resources.GenderArmorResourceManager;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public class WildfireClientHelper {
   private WildfireClientHelper() {
      throw new UnsupportedOperationException();
   }

   public static IGenderArmor getArmorConfig(ItemStack stack) {
      if (stack.isEmpty()) {
         return IGenderArmor.EMPTY;
      } else {
         Optional<IGenderArmor> genderArmor = GenderArmorResourceManager.get(stack);
         if (genderArmor.isPresent()) {
            return genderArmor.get();
         } else {
            return stack.has(DataComponents.EQUIPPABLE) ? IGenderArmor.DEFAULT : IGenderArmor.EMPTY;
         }
      }
   }
}
