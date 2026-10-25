package com.wildfire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wildfire.api.IGenderArmor;
import com.wildfire.client.appearance.AppearanceState;
import com.wildfire.client.cloud.CloudSync;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.client.gui.SyncedPlayerList;
import com.wildfire.client.gui.WildfireToast;
import com.wildfire.client.gui.screen.WardrobeBrowserScreen;
import com.wildfire.client.render.GenderArmorLayer;
import com.wildfire.client.render.GenderLayer;
import com.wildfire.client.render.GenderRenderState;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireHelper;
import com.wildfire.common.WildfireLang;
import com.wildfire.common.config.value.ConfigValue;
import com.wildfire.common.entitydata.EntityConfig;
import com.wildfire.common.entitydata.EntityConfigHolder;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import com.wildfire.common.networking.WildfireSync;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.model.object.armorstand.ArmorStandArmorModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class WildfireClientEventHandler {
   private static int timer = 0;

   private WildfireClientEventHandler() {
      throw new UnsupportedOperationException();
   }

   public static void onPlayerNametag(AvatarRenderState state, SubmitNodeCollector nodeCollector, PoseStack matrixStack, CameraRenderState camera) {
      GenderRenderState genderRenderState = ClientHelper.INSTANCE.getRenderState(state);
      if (genderRenderState != null && genderRenderState.nametag != null && state.nameTagAttachment != null) {
         matrixStack.pushPose();
         matrixStack.scale(0.5F, 0.5F, 0.5F);
         nodeCollector.submitNameTag(
            matrixStack,
            new Vec3(state.nameTagAttachment.x, (state.nameTagAttachment.y + 0.05) * 2.0, state.nameTagAttachment.z),
            state.showExtraEars ? -10 : 0,
            genderRenderState.nametag,
            !state.isDiscrete,
            state.lightCoords,
            camera
         );
         matrixStack.popPose();
         matrixStack.translate(0.0F, 0.0618125F, 0.0F);
      }
   }

   public static void renderTooltip(ItemStack item, Consumer<Component> tooltipAppender, @Nullable Player player) {
      if (player != null && ClientConfig.config().armorStat().get() && !ClientConfig.config().overrides().armorPhysics().get()) {
         Equippable equippableComponent = (Equippable)item.get(DataComponents.EQUIPPABLE);
         if (equippableComponent != null && equippableComponent.slot() == EquipmentSlot.CHEST) {
            PlayerConfigHolder playerConfig = WildfireGender.getPlayerById(player.getUUID());
            if (playerConfig != null && playerConfig.gender().get().canHaveBreasts()) {
               IGenderArmor config = WildfireClientHelper.getArmorConfig(item);
               if (config.coversBreasts() && config.physicsResistance() != 0.0F) {
                  String formatted = WildfireHelper.toFormattedPercent(config.physicsResistance()) + "%";
                  tooltipAppender.accept(WildfireLang.ARMOR_TOOLTIP.translateColored(TextColor.LIGHT_PURPLE, formatted));
               }
            }
         }
      }
   }

   public static void renderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
      Minecraft client = Minecraft.getInstance();
      if (client.gui.screen() instanceof WardrobeBrowserScreen) {
         SyncedPlayerList.resetTimer();
      } else {
         if (ClientConfig.config().playerListMode().get().isVisible()) {
            SyncedPlayerList.drawSyncedPlayers(context);
         } else {
            SyncedPlayerList.resetTimer();
         }
      }
   }

   public static void onEntityUnload(Entity entity, Level world) {
      EntityConfigHolder.CACHE.invalidate(entity.getUUID());
   }

   public static void onClientTick(Minecraft client) {
      if (client.level != null && client.player != null) {
         timer++;
         if (timer % 5 == 0) {
            PlayerConfigHolder clientConfig = WildfireGender.getPlayerById(client.player.getUUID());
            if (client.isMultiplayerServer() && clientConfig != null && !AppearanceState.isActive()) {
               WildfireSync.sendToServer(client.player.connection.getConnection(), clientConfig);
            }

            if (timer % 40 == 0) {
               CloudSync.sendNextQueueBatch();
               if (clientConfig != null && !AppearanceState.isActive()) {
                  clientConfig.attemptCloudSync();
               }
            }
         }

         if (WildfireKeyBindings.INSTANCE.toggleKey().consumeClick()
            && client.gui.screen() == null
            && ClientConfig.config().overrides().disableRendering().update(ConfigValue.TOGGLE)) {
            ClientConfig.INSTANCE.save();
         }

         if (WildfireKeyBindings.INSTANCE.configKey().consumeClick() && client.gui.screen() == null) {
            WardrobeBrowserScreen.open(client, client.player);
         }
      }
   }

   public static void clientDisconnect() {
      WildfireGender.CACHE.invalidateAll();
      EntityConfigHolder.CACHE.invalidateAll();
   }

   public static void clientJoin(Minecraft client) {
      if (client.player != null && ClientConfig.config().showToast().get()) {
         Component button = WildfireKeyBindings.INSTANCE.configKey().getTranslatedKeyMessage();
         ToastManager toastManager = client.gui.toastManager();
         toastManager.addToast(
            new WildfireToast(Minecraft.getInstance().font, WildfireLang.PLAYER_LIST_TITLE.translate(), WildfireLang.TOAST_GET_STARTED.translate(button))
         );
      }
   }

   public static void onEntityTick(LivingEntity entity) {
      if (EntityConfig.isSupportedEntity(entity)) {
         EntityConfigHolder<?> cfg = EntityConfigHolder.getEntity(entity);
         if (entity instanceof ArmorStand) {
            cfg.readFromStack(entity.getItemBySlot(EquipmentSlot.CHEST));
         }

         cfg.breastPhysics().tick(entity);
      }
   }

   public static void addAvatarRenderLayers(
      @Nullable AvatarRenderer<?> avatarRenderer,
      EquipmentLayerRenderer equipmentRenderer,
      BiConsumer<AvatarRenderer<?>, RenderLayer<AvatarRenderState, PlayerModel>> registration
   ) {
      if (avatarRenderer != null) {
         registration.accept(avatarRenderer, new GenderLayer(avatarRenderer));
         registration.accept(avatarRenderer, new GenderArmorLayer(avatarRenderer, equipmentRenderer));
      }
   }

   public static void addArmorStandRenderLayers(
      ArmorStandRenderer armorStandRenderer,
      EquipmentLayerRenderer equipmentRenderer,
      BiConsumer<ArmorStandRenderer, RenderLayer<ArmorStandRenderState, ArmorStandArmorModel>> registration
   ) {
      registration.accept(armorStandRenderer, new GenderArmorLayer(armorStandRenderer, equipmentRenderer));
   }
}
