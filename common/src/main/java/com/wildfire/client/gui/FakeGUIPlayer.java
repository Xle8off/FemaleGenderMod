package com.wildfire.client.gui;

import com.google.common.base.Suppliers;
import com.wildfire.api.Gender;
import com.wildfire.client.cloud.CloudSync;
import com.wildfire.client.contributors.Contributor;
import com.wildfire.client.contributors.Contributors;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.entitydata.EntityConfigHolder;
import com.wildfire.common.entitydata.PlayerConfig;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class FakeGUIPlayer {
   public static final Consumer<PlayerConfig> FEMALE_CHANGES = config -> {
      config.gender.update(Gender.FEMALE);
      config.breasts.yOffset().update(-0.2F);
      config.breasts.physics().uniboob().update(false);
      config.breasts.cleavage().update(0.05F);
   };
   private final String name;
   private final UUID uuid;
   private final Supplier<FakeGUIPlayer.GUIMannequin> entity;
   private final @Nullable Component description;

   public FakeGUIPlayer(String name, UUID uuid, @Nullable Component description, @Nullable Consumer<PlayerConfig> defaultGenderSettings) {
      this.name = name;
      this.uuid = uuid;
      this.entity = createPlayerSupplier(this.uuid, this.name, defaultGenderSettings);
      this.description = description;
   }

   public FakeGUIPlayer(String name, UUID uuid, @Nullable Consumer<PlayerConfig> defaultGenderSettings) {
      this(name, uuid, null, defaultGenderSettings);
   }

   public ClientMannequin getEntity() {
      return this.entity.get();
   }

   public UUID getUUID() {
      return this.uuid;
   }

   public String getName() {
      return this.name;
   }

   public Contributor.@Nullable Role getRole() {
      return Contributors.getRole(this.uuid);
   }

   public Contributor.Role getRoleOrGeneric() {
      Contributor.Role role = this.getRole();
      return role == null ? Contributor.Role.GENERIC : role;
   }

   public @Nullable Component getDescription() {
      return this.description;
   }

   public void tick() {
      FakeGUIPlayer.GUIMannequin entity = this.entity.get();
      entity.applyLoadedSkin();
      entity.tickCount++;
      EntityConfigHolder.getEntity(entity).breastPhysics().tick(entity);
   }

   private static Supplier<FakeGUIPlayer.GUIMannequin> createPlayerSupplier(UUID uuid, String name, @Nullable Consumer<PlayerConfig> defaultGenderData) {
      return Suppliers.memoize(
         () -> {
            Minecraft client = Minecraft.getInstance();
            assert client.level != null;
            FakeGUIPlayer.GUIMannequin entity = new FakeGUIPlayer.GUIMannequin(
               client.level, client.playerSkinRenderCache(), ResolvableProfile.createUnresolved(uuid)
            );
            entity.setCustomName(Component.literal(name));

            PlayerConfigHolder config;
            try {
               config = (PlayerConfigHolder)EntityConfigHolder.CACHE.get(entity.getUUID(), () -> new PlayerConfigHolder(entity.getUUID()));
            } catch (ExecutionException | ClassCastException var7) {
               return entity;
            }

            config.forceSimplifiedPhysics = true;
            PlayerConfigHolder cached = WildfireGender.getPlayerById(uuid);
            if (cached == null) {
               CloudSync.getProfile(uuid, true).thenAccept(json -> {
                  if (json != null) {
                     config.updateFromJson(json);
                  } else if (defaultGenderData != null) {
                     defaultGenderData.accept(config.config());
                  }
               });
            } else {
               config.updateFromJson(cached.toJson());
            }

            return entity;
         }
      );
   }

   private static class GUIMannequin extends ClientMannequin {
      private final ResolvableProfile copySkinFrom;

      public GUIMannequin(Level world, PlayerSkinRenderCache skinCache, ResolvableProfile copySkinFrom) {
         super(world, skinCache);
         this.copySkinFrom = copySkinFrom;
         this.updateSkin();
         this.setId(1);
      }

      public void applyLoadedSkin() {
         if (this.skinLookup != null && this.skinLookup.isDone()) {
            try {
               Optional<net.minecraft.world.entity.player.PlayerSkin> var10000 = this.skinLookup.get();
               FakeGUIPlayer.GUIMannequin var1 = this;
               var10000.ifPresent(x$0 -> var1.setSkin(x$0));
               this.skinLookup = null;
            } catch (Exception var2) {
            }
         }
      }

      public ResolvableProfile getProfile() {
         return this.copySkinFrom;
      }
   }
}
