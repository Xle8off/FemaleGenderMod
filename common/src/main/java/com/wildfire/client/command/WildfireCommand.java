package com.wildfire.client.command;

import com.google.common.cache.Cache;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.client.gui.screen.WardrobeBrowserScreen;
import com.wildfire.client.gui.screen.WildfireFirstTimeSetupScreen;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireLang;
import com.wildfire.common.config.enums.SyncVerbosity;
import com.wildfire.common.entitydata.BreastDataComponent;
import com.wildfire.common.entitydata.EntityConfigHolder;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.UnknownNullability;

public class WildfireCommand {
   private static final Component COMMAND_PREFIX = WildfireLang.GENERIC_BRACKETS
      .translateColored(
         TextColor.GRAY,
         WildfireLang.GENERIC_CONCAT
            .translate(WildfireLang.MISC_F.translateColored(TextColor.LIGHT_PURPLE), WildfireLang.MISC_GM.translateColored(TextColor.WHITE))
      );

   public static <SOURCE extends SharedSuggestionProvider> void register(CommandDispatcher<SOURCE> dispatcher, ClientCommandHelper<SOURCE> helper) {
      LiteralArgumentBuilder<SOURCE> debug = (LiteralArgumentBuilder<SOURCE>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)helper.literalArgumentBuilder(
                           "debug"
                        )
                        .executes(
                           ctx -> {
                              sendHelp(
                                 ctx,
                                 helper,
                                 WildfireLang.DEBUG_COMMAND,
                                 WildfireLang.COMMAND_INVALIDATE_CACHE,
                                 WildfireLang.COMMAND_TARGET,
                                 WildfireLang.COMMAND_CACHE,
                                 WildfireLang.COMMAND_FIRST_TIME,
                                 WildfireLang.COMMAND_SYNC_VERBOSITY
                              );
                              helper.sendSystemMessage((SOURCE)ctx.getSource(), CommonComponents.EMPTY);
                              sendHelp(ctx, helper, WildfireLang.SINGLE_PLAYER_COMMAND, WildfireLang.COMMAND_TRIM, WildfireLang.COMMAND_ARMOR_STAND);
                              return 1;
                           }
                        ))
                     .then(helper.literalArgumentBuilder("invalidatecache").executes(ctx -> invalidateCache(ctx, helper))))
                  .then(helper.literalArgumentBuilder("target").executes(ctx -> getEntityLookingAt(ctx, helper))))
               .then(
                  helper.literalArgumentBuilder("firsttime")
                     .executes(
                        ctx -> {
                           Minecraft client = helper.getMinecraft((SOURCE)ctx.getSource());
                           client.execute(
                              () -> client.schedule(
                                 () -> client.gui.setScreen(new WildfireFirstTimeSetupScreen(null, helper.getPlayer((SOURCE)ctx.getSource()).getUUID()))
                              )
                           );
                           return 1;
                        }
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)helper.literalArgumentBuilder("cache")
                     .then(
                        ((RequiredArgumentBuilder)helper.argument("allPlayers", BoolArgumentType.bool()).executes(ctx -> getUsers(ctx, helper)))
                           .then(helper.argument("showEntities", BoolArgumentType.bool()).executes(ctx -> getUsers(ctx, helper)))
                     ))
                  .executes(ctx -> getUsers(ctx, helper))
            ))
         .then(
            helper.literalArgumentBuilder("syncverbosity")
               .then(helper.argument("level", new SyncVerbosity.SyncVerbosityArgumentType()).executes(ctx -> setLogLevel(ctx, helper)))
         );
      if (Minecraft.getInstance().isLocalServer()) {
         ((LiteralArgumentBuilder)debug.then(
               ((LiteralArgumentBuilder)helper.literalArgumentBuilder("trim")
                     .then(helper.argument("glint", BoolArgumentType.bool()).executes(ctx -> equipTrimmedChestplate(ctx, helper))))
                  .executes(ctx -> equipTrimmedChestplate(ctx, helper))
            ))
            .then(helper.literalArgumentBuilder("armorstand").executes(ctx -> spawnArmorStand(ctx, helper)));
      }

      LiteralCommandNode<SOURCE> root = dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)helper.literalArgumentBuilder("femalegender").executes(ctx -> openConfig(ctx, helper))).then(debug)
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)helper.literalArgumentBuilder("fgm").executes(ctx -> openConfig(ctx, helper))).redirect(root)
      );
   }

   private static <SOURCE extends SharedSuggestionProvider, T> @UnknownNullability("nullability depends on the relevant ArgumentType & defaultValue") T getOrDefault(
      CommandContext<SOURCE> ctx, String name, @UnknownNullability T defaultValue, Class<T> clazz
   ) {
      T value = defaultValue;

      try {
         value = (T)ctx.getArgument(name, clazz);
      } catch (IllegalArgumentException var6) {
      }

      return value;
   }

   public static <SOURCE extends SharedSuggestionProvider> void send(CommandContext<SOURCE> ctx, ClientCommandHelper<SOURCE> helper, Component text) {
      helper.sendSystemMessage((SOURCE)ctx.getSource(), WildfireLang.GENERIC_SPACE.translate(COMMAND_PREFIX, text));
   }

   public static <SOURCE extends SharedSuggestionProvider> void sendHelp(
      CommandContext<SOURCE> ctx, ClientCommandHelper<SOURCE> helper, WildfireLang header, WildfireLang... usageToDescription
   ) {
      List<Component> lines = new ArrayList<>();
      lines.add(WildfireLang.GENERIC_SPACE.translate(COMMAND_PREFIX, header.translate().withStyle(style -> style.withUnderlined(true))));

      for (WildfireLang langEntry : usageToDescription) {
         lines.add(
            WildfireLang.GENERIC_SPACE
               .translate(
                  COMMAND_PREFIX,
                  WildfireLang.GENERIC_DASH_EXPLANATION
                     .translateColored(TextColor.GRAY, langEntry.translateColored(TextColor.AQUA), langEntry.translateDescription().withColor(TextColor.WHITE))
               )
         );
      }

      helper.sendSystemMessage((SOURCE)ctx.getSource(), ComponentUtils.formatList(lines, CommonComponents.NEW_LINE));
   }

   private static <SOURCE extends SharedSuggestionProvider> int openConfig(CommandContext<SOURCE> ctx, ClientCommandHelper<SOURCE> helper) {
      Minecraft client = helper.getMinecraft((SOURCE)ctx.getSource());
      helper.getMinecraft((SOURCE)ctx.getSource()).schedule(() -> {
         LocalPlayer player = helper.getPlayer((SOURCE)ctx.getSource());
         if (player != null) {
            WardrobeBrowserScreen.open(client, player);
         }
      });
      return 1;
   }

   private static <SOURCE extends SharedSuggestionProvider> int getEntityLookingAt(CommandContext<SOURCE> ctx, ClientCommandHelper<SOURCE> helper) {
      Minecraft minecraft = helper.getMinecraft((SOURCE)ctx.getSource());
      Entity target = minecraft.crosshairPickEntity;
      if (target != null) {
         send(ctx, helper, WildfireLang.COMMAND_LOOKING_AT.translate(target.getName()));
         send(ctx, helper, WildfireLang.COMMAND_LOOKING_AT_UUID.translate(target.getStringUUID()));
         send(ctx, helper, WildfireLang.COMMAND_LOOKING_AT_TYPE.translate(target.getType()));
         send(ctx, helper, WildfireLang.COMMAND_LOOKING_AT_CLASS.translate(target.getClass()));
         send(ctx, helper, WildfireLang.COMMAND_LOOKING_AT_RENDERER.translate(minecraft.getEntityRenderDispatcher().getRenderer(target)));
      } else {
         send(ctx, helper, WildfireLang.COMMAND_LOOKING_AT_NONE.translate());
      }

      return 1;
   }

   public static <SOURCE extends SharedSuggestionProvider> int setLogLevel(CommandContext<SOURCE> ctx, ClientCommandHelper<SOURCE> helper) {
      SyncVerbosity level = (SyncVerbosity)ctx.getArgument("level", SyncVerbosity.class);
      if (ClientConfig.config().cloudSync().logVerbosity().update(level)) {
         ClientConfig.INSTANCE.save();
         send(ctx, helper, WildfireLang.COMMAND_LOG_LEVEL.translate(level));
         return 1;
      } else {
         return 0;
      }
   }

   private static <SOURCE extends SharedSuggestionProvider> int getUsers(CommandContext<SOURCE> ctx, ClientCommandHelper<SOURCE> helper) {
      boolean allPlayers = getOrDefault(ctx, "allPlayers", false, Boolean.class);
      boolean showEntities = getOrDefault(ctx, "showEntities", false, Boolean.class);
      Level level = helper.getLevel((SOURCE)ctx.getSource());
      List<Component> players = dump(WildfireGender.CACHE, level, !allPlayers);
      if (!players.isEmpty()) {
         send(ctx, helper, WildfireLang.COMMAND_SYNCED_PLAYERS.translate(players.size()));

         for (Component line : players) {
            send(ctx, helper, line);
         }
      }

      if (showEntities) {
         List<Component> entities = dump(EntityConfigHolder.CACHE, level, false);
         if (!entities.isEmpty()) {
            send(ctx, helper, WildfireLang.COMMAND_ENTITIES.translate(entities.size()));

            for (Component line : entities) {
               send(ctx, helper, line);
            }
         }
      }

      return 1;
   }

   private static List<Component> dump(Cache<UUID, ? extends EntityConfigHolder<?>> cache, Level world, boolean ignoreEmptyConfig) {
      List<Component> lines = new ArrayList<>();

      for (Entry<UUID, ? extends EntityConfigHolder<?>> entry : cache.asMap().entrySet()) {
         UUID uuid = entry.getKey();
         EntityConfigHolder<?> config = (EntityConfigHolder<?>)entry.getValue();
         if (config != null
            && !(
               config instanceof PlayerConfigHolder playerConfig && playerConfig.getSyncStatus() == PlayerConfigHolder.SyncStatus.UNKNOWN && ignoreEmptyConfig
            )) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
               MutableComponent info = ComponentUtils.formatList(config.getDebugInfo(), CommonComponents.NEW_LINE, Component::literal);
               lines.add(
                  WildfireLang.GENERIC_DASH_EXPLANATION
                     .translate(entity.getDisplayName(), config.gender().get().getDisplayName())
                     .withStyle(style -> style.withHoverEvent(new ShowText(info)))
               );
            }
         }
      }

      return lines;
   }

   private static <SOURCE extends SharedSuggestionProvider> int invalidateCache(CommandContext<SOURCE> ctx, ClientCommandHelper<SOURCE> helper) {
      WildfireGender.CACHE.invalidateAll();
      EntityConfigHolder.CACHE.invalidateAll();
      send(ctx, helper, WildfireLang.COMMAND_INVALIDATE_CACHE_SUCCESS.translate());
      return 1;
   }

   private static <SOURCE extends SharedSuggestionProvider> ServerPlayer getIntegratedServerPlayer(
      CommandContext<SOURCE> ctx, ClientCommandHelper<SOURCE> helper
   ) {
      IntegratedServer integratedServer = Objects.requireNonNull(helper.getMinecraft((SOURCE)ctx.getSource()).getSingleplayerServer());
      PlayerList playerManager = Objects.requireNonNull(integratedServer.getPlayerList());
      return Objects.requireNonNull(playerManager.getPlayer(helper.getPlayer((SOURCE)ctx.getSource()).getUUID()));
   }

   private static <SOURCE extends SharedSuggestionProvider> int equipTrimmedChestplate(CommandContext<SOURCE> ctx, ClientCommandHelper<SOURCE> helper) {
      Boolean glint = getOrDefault(ctx, "glint", null, Boolean.class);
      ServerPlayer player = getIntegratedServerPlayer(ctx, helper);
      if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
         return 0;
      }

      ItemStack item = new ItemStack(Items.IRON_CHESTPLATE);
      Reference<TrimMaterial> material = player.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.AMETHYST);
      Reference<TrimPattern> pattern = player.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.COAST);
      item.set(DataComponents.TRIM, new ArmorTrim(material, pattern));
      if (glint != null) {
         item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint);
      }

      player.setItemSlot(EquipmentSlot.CHEST, item);
      return 1;
   }

   private static <SOURCE extends SharedSuggestionProvider> int spawnArmorStand(CommandContext<SOURCE> ctx, ClientCommandHelper<SOURCE> helper) {
      ServerPlayer player = getIntegratedServerPlayer(ctx, helper);
      if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
         return 0;
      } else {
         ServerLevel world = player.level();
         ItemStack item = new ItemStack(Items.IRON_CHESTPLATE);
         PlayerConfigHolder config = WildfireGender.getOrAddPlayerById(player.getUUID());
         BreastDataComponent component = BreastDataComponent.fromPlayer(player, config);
         if (component == null) {
            helper.sendFailure((SOURCE)ctx.getSource(), WildfireLang.COMMAND_ARMOR_STAND_NO_COMPONENT.translateColored(TextColor.RED));
            return 0;
         } else {
            component.write(item);
            ArmorStand stand = new ArmorStand(world, player.getBlockX(), player.getBlockY(), player.getBlockZ());
            stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            stand.setItemSlot(EquipmentSlot.CHEST, item);
            stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            world.addFreshEntity(stand);
            return 1;
         }
      }
   }
}
