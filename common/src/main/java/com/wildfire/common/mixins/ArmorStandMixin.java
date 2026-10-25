package com.wildfire.common.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.wildfire.common.WildfireEventHandler;
import com.wildfire.common.entitydata.BreastDataComponent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ArmorStand.class)
abstract class ArmorStandMixin extends LivingEntity {
   private ArmorStandMixin(EntityType<? extends LivingEntity> entityType, Level world) {
      super(entityType, world);
   }

   @ModifyArg(
      method = "swapItem",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/decoration/ArmorStand;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"
      ),
      index = 1
   )
   public ItemStack attachBreastData(ItemStack stack, @Local(argsOnly = true) EquipmentSlot slot, @Local(argsOnly = true) Player player) {
      if (!this.level().isClientSide() && slot == EquipmentSlot.CHEST && !stack.isEmpty()) {
         WildfireEventHandler.onEquipArmorStand(player, stack);
         return stack;
      } else {
         return stack;
      }
   }

   @ModifyArg(
      method = "swapItem",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/player/Player;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V"
      ),
      index = 1
   )
   public ItemStack removeBreastDataOnReplace(ItemStack stack, @Local(argsOnly = true) Player player) {
      if (!player.level().isClientSide()) {
         BreastDataComponent.removeFromStack(stack);
      }

      return stack;
   }

   @ModifyArg(
      method = "brokenByAnything",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/block/Block;popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"
      ),
      index = 2
   )
   public ItemStack removeBreastDataOnBreak(ItemStack stack) {
      if (!this.level().isClientSide()) {
         BreastDataComponent.removeFromStack(stack);
      }

      return stack;
   }
}
