package com.wildfire.common.entitydata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildfire.common.config.validator.ConfigRange;
import com.wildfire.common.config.value.ConfigKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public record BreastDataComponent(float breastSize, float cleavage, Vector3fc offsets, boolean jacket, @Nullable CustomData nbtComponent) {
   private static final String LEGACY_KEY = "WildfireGender";
   private static final Codec<BreastDataComponent> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            BreastState.CODEC.fieldOf("breasts").forGetter(BreastState::new), Codec.BOOL.optionalFieldOf("Jacket", true).forGetter(BreastDataComponent::jacket)
         )
         .apply(instance, (state, jacket) -> new BreastDataComponent(state.breastSize(), state.cleavage(), state.offsets(), jacket, null))
   );
   private static final Codec<BreastDataComponent> LEGACY_CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            legacy(Breasts.BUST_SIZE, "BreastSize").forGetter(BreastDataComponent::breastSize),
            legacy(Breasts.BREASTS_CLEAVAGE, "Cleavage").forGetter(BreastDataComponent::cleavage),
            legacy(Breasts.BREASTS_OFFSET_X, "XOffset").forGetter(component -> component.offsets.x()),
            legacy(Breasts.BREASTS_OFFSET_Y, "YOffset").forGetter(component -> component.offsets.y()),
            legacy(Breasts.BREASTS_OFFSET_Z, "ZOffset").forGetter(component -> component.offsets.y()),
            Codec.BOOL.optionalFieldOf("Jacket", true).forGetter(BreastDataComponent::jacket)
         )
         .apply(instance, (breastSize, cleavage, x, y, z, jacket) -> new BreastDataComponent(breastSize, cleavage, new Vector3f(x, y, z), jacket, null))
   );
   private static final Codec<BreastDataComponent> OR_LEGACY = CODEC.withAlternative(LEGACY_CODEC);

   private static MapCodec<Float> legacy(ConfigKey<Float> configKey, String legacyKey) {
      if (configKey.validator() instanceof ConfigRange(Comparable var10, Comparable var12)) {
         Float var6 = (Float)var10;
         Float minInclusive = var6;
         var6 = (Float)var12;
         Float maxInclusive = var6;
         return ExtraCodecs.floatRange(minInclusive, maxInclusive).lenientOptionalFieldOf(legacyKey, configKey.defaultValue());
      } else {
         throw new IllegalArgumentException("No range defined for config key: " + legacyKey);
      }
   }

   public static @Nullable BreastDataComponent fromPlayer(Player player, PlayerConfigHolder config) {
      if (config.gender().get().canHaveBreasts() && config.showBreastsInArmor().get()) {
         Breasts breasts = config.breasts();
         return new BreastDataComponent(
            breasts.bustSize().get(), breasts.cleavage().get(), breasts.offset(), player.isModelPartShown(PlayerModelPart.JACKET), null
         );
      } else {
         return null;
      }
   }

   public static @Nullable BreastDataComponent fromComponent(@Nullable CustomData component) {
      if (component == null) {
         return null;
      }

      CompoundTag compoundTag = component.copyTag();
      return OR_LEGACY.parse(
            NbtOps.INSTANCE, (Tag)compoundTag.getCompound("female_gender_mod").orElseGet(() -> compoundTag.getCompoundOrEmpty("WildfireGender"))
         )
         .result()
         .map(breastDataComponent -> breastDataComponent.withComponent(component))
         .orElse(null);
   }

   public void write(ItemStack stack) {
      if (stack.isEmpty()) {
         throw new IllegalArgumentException("The provided ItemStack must not be empty");
      }

      CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> {
         if (nbt.contains("WildfireGender")) {
            nbt.remove("WildfireGender");
         }

         nbt.store("female_gender_mod", CODEC, this);
      });
   }

   public static void removeFromStack(ItemStack stack) {
      if (!stack.isEmpty()) {
         CustomData component = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
         if (component != null) {
            CompoundTag compoundTag = component.copyTag();
            if (compoundTag.contains("female_gender_mod") || compoundTag.contains("WildfireGender")) {
               CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> {
                  nbt.remove("female_gender_mod");
                  nbt.remove("WildfireGender");
               });
            }
         }
      }
   }

   private BreastDataComponent withComponent(CustomData component) {
      return new BreastDataComponent(this.breastSize, this.cleavage, this.offsets, this.jacket, component);
   }
}
