/*
 * Wildfire's Female Gender Mod is a female gender mod created for Minecraft.
 * Copyright (C) 2023-present WildfireRomeo
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wildfire.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wildfire.events.ArmorStatsTooltipEvent;
import com.wildfire.events.EntityHurtSoundEvent;
import com.wildfire.events.EntityTickEvent;
import com.wildfire.events.PlayerNametagRenderEvent;
import com.wildfire.gui.SyncedPlayerList;
import com.wildfire.gui.WildfireToast;
import com.wildfire.gui.screen.WardrobeBrowserScreen;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.WildfireHelper;
import com.wildfire.main.WildfireLang;
import com.wildfire.main.cloud.CloudSync;
import com.wildfire.main.config.ClientConfig;
import com.wildfire.main.entitydata.EntityConfig;
import com.wildfire.main.entitydata.EntityConfigHolder;
import com.wildfire.main.entitydata.PlayerConfigHolder;
import com.wildfire.main.networking.WildfireSync;
import com.wildfire.main.networking.appearance.ServerAppearanceSync;
import com.wildfire.main.networking.appearance.CosmeticCatalogClient;
import com.wildfire.main.networking.appearance.ModUpdate;
import com.wildfire.main.networking.appearance.SocialClient;
import com.wildfire.main.networking.appearance.NotificationInbox;
import com.wildfire.mixins.accessors.AbstractContainerScreenAccessor;
import com.wildfire.render.GenderArmorLayer;
import com.wildfire.render.GenderLayer;
import com.wildfire.render.GenderRenderState;
import com.wildfire.render.HolidayFeaturesRenderer;
import com.wildfire.render.CosmeticLayer;
import com.wildfire.physics.AnimatedTorsoPhysics;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/// @apiNote Only use this on the client side
public final class WildfireClientEventHandler {//TODO: Move other classes to a client based package

    private WildfireClientEventHandler() {
        throw new UnsupportedOperationException();
    }

    private static final Category CATEGORY = Util.make(() -> KeyMapping.Category.register(WildfireGender.id("generic")));
    private static final KeyMapping TOGGLE_KEYBIND = Util.make(() -> {
        KeyMapping keybind = new KeyMapping("key.wildfire_gender.toggle", InputConstants.UNKNOWN.getValue(), CATEGORY);
        KeyMappingHelper.registerKeyMapping(keybind);
        return keybind;
    });

    /// Register all client-side events
    public static void registerClientEvents() {
        com.wildfire.client.screens.ScreenClient.configureMedia(com.wildfire.client.screens.ScreenInteraction::mediaOffer);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,level,hand,hit)->level.isClientSide()&&com.wildfire.client.screens.ScreenInteraction.use(hit)?net.minecraft.world.InteractionResult.SUCCESS:net.minecraft.world.InteractionResult.PASS);
        com.wildfire.client.music.SpringPlusSession.configure(() -> {
            var client = Minecraft.getInstance();
            return client.player != null && client.level != null && client.isMultiplayerServer() && ServerAppearanceSync.isActive();
        });
        ClientEntityEvents.ENTITY_UNLOAD.register(WildfireClientEventHandler::onEntityUnload);
        ClientTickEvents.END_CLIENT_TICK.register(WildfireClientEventHandler::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register(WildfireClientEventHandler::clientDisconnect);
        ClientPlayConnectionEvents.JOIN.register(WildfireClientEventHandler::clientJoin);
        LivingEntityRenderLayerRegistrationCallback.EVENT.register(WildfireClientEventHandler::registerRenderLayers);
        LevelRenderEvents.END_EXTRACTION.register(CosmeticLayer::captureFirstPersonPet);
        LevelRenderEvents.COLLECT_SUBMITS.register(CosmeticLayer::submitFirstPersonPet);
        LevelRenderEvents.COLLECT_SUBMITS.register(context->com.wildfire.client.screens.ScreenWorldRenderer.submit(context.poseStack(),context.submitNodeCollector(),context.levelState().cameraRenderState));
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            WildfireGender.id("player_list"),
            WildfireClientEventHandler::renderHud
        );
        ArmorStatsTooltipEvent.EVENT.register(WildfireClientEventHandler::renderTooltip);
        EntityTickEvent.EVENT.register(WildfireClientEventHandler::onEntityTick);
        PlayerNametagRenderEvent.EVENT.register(WildfireClientEventHandler::onPlayerNametag);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!com.wildfire.client.music.SpringPlusSession.enabled()) return;
            if (screen instanceof ChatScreen) {
                Screens.getWidgets(screen).add(com.wildfire.gui.screen.NotificationCenterScreen.hudPlayerWidget(screen));
                addQuickButtons(client, screen, width);
                return;
            }
            if (!(screen instanceof InventoryScreen || screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)) return;
            int x = ((AbstractContainerScreenAccessor) screen).wildfire$getLeftPos() + 129;
            int y = height / 2 - 22;
            var button = new AppearanceButton(x, y, _ -> {
                if (client.player == null || !ServerAppearanceSync.isActive()) return;
                client.player.connection.sendCommand("appearance");
            });
            if(screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative){
                button.setX(((AbstractContainerScreenAccessor)screen).wildfire$getLeftPos()+145);
                button.setY(((AbstractContainerScreenAccessor)screen).wildfire$getTopPos()+24);
                button.visible=creative.isInventoryOpen();
            }
            button.active = ServerAppearanceSync.isActive();
            button.setTooltip(Tooltip.create(button.active
                ? Component.literal("Открыть кастомизацию персонажа")
                : Component.literal("Кастомизация доступна только на сервере")
                    .withStyle(style -> style.withColor(0xF3A5D8))
                    .append(Component.literal("\n✦ SpringPlus.net ✦")
                        .withStyle(style -> style.withColor(0xB691FF).withBold(true)))));
            Screens.getWidgets(screen).add(button);
            ScreenEvents.beforeExtract(screen).register((current, graphics, mouseX, mouseY, delta) -> {
                button.setX(((AbstractContainerScreenAccessor) current).wildfire$getLeftPos() + 129);
                button.setY(current.height / 2 - 22);
                button.active = ServerAppearanceSync.isActive();
                if(current instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative){
                    button.setX(((AbstractContainerScreenAccessor)current).wildfire$getLeftPos()+145);
                    button.setY(((AbstractContainerScreenAccessor)current).wildfire$getTopPos()+24);
                    button.visible=creative.isInventoryOpen();button.active&=button.visible;
                }
            });
        });
    }

    private static void addQuickButtons(Minecraft client, net.minecraft.client.gui.screens.Screen screen, int width) {
        var notifications = new NotificationButton(10, 14, _ ->
            client.gui.setScreen(new com.wildfire.gui.screen.NotificationCenterScreen(screen)));
        notifications.setTooltip(Tooltip.create(Component.literal("Уведомления SpringPlus")));
        Screens.getWidgets(screen).add(notifications);
        var streams = new com.wildfire.client.music.StreamHudButton(screen);
        Screens.getWidgets(screen).add(streams);
        var updateButton = new com.wildfire.client.music.UpdateHudButton(ModUpdate::updateHudState, ModUpdate::activateUpdateHud);
        Screens.getWidgets(screen).add(updateButton);
        if (screen instanceof ChatScreen) {
            for (int slot = 0; slot < 3; slot++) {
            final int toastSlot = slot;
            final long[] displayedId = {-1};
            Button primary = new com.wildfire.gui.ToastControlButton(10, 0, 100, 20, Component.empty(), button -> {
                if (displayedId[0] != ModUpdate.popupId(toastSlot)) return;
                var action = ModUpdate.popupAction(toastSlot);
                if (action == null) return;
                if ("message".equals(action.kind()) || "friend_message".equals(action.kind()))
                    client.gui.setScreen(new com.wildfire.gui.screen.NotificationCenterScreen(screen, action.target()));
                else if ("friend_request".equals(action.kind())) SocialClient.action("add", action.target());
                else if ("notification".equals(action.kind())) {
                    var center=new com.wildfire.gui.screen.NotificationCenterScreen(screen);
                    center.openNotification(action.target()); client.gui.setScreen(center);
                } else if("stream".equals(action.kind()))net.minecraft.util.Util.getPlatform().openUri(action.target());
                ModUpdate.dismissPopup(displayedId[0]);
            },ModUpdate::popupControlsReveal);
            Button secondary = new com.wildfire.gui.ToastControlButton(116, 0, 106, 20, Component.empty(), button -> {
                if (displayedId[0] != ModUpdate.popupId(toastSlot)) return;
                var action = ModUpdate.popupAction(toastSlot);
                if (action == null) return;
                if(!"notification".equals(action.kind()))SocialClient.action("message".equals(action.kind()) ? "add" : "reject", action.target());
                ModUpdate.dismissPopup(displayedId[0]);
            },ModUpdate::popupControlsReveal);
            var dismiss=new com.wildfire.gui.ToastDismissButton(()->ModUpdate.popupId(toastSlot),
                ()->ModUpdate.popupTop(toastSlot),ModUpdate::popupControlsReveal,ModUpdate::dismissPopup);
            Screens.getWidgets(screen).add(dismiss);
            Screens.getWidgets(screen).add(primary);
            Screens.getWidgets(screen).add(secondary);
            primary.visible = secondary.visible = false;
            ScreenEvents.beforeExtract(screen).register((current, graphics, mouseX, mouseY, delta) -> {
                dismiss.sync(current.width);
                var action = ModUpdate.popupAction(toastSlot);
                displayedId[0] = ModUpdate.popupId(toastSlot);
                primary.visible = action != null;
                secondary.visible = action != null && !"message".equals(action.kind()) && !"friend_message".equals(action.kind()) && !"stream".equals(action.kind());
                if (action == null) return;
                primary.setWidth("friend_message".equals(action.kind())||"stream".equals(action.kind()) ? 160 : 100);
                primary.setX(secondary.visible ? 10 : 10 + (212 - primary.getWidth()) / 2);
                primary.setY(ModUpdate.popupActionY(toastSlot));
                secondary.setY(ModUpdate.popupActionY(toastSlot));
                primary.setMessage(Component.literal("stream".equals(action.kind())?"Смотреть стрим":"friend_message".equals(action.kind()) ? "Написать сообщение" : "notification".equals(action.kind()) ? "Посмотреть" : "message".equals(action.kind()) ? "Ответить" : "Принять"));
                secondary.setMessage(Component.literal("notification".equals(action.kind()) ? "Закрыть" : "message".equals(action.kind()) ? "В друзья" : "Отклонить"));
            });
            }
        }
        ScreenEvents.beforeExtract(screen).register((current, graphics, mouseX, mouseY, delta) -> {
            boolean bellVisible = (current instanceof ChatScreen || com.wildfire.client.config.CosmeticPreferences.hudBell()) && !ModUpdate.popupVisible();
            notifications.visible = bellVisible;
            notifications.setY(14 + ModUpdate.topInset());
            streams.sync(14 + ModUpdate.topInset(), bellVisible);
            updateButton.sync(38 + ModUpdate.topInset(),
                com.wildfire.client.music.SpringPlusSession.enabled() && !ModUpdate.popupVisible());
            if (current instanceof ChatScreen) com.wildfire.gui.QuickHud.drawTrack(graphics);
        });
    }

    private static final class AppearanceButton extends Button {
        private static final ItemStack ICON = new ItemStack(Items.ARMOR_STAND);

        private AppearanceButton(int x, int y, OnPress onPress) {
            super(x, y, 20, 18, Component.literal("Образ"), onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            com.wildfire.gui.FlatButtons.plate(graphics,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,com.wildfire.gui.UiTheme.ACCENT);
            graphics.item(ICON, getX() + 2, getY() + 1);
        }
    }

    private static final class NotificationButton extends com.wildfire.client.music.ChatOverlayButton {
        private NotificationButton(int x, int y, OnPress onPress) {
            super(x, y, 20, 20, Component.literal("Уведомления"), onPress);
        }
        @Override public ComponentPath nextFocusPath(FocusNavigationEvent event) { return null; }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            com.wildfire.gui.FlatButtons.plate(graphics,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),false,com.wildfire.gui.UiTheme.ACCENT);
            com.wildfire.gui.QuickHud.drawBellIcon(graphics, getX(), getY());
        }
    }

    private static void onPlayerNametag(AvatarRenderState state, PoseStack matrixStack, Consumer<Component> renderHelper) {
        var genderRenderState = GenderRenderState.get(state);
        if (genderRenderState == null) {
            return;
        }

        Component nametag = genderRenderState.nametag;
        if (nametag == null) {
            return;
        }

        matrixStack.pushPose();
        //TODO: Try to base this off of EntityRenderState#nameTagAttachment which should help position it appropriately
        float translationAmt = switch (state.pose) {
            case CROUCHING -> 0.8f;
            case SLEEPING -> 0.125f;
            case SWIMMING, FALL_FLYING -> 0.3f;
            case SITTING -> 0.275f; //not tested; sitting on a pig doesn't work apparently
            default -> 0.95f;
        };
        matrixStack.translate(0f, translationAmt, 0f);
        matrixStack.scale(0.5f, 0.5f, 0.5f);
        renderHelper.accept(nametag);
        matrixStack.popPose();
        // shift the rest of the name tag up a little bit
        matrixStack.translate(0f, 2.15F * 1.15F * 0.025F, 0f);
    }

    private static void renderTooltip(ItemStack item, Consumer<Component> tooltipAppender, @Nullable Player player) {
        if (player == null || !ClientConfig.config().armorStat.get()) {
            return;
        }
        if (ClientConfig.config().armorPhysicsOverride.get()) {
            return;
        }

        var playerConfig = WildfireGender.getPlayerById(player.getUUID());
        if (playerConfig == null || !playerConfig.gender().get().canHaveBreasts()) {
            return;
        }

        var equippableComponent = item.get(DataComponents.EQUIPPABLE);
        if (equippableComponent == null || equippableComponent.slot() != EquipmentSlot.CHEST) {
            return;
        }

        var config = WildfireClientHelper.getArmorConfig(item);
        // don't show a +0 tooltip on items that don't interact with physics (e.g. Elytra)
        if (!config.coversBreasts() || config.physicsResistance() == 0f) {
            return;
        }

        var formatted = WildfireHelper.toFormattedPercent(config.physicsResistance()) + "%";
        //~ if >=26.2 'net.minecraft.ChatFormatting' -> 'TextColor'
        tooltipAppender.accept(WildfireLang.ARMOR_TOOLTIP.translateColored(TextColor.LIGHT_PURPLE, formatted));
    }

    private static void renderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!com.wildfire.client.music.SpringPlusSession.enabled()) return;
        ModUpdate.renderHud(context);
        var client = Minecraft.getInstance();
        if (client.player != null && client.gui.screen() == null) com.wildfire.gui.QuickHud.draw(context);
        //~ if >=26.2 'client.screen' -> 'client.gui.screen()'
        if (client.gui.screen() instanceof WardrobeBrowserScreen) {
            SyncedPlayerList.resetTimer();
            return;
        }

        if (ClientConfig.config().playerListMode.get().isVisible()) {
            SyncedPlayerList.drawSyncedPlayers(context);
        } else {
            SyncedPlayerList.resetTimer();
        }
    }

    /// Attach breast render layers to players and armor stands
    private static void registerRenderLayers(EntityType<? extends LivingEntity> entityType, LivingEntityRenderer<?, ?, ?> entityRenderer,
        LivingEntityRenderLayerRegistrationCallback.RegistrationHelper registrationHelper,
        EntityRendererProvider.Context context) {
        if (entityRenderer instanceof AvatarRenderer<?> playerRenderer) {
            registrationHelper.register(new GenderLayer<>(playerRenderer));
            registrationHelper.register(new GenderArmorLayer<>(playerRenderer, context.getEquipmentRenderer()));
            registrationHelper.register(new HolidayFeaturesRenderer(playerRenderer));
            registrationHelper.register(new CosmeticLayer(playerRenderer));
        } else if (entityRenderer instanceof ArmorStandRenderer armorStandRenderer) {
            registrationHelper.register(new GenderArmorLayer<>(armorStandRenderer, context.getEquipmentRenderer()));
        }
    }

    /// Remove (non-player) entities from the client cache when they're unloaded
    private static void onEntityUnload(Entity entity, Level world) {
        // note that we don't attempt to unload players; they're instead only ever unloaded once we leave a world,
        // or once they disconnect
        EntityConfigHolder.CACHE.invalidate(entity.getUUID());
    }

    /// Perform various actions that should happen once per client tick, such as syncing client player settings to the server.
    private static void onClientTick(Minecraft client) {
        com.wildfire.client.route.RouteProbeClient.tick(client);
        com.wildfire.client.screens.ScreenClient.tick(client.getConnection(),client.level);
        com.wildfire.client.screens.BrowserRuntime.tick(client.level);
        com.wildfire.client.screens.ScreenInteraction.tickAutoView();
        com.wildfire.client.discord.DiscordPresence.tick(client,com.wildfire.main.networking.appearance.SocialClient.roleName());
        com.wildfire.client.music.SpringPlusSession.tick();
        if (!com.wildfire.client.music.SpringPlusSession.enabled()) return;
        com.wildfire.client.music.MusicPriority.tick(com.wildfire.main.networking.appearance.MusicClient.serverActive());
        if (client.level == null || client.player == null) {
            return;
        }
        CosmeticCatalogClient.tick();
        ModUpdate.tick();
        ServerAppearanceSync.tickSkinReady();
        com.wildfire.main.networking.appearance.NotificationPreferenceSync.tick();
        com.wildfire.main.networking.appearance.ListeningSync.tick();
        //~ if >=26.2 'client.screen' -> 'client.gui.screen()' {
        if (TOGGLE_KEYBIND.consumeClick() && client.gui.screen() == null) {
            ClientConfig.RENDER_BREASTS ^= true;
        }
        //~}
    }

    /// Clears all caches when the client player disconnects from a server/closes a singleplayer world
    private static void clientDisconnect(ClientPacketListener networkHandler, Minecraft client) {
        com.wildfire.client.music.SpotifyWebPlayer.disconnect();
        com.wildfire.client.music.PersonalMusicPlayer.stop();
        WildfireGender.CACHE.invalidateAll();
        EntityConfigHolder.CACHE.invalidateAll();
        AnimatedTorsoPhysics.clear();
        com.wildfire.client.render.TorsoMotionPhysics.clear();
        com.wildfire.main.networking.appearance.MusicClient.clear();
        com.wildfire.main.networking.appearance.SocialClient.clear();
        com.wildfire.main.networking.appearance.NotificationPreferenceSync.reset();
        com.wildfire.client.music.ListeningState.reset();
    }

    private static void clientJoin(ClientPacketListener var1, PacketSender var2, Minecraft client) {
        byte[] routeProbe = com.wildfire.client.route.RouteProbeClient.freshPayload();
        if (routeProbe != null) var2.sendPacket(new com.wildfire.main.networking.appearance.RouteProbePacket(routeProbe));
        // The server sync plugin is the only component allowed to open customization.
    }

    /// Play the relevant mod hurt sound when a player takes damage
    private static void onEntityHurt(LivingEntity entity, DamageSource damageSource) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        if (!(entity instanceof Player player) || !player.level().isClientSide()) {
            return;
        }

        PlayerConfigHolder genderPlayer = WildfireGender.getPlayerById(player.getUUID());
        if (genderPlayer == null || !genderPlayer.sounds().hurt().get()
            || !com.wildfire.client.config.CosmeticPreferences.modHurtSound()) {
            return;
        }

        SoundEvent hurtSound = genderPlayer.gender().get().getHurtSound();
        if (hurtSound != null) {
            float pitchVariation = (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.2F;
            player.playSound(hurtSound, 1f, pitchVariation + genderPlayer.sounds().voicePitch().get());
        }
    }

    /// Tick breast physics on entity tick
    private static void onEntityTick(LivingEntity entity) {
        if (EntityConfig.isSupportedEntity(entity)) {
            EntityConfigHolder<?> cfg = EntityConfigHolder.getEntity(entity);
            if (entity instanceof ArmorStand) {
                cfg.readFromStack(entity.getItemBySlot(EquipmentSlot.CHEST));
            }
            cfg.breastPhysics().tick(entity);
        }
    }
}
