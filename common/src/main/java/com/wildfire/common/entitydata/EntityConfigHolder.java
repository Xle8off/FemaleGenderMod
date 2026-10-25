package com.wildfire.common.entitydata;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.serialization.JsonOps;
import com.wildfire.api.Gender;
import com.wildfire.client.physics.BothBreastsPhysics;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.config.UVs;
import com.wildfire.common.config.value.ConfigValue;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jspecify.annotations.Nullable;

public class EntityConfigHolder<CONFIG extends EntityConfig> {
   public static final LoadingCache<UUID, EntityConfigHolder<? extends EntityConfig>> CACHE = CacheBuilder.newBuilder()
      .expireAfterAccess(Duration.ofMinutes(5L))
      .build(
         CacheLoader.<UUID,EntityConfigHolder<? extends EntityConfig>>from(uuid -> new EntityConfigHolder<>(uuid, EntityConfig.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.emptyMap()).getOrThrow()))
      );
   public final UUID uuid;
   protected final BothBreastsPhysics breastPhysics;
   protected boolean jacketLayer = true;
   protected @Nullable BreastDataComponent fromComponent;
   @Internal
   public boolean forceSimplifiedPhysics = false;
   protected CONFIG config;

   public static EntityConfigHolder<? extends EntityConfig> getEntity(LivingEntity entity) {
      return entity instanceof Player ? WildfireGender.getOrAddPlayerById(entity.getUUID()) : (EntityConfigHolder)CACHE.getUnchecked(entity.getUUID());
   }

   protected EntityConfigHolder(UUID uuid, CONFIG config) {
      this.uuid = uuid;
      this.breastPhysics = new BothBreastsPhysics(this);
      this.config = config;
   }

   public CONFIG config() {
      return this.config;
   }

   public void readFromStack(ItemStack chestplate) {
      CustomData component = (CustomData)chestplate.get(DataComponents.CUSTOM_DATA);
      if (chestplate.isEmpty() || component == null) {
         this.fromComponent = null;
         this.gender().update(Gender.MALE);
      } else if (this.fromComponent == null || !Objects.equals(component, this.fromComponent.nbtComponent())) {
         this.fromComponent = BreastDataComponent.fromComponent(component);
         if (this.fromComponent == null) {
            this.gender().update(Gender.MALE);
         } else {
            this.breasts().updateFromComponent(this.fromComponent);
            this.gender().update(this.breasts().bustSize().get() >= 0.02F ? Gender.FEMALE : Gender.MALE);
            this.jacketLayer = this.fromComponent.jacket();
         }
      }
   }

   public boolean hasJacketLayer() {
      return this.jacketLayer;
   }

   public BothBreastsPhysics breastPhysics() {
      return this.breastPhysics;
   }

   public List<String> getDebugInfo() {
      List<String> info = this.breasts().getDebugInfo();

      info.addFirst("Gender: " + switch ((Gender)this.gender().get()) {
         case FEMALE -> ChatFormatting.LIGHT_PURPLE + "Female";
         case MALE -> ChatFormatting.BLUE + "Male";
         case OTHER -> ChatFormatting.GREEN + "Other";
      });
      return info;
   }

   @Override
   public String toString() {
      return "%s(uuid=%s, gender=%s)".formatted(this.getClass().getCanonicalName(), this.uuid, this.gender());
   }

   public final ConfigValue<Gender> gender() {
      return this.config.gender;
   }

   public Breasts breasts() {
      return this.config.breasts;
   }

   public final UVs uvs() {
      return this.config.uvs;
   }
}
