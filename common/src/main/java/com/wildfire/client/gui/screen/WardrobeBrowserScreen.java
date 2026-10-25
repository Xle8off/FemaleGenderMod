package com.wildfire.client.gui.screen;

import com.wildfire.api.Gender;
import com.wildfire.client.cloud.CloudSync;
import com.wildfire.client.cloud.SyncUnavailable;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.client.contributors.Contributors;
import com.wildfire.client.gui.IFancyFontRenderer;
import com.wildfire.client.gui.SyncedPlayerList;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireLang;
import com.wildfire.common.config.enums.ShowPlayerListMode;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.time.Month;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

public class WardrobeBrowserScreen extends BaseWildfireScreen {
   private static final Identifier BACKGROUND_MALE = WildfireGender.id("textures/gui/wardrobe_bg_male.png");
   private static final Identifier BACKGROUND_FEMALE = WildfireGender.id("textures/gui/wardrobe_bg_female.png");
   private static final Identifier BACKGROUND_OTHER = WildfireGender.id("textures/gui/wardrobe_bg_other.png");
   private static final Identifier TXTR_RIBBON = WildfireGender.id("bc_ribbon");
   private static final Identifier CLOUD_ICON = WildfireGender.id("cloud");
   private static final boolean isBreastCancerAwarenessMonth = Month.from(ZonedDateTime.now()) == Month.OCTOBER;
   private final WidgetTooltipHolder contribTooltip = new WidgetTooltipHolder();

   public WardrobeBrowserScreen(@Nullable Screen parent, UUID uuid) {
      super(WildfireLang.WARDROBE_TITLE.translate(), parent, uuid);
   }

   public static BaseWildfireScreen create(LocalPlayer player, @Nullable Screen parent) {
      return ClientConfig.config().firstTimeLoad().get() && CloudSync.isAvailable()
         ? new WildfireFirstTimeSetupScreen(parent, player.getUUID())
         : new WardrobeBrowserScreen(parent, player.getUUID());
   }

   public static void open(Minecraft client, LocalPlayer player) {
      client.gui.setScreen(create(player, null));
   }

   @Override
   public void init() {
      super.init();
      Minecraft client = Objects.requireNonNull(this.minecraft, "client");
      int y = this.height / 2;
      PlayerConfigHolder plr = Objects.requireNonNull(this.getPlayer(), "getPlayer()");
      this.addButton(
         builder -> builder.message(() -> WildfireLang.PLAYER_LIST_MODE.translate(ClientConfig.config().playerListMode().get().getTranslatedName()))
            .tooltip(ClientConfig.config().playerListMode().get().tooltip())
            .position(126, 4)
            .size(185, 10)
            .onPress(button -> {
               if (ClientConfig.config().playerListMode().update(ShowPlayerListMode::next)) {
                  ClientConfig.INSTANCE.save();
                  button.updateMessage();
                  button.setTooltip(ClientConfig.config().playerListMode().get().tooltip());
               }
            })
      );
      this.addButton(
         builder -> builder.message(() -> plr.gender().get().getDisplayName())
            .position(this.width / 2 - 130, this.height / 2 + 33)
            .size(80, 15)
            .onPress(var2x -> {
               if (plr.gender().update(Gender::next)) {
                  plr.save();
                  this.rebuildWidgets();
               }
            })
      );
      this.addButton(
         builder -> builder.message(() -> WildfireLang.GENERIC_ELLIPSIS_SUFFIX.translate(WildfireLang.APPEARANCE_SETTINGS_TITLE))
            .position(this.width / 2 - 36, this.height / 2 - 63)
            .size(157, 20)
            .onPress(var2x -> client.gui.setScreen(new WildfireBreastCustomizationScreen(this, this.playerUUID)))
            .active(plr.gender().get().canHaveBreasts())
      );
      this.addButton(
         builder -> {
            builder.message(WildfireLang.CLOUD_SETTINGS::translate);
            builder.position(this.width / 2 - 36, y + 30);
            builder.size(24, 18);
            builder.renderer(
               (button, ctx, var2x, var3x, var4x) -> ctx.blitSprite(RenderPipelines.GUI_TEXTURED, CLOUD_ICON, button.getX() + 2, button.getY() + 2, 20, 14)
            );
            builder.onPress(var2x -> client.gui.setScreen(new WildfireCloudSyncScreen(this, this.playerUUID)));
            SyncUnavailable cloudUnavailable = CloudSync.unavailableReason();
            if (cloudUnavailable != null) {
               builder.tooltip(Tooltip.create(cloudUnavailable.text()));
               builder.active(false);
            } else {
               builder.tooltip(Tooltip.create(WildfireLang.CLOUD_TOOLTIP.translate()));
            }
         }
      );
      this.addButton(
         builder -> builder.message(() -> WildfireLang.GENERIC_ELLIPSIS_SUFFIX.translate(WildfireLang.CREDITS_TITLE))
            .position(this.width / 2 + 2, this.height / 2 + 33)
            .size(78, 15)
            .onPress(var2x -> client.gui.setScreen(new WildfireCreditsScreen(this, this.playerUUID)))
      );
      this.addButton(
         builder -> builder.message(() -> Component.literal("⚙"))
            .position(this.width / 2 + 84, this.height / 2 + 33)
            .size(22, 15)
            .onPress(var2x -> client.gui.setScreen(new CosmeticSettingsScreen(this, this.playerUUID)))
      );
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      this.extractTransparentBackground(graphics);
      PlayerConfigHolder plr = this.getPlayer();
      if (plr != null) {
         Identifier backgroundTexture = switch ((Gender)plr.gender().get()) {
            case MALE -> BACKGROUND_MALE;
            case FEMALE -> BACKGROUND_FEMALE;
            case OTHER -> BACKGROUND_OTHER;
         };
         graphics.blit(RenderPipelines.GUI_TEXTURED, backgroundTexture, (this.width - 272) / 2, (this.height - 138) / 2, 0.0F, 0.0F, 268, 124, 512, 512);
         this.renderPlayerInFrame(graphics, this.width / 2 - 90, this.height / 2 + 18, mouseX, mouseY);
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      super.extractRenderState(graphics, mouseX, mouseY, delta);
      int x = this.width / 2;
      int y = this.height / 2;
      this.drawScrollingString(graphics, this.getTitle(), 0, y - 82, IFancyFontRenderer.TextAlignment.CENTER, -1, graphics.guiWidth(), 5, false);
      this.drawCreatorContributorText(graphics, mouseX, mouseY, y + 65 + (isBreastCancerAwarenessMonth ? 30 : 0));
      if (isBreastCancerAwarenessMonth) {
         int bcaY = y - 45;
         graphics.fill(x - 159, bcaY + 106, x + 159, bcaY + 136, ARGB.black(85));
         this.drawScrollingString(
            graphics,
            WildfireLang.CANCER_AWARENESS_TITLE.translate().withStyle(style -> style.withBold(true).withItalic(true)),
            x - 153,
            bcaY + 117,
            IFancyFontRenderer.TextAlignment.LEFT,
            -1,
            283,
            5,
            false
         );
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TXTR_RIBBON, x + 130, bcaY + 109, 26, 26);
      }

      SyncedPlayerList.drawSyncedPlayers(this, graphics, 126, graphics.guiWidth());
   }

