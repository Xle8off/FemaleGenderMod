package com.wildfire.client.physics;

import com.wildfire.api.IGenderArmor;
import com.wildfire.client.WildfireClientHelper;
import com.wildfire.common.entitydata.EntityConfigHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

public record BothBreastsPhysics(BreastPhysics left, BreastPhysics right) {
   public BothBreastsPhysics(EntityConfigHolder<?> configHolder) {
      this(new BreastPhysics(configHolder), new BreastPhysics(configHolder));
   }

   public void tick(LivingEntity entity) {
      IGenderArmor armor = WildfireClientHelper.getArmorConfig(entity.getItemBySlot(EquipmentSlot.CHEST));
      this.left.update(entity, armor);
      this.right.update(entity, armor);
   }
}
