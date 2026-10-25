package com.wildfire.client.gui.screen;

import com.google.common.base.Suppliers;
import com.wildfire.client.WildfireGenderClient;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.client.config.ClientConfigInstance;
import com.wildfire.client.gui.FakeGUIPlayer;
import com.wildfire.client.gui.IFancyFontRenderer;
import com.wildfire.client.gui.WildfireButton;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireLang;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.UnknownNullability;
import org.jspecify.annotations.Nullable;

public class WildfireFirstTimeSetupScreen extends BaseWildfireScreen {
   private static final Component DESCRIPTION = WildfireLang.FIRST_TIME_DESCRIPTION.translate();
   private static final Component NOTICE = WildfireLang.FIRST_TIME_NOTICE.translate();
   private static final int SCREEN_WIDTH = 274;
   private static final Component ENABLE_CLOUD_SYNCING = WildfireLang.FIRST_TIME_ENABLE.translateColored(TextColor.GREEN);
   private static final Component DISABLE_CLOUD_SYNCING = WildfireLang.FIRST_TIME_DISABLE.translateColored(TextColor.RED);
   private static final Identifier BACKGROUND = WildfireGender.id("textures/gui/first_time_bg.png");
   private static final UUID keiraUUID = UUID.fromString("372271ab-28f2-44bd-b585-95f43e010c22");
   private final Supplier<FakeGUIPlayer> fakeKeira = Suppliers.memoize(() -> new FakeGUIPlayer("KeiaraFGM", keiraUUID, FakeGUIPlayer.FEMALE_CHANGES));

   public WildfireFirstTimeSetupScreen(@Nullable Screen parent, UUID uuid) {
      super(WildfireLang.FIRST_TIME_TITLE.translate().withStyle(style -> style.withUnderlined(true)), parent, uuid);
   }

   @Override
   public void init() {
      super.init();
      int x = this.width / 2;
      int y = this.height / 2;
      var ref = new Object() {
         @UnknownNullability WildfireButton no;
      };
      this.addButton(builder -> builder.message(() -> ENABLE_CLOUD_SYNCING).position(x + 3, y + 74).size(128, 20).onPress(button -> {
         ClientConfigInstance config = ClientConfig.config();
         config.cloudSync().enabled().update(true);
         config.cloudSync().automatic().update(true);
         config.firstTimeLoad().update(false);
         button.active = false;
         button.setMessage(CommonComponents.ELLIPSIS);
         ref.no.setActive(false);
         WardrobeBrowserScreen nextScreen = new WardrobeBrowserScreen(null, this.playerUUID);
         this.doInitialSync().thenRun(() -> this.minecraft.execute(() -> this.minecraft.gui.setScreen(nextScreen)));
      }).tooltip(Tooltip.create(WildfireLang.FIRST_TIME_ENABLE_TOOLTIP.line(1).append("\n\n").append(WildfireLang.FIRST_TIME_ENABLE_TOOLTIP.line(2)))));
      ref.no = this.addButton(builder -> builder.message(() -> DISABLE_CLOUD_SYNCING).position(x - 131, y + 74).size(128, 20).onPress(var1x -> {
         ClientConfigInstance config = ClientConfig.config();
         config.cloudSync().enabled().update(false);
         config.cloudSync().automatic().update(false);
         config.firstTimeLoad().update(false);
         this.minecraft.gui.setScreen(new WardrobeBrowserScreen(null, this.playerUUID));
      }));
   }

   private CompletableFuture<Void> doInitialSync() {
      Minecraft client = Objects.requireNonNull(this.minecraft);
      assert client.player != null;
      UUID clientUUID = client.player.getUUID();
      WildfireGender.CACHE.asMap().values().removeIf(config -> config.syncStatus == PlayerConfigHolder.SyncStatus.UNKNOWN);
      return CompletableFuture.runAsync(() -> {
         PlayerConfigHolder clientConfig = WildfireGender.getOrAddPlayerById(clientUUID);
         if (!clientConfig.hasLocalConfig()) {
            try {
               WildfireGenderClient.loadGenderInfo(clientUUID, false, true).join();
            } catch (CompletionException var3) {
               return;
            } catch (Exception e) {
               WildfireGender.LOGGER.error("Failed to perform initial sync from the cloud", e);
               return;
            }

            clientConfig.save();
            clientConfig.needsCloudSync = false;
         } else {
            clientConfig.needsCloudSync = true;
         }
      });
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      this.extractTransparentBackground(graphics);
      graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, (this.width - 274) / 2, (this.height - 200) / 2, 0.0F, 0.0F, 274, 200, 512, 512);
   }

   public void tick() {
      super.tick();
      this.fakeKeira.get().tick();
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      super.extractRenderState(graphics, mouseX, mouseY, delta);
      int x = this.width / 2;
      int y = this.height / 2;
      this.drawScrollingString(graphics, this.getTitle(), x - 137, y - 24, IFancyFontRenderer.TextAlignment.CENTER, -12566464, 274, 6, false);
      this.drawScrollingString(
         graphics, WildfireLang.KEIRA.translateColored(TextColor.LIGHT_PURPLE), x - 63, y - 10, IFancyFontRenderer.TextAlignment.CENTER, -1, 191, 0, false
      );
      this.drawCenteredTextWrapped(graphics, DESCRIPTION, x + 32, y + 2, 191, -1);
      this.drawScaledScrollingString(graphics, NOTICE, x - 137, y + 63, IFancyFontRenderer.TextAlignment.CENTER, -12566464, 274, 6, false, 0.8F);
      ClientMannequin fakeKeira = this.fakeKeira.get().getEntity();
      InventoryScreen.extractEntityInInventoryFollowsMouse(
         graphics, x - 132, y - 13, x - 75, y + 60, 50, getEntityScale(fakeKeira, 0.4F), mouseX, mouseY, fakeKeira
      );
   }

   public void removed() {
      ClientConfig.INSTANCE.save();
      super.removed();
   }
}