   private void drawCreatorContributorText(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int creatorY) {
      Minecraft client = Objects.requireNonNull(this.minecraft);
      if (client.player != null && client.level != null) {
         Map<UUID, PlayerInfo> entries = client.player
            .connection
            .getOnlinePlayers()
            .stream()
            .collect(Collectors.toMap(entry -> entry.getProfile().id(), Function.identity()));
         boolean withCreator = entries.containsKey(Contributors.CREATOR_UUID);
         List<PlayerInfo> foundContributors = Contributors.getContributorUUIDs()
            .stream()
            .filter(it -> !it.equals(Contributors.CREATOR_UUID))
            .map(entries::get)
            .filter(Objects::nonNull)
            .toList();
         if (withCreator || !foundContributors.isEmpty()) {
            ArrayList<PlayerInfo> toList = new ArrayList<>(foundContributors);
            Component text;
            if (withCreator && !foundContributors.isEmpty()) {
               text = WildfireLang.LABEL_WITH_BOTH.translate();
               toList.addFirst(entries.get(Contributors.CREATOR_UUID));
            } else if (withCreator) {
               text = WildfireLang.LABEL_WITH_CREATOR.translate();
            } else {
               text = WildfireLang.LABEL_WITH_CONTRIBUTOR.translate();
            }

            int textWidth = this.font.width(text);
            this.drawCenteredTextWrapped(graphics, text, this.width / 2, creatorY, 300, -65281);
            int lines = Mth.ceil(textWidth / 300.0);
            if (!toList.isEmpty()
               && mouseX > this.width / 2 - textWidth / 2
               && mouseX < this.width / 2 + textWidth / 2
               && mouseY > creatorY - 2
               && mouseY < creatorY + 9 * lines) {
               List<MutableComponent> contributorNames = toList.stream()
                  .map(entry -> PlayerTeam.formatNameForTeam(entry.getTeam(), Component.literal(entry.getProfile().name())))
                  .toList();
               this.contribTooltip.set(Tooltip.create(ComponentUtils.formatList(contributorNames, CommonComponents.NEW_LINE)));
               this.contribTooltip.refreshTooltipForNextRenderPass(graphics, mouseX, mouseY, true, true, ScreenRectangle.empty());
            }
         }
      }
   }
}
