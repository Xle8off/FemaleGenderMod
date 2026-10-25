package com.wildfire.client.render.debug;

import com.wildfire.api.IGenderArmor;
import com.wildfire.client.resources.GenderArmorResourceManager;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.entitydata.EntityConfig;
import com.wildfire.common.entitydata.EntityConfigHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

public class GenderDebugHudEntry implements DebugScreenEntry {
   private final Identifier id;
   private final boolean clientPlayer;
   public static final Identifier SELF = WildfireGender.id("self_gender_info");
   public static final Identifier OTHER = WildfireGender.id("target_gender_info");
   private static final String PREFIX = ""
      + ChatFormatting.GRAY
      + ChatFormatting.UNDERLINE
      + "["
      + ChatFormatting.LIGHT_PURPLE
      + ChatFormatting.UNDERLINE
      + "F"
      + ChatFormatting.WHITE
      + ChatFormatting.UNDERLINE
      + "GM"
      + ChatFormatting.GRAY
      + ChatFormatting.UNDERLINE
      + "]"
      + ChatFormatting.RESET
      + ChatFormatting.UNDERLINE;

   public GenderDebugHudEntry(boolean clientPlayer) {
      this.clientPlayer = clientPlayer;
      this.id = clientPlayer ? SELF : OTHER;
   }

   public void display(DebugScreenDisplayer lines, @Nullable Level world, @Nullable LevelChunk clientChunk, @Nullable LevelChunk chunk) {
      Minecraft client = Minecraft.getInstance();
      Entity target = (Entity)(this.clientPlayer ? client.player : client.crosshairPickEntity);
      if (target instanceof LivingEntity living && EntityConfig.isSupportedEntity(living)) {
         EntityConfigHolder<?> config = EntityConfigHolder.getEntity(living);
         List<String> info = new ArrayList<>();
         info.add(PREFIX + " Gender Data");
         info.add("UUID: " + target.getUUID());
         info.addAll(config.getDebugInfo());
         this.addEquippedChestplate(info, config, living);
         lines.addToGroup(this.id, info);
      }
   }

   private void addEquippedChestplate(List<String> lines, EntityConfigHolder<?> config, LivingEntity entity) {
      ItemStack equippedChestplate = entity.getItemBySlot(EquipmentSlot.CHEST);
      Equippable equippable = (Equippable)equippedChestplate.get(DataComponents.EQUIPPABLE);
      ResourceKey<EquipmentAsset> asset = (ResourceKey<EquipmentAsset>)Optionull.map(equippable, it -> (ResourceKey)it.assetId().orElse(null));
      if (asset != null) {
         lines.add("");
         lines.add(PREFIX + " Equipped Chestplate");
         Identifier id = asset.identifier();
         IGenderArmor armorConfig = (IGenderArmor)Optionull.mapOrDefault(GenderArmorResourceManager.get(id), Function.identity(), IGenderArmor.DEFAULT);
         lines.add("Material: " + id);
         if (!armorConfig.coversBreasts()) {
            lines.add("Covers breasts: false");
         } else if (armorConfig.alwaysHidesBreasts()) {
            lines.add("Covers breasts: true");
         } else {
            lines.add("Physics resistance: " + armorConfig.physicsResistance());
            lines.add("Tightness: " + armorConfig.tightness());
            lines.add("Armor stands copy: " + armorConfig.armorStandsCopySettings());
            if (armorConfig.tightness() > 0.0F) {
               float renderedSize = config.breasts().bustSize().get() * (1.0F - 0.15F * armorConfig.tightness());
               lines.add("Rendered breast size: " + renderedSize);
            }
         }
      }
   }
}
