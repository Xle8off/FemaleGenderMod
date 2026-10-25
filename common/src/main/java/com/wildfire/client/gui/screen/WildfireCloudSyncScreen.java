package com.wildfire.client.gui.screen;

import com.wildfire.client.cloud.CloudSync;
import com.wildfire.client.cloud.SyncLog;
import com.wildfire.client.cloud.SyncingTooFrequentlyException;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.client.gui.IFancyFontRenderer;
import com.wildfire.client.gui.WildfireButton;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireLang;
import com.wildfire.common.config.value.ConfigValue;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.UnknownNullability;

public class WildfireCloudSyncScreen extends BaseWildfireScreen {
   private static final Identifier BACKGROUND = WildfireGender.id("textures/gui/sync_bg_v2.png");
   private static final Component ENABLED = WildfireLang.LABEL_ENABLED.translateColored(TextColor.GREEN);
   private static final Component DISABLED = WildfireLang.LABEL_DISABLED.translateColored(TextColor.RED);

   protected WildfireCloudSyncScreen(Screen parent, UUID uuid) {
      super(WildfireLang.CLOUD_SETTINGS.translate(), parent, uuid);
   }

   @Override
   public void init() {
      super.init();
      int x = this.width / 2;
      int y = this.height / 2;
      int yPos = y - 47;
      int xPos = x - 78 - 1;
      var ref = new Object() {
         @UnknownNullability WildfireButton btnSyncNow;
         @UnknownNullability WildfireButton btnDelete;
         @UnknownNullability WildfireButton btnAutomaticSync;
      };
      this.addButton(
         builder -> builder.message(() -> WildfireLang.CLOUD_STATUS.translate(CloudSync.isEnabled() ? ENABLED : DISABLED))
            .position(xPos, yPos)
            .size(157, 20)
            .onPress(button -> {
               if (ClientConfig.config().cloudSync().enabled().update(ConfigValue.TOGGLE)) {
                  boolean available = CloudSync.isAvailable();
                  boolean enabled = ClientConfig.config().cloudSync().enabled().get();
                  button.updateMessage();
                  ref.btnAutomaticSync.setActive(enabled);
                  ref.btnSyncNow.setVisible(enabled && available);
                  ref.btnDelete.setVisible(!enabled && available);
                  ref.btnAutomaticSync.updateMessage();
               }
            })
      );
      ref.btnAutomaticSync = this.addButton(
         builder -> builder.message(
               () -> WildfireLang.CLOUD_AUTOMATIC
                  .translate(
                     CloudSync.isEnabled() ? (ClientConfig.config().cloudSync().automatic().get() ? ENABLED : DISABLED) : WildfireLang.LABEL_OFF.translate()
                  )
            )
            .position(xPos, yPos + 20)
            .size(157, 20)
            .onPress(button -> {
               if (ClientConfig.config().cloudSync().automatic().update(ConfigValue.TOGGLE)) {
                  button.updateMessage();
               }
            })
            .tooltip(Tooltip.create(WildfireLang.CLOUD_AUTOMATIC_TOOLTIP.line(1).append("\n\n").append(WildfireLang.CLOUD_AUTOMATIC_TOOLTIP.line(2))))
            .active(CloudSync.isEnabled())
      );
      ref.btnSyncNow = this.addButton(
         builder -> builder.message(WildfireLang.CLOUD_SYNC::translate).position(xPos + 98, yPos + 42).size(60, 15).onPress(this::sync)
      );
      ref.btnSyncNow.setVisible(CloudSync.isEnabled());
      ref.btnDelete = this.addButton(
         builder -> builder.message(() -> WildfireLang.CLOUD_DELETE.translateColored(TextColor.RED))
            .position(xPos + 98, yPos + 42)
            .size(60, 15)
            .onPress(this::delete)
      );
      ref.btnDelete.setVisible(!CloudSync.isEnabled());
      this.addButton(
         builder -> builder.message(() -> Component.literal("X"))
            .position(this.width / 2 + 74, yPos - 11)
            .size(8, 9)
            .onPress(var1x -> this.onClose())
            .narration(var0 -> Component.translatable("gui.narrate.button", new Object[]{Component.translatable("gui.done")}))
      );
   }

   private void sync(Button button) {
      button.active = false;
      button.setMessage(WildfireLang.CLOUD_SYNCING.translate());
      CompletableFuture.runAsync(() -> {
         try {
            CloudSync.sync(Objects.requireNonNull(this.getPlayer())).join();
            button.setMessage(WildfireLang.CLOUD_SYNCING_SUCCESS.translate());
         } catch (Exception e) {
            Throwable actualException = e instanceof CompletionException ce ? ce.getCause() : e;
            if (actualException instanceof SyncingTooFrequentlyException) {
               WildfireGender.LOGGER.warn("Failed to sync settings as we've already synced too recently");
               SyncLog.add(WildfireLang.SYNC_LOG_TOO_FREQUENT);
            } else {
               WildfireGender.LOGGER.error("Failed to sync settings", actualException);
            }

            button.setMessage(WildfireLang.CLOUD_SYNCING_FAIL.translate());
         }
      });
   }

   private void delete(Button widget) {
      widget.active = false;
      CompletableFuture.runAsync(() -> {
         try {
            CloudSync.deleteProfile(Objects.requireNonNull(this.getPlayer())).join();
            widget.setMessage(WildfireLang.CLOUD_DELETED.translate());
         } catch (Exception e) {
            WildfireGender.LOGGER.error("Failed to delete cloud sync profile", e);
            widget.setMessage(WildfireLang.CLOUD_DELETE_FAILED.translate());
         }
      });
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      this.extractTransparentBackground(graphics);
      graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, (this.width - 172) / 2, (this.height - 124) / 2, 0.0F, 0.0F, 172, 144, 256, 256);
      graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, (this.width - 172) / 2, (this.height - 124) / 2, 0.0F, 0.0F, 172, 144, 256, 256);
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      if (this.minecraft.level != null) {
         super.extractRenderState(graphics, mouseX, mouseY, delta);
         int x = this.width / 2;
         int y = this.height / 2;
         y -= 47;
         this.drawScrollingString(graphics, this.getTitle(), x - 79, y - 12, IFancyFontRenderer.TextAlignment.LEFT, -12303292, 141, 11, 0, false);
         this.drawScrollingString(
            graphics, WildfireLang.CLOUD_STATUS_LOG.translate(), x - 79, y + 47, IFancyFontRenderer.TextAlignment.LEFT, -12303292, 95, 11, 0, false
         );

         for (int i = SyncLog.SYNC_LOG.size() - 1; i >= 0; i--) {
            int reverseIndex = SyncLog.SYNC_LOG.size() - 1 - i;
            SyncLog.Entry entry = SyncLog.SYNC_LOG.get(i);
            if (reverseIndex < 6) {
               int ey = y + 110 - reverseIndex * 10;
               this.drawScrollingString(graphics, entry.text(), x - 78, ey, IFancyFontRenderer.TextAlignment.LEFT, entry.color(), 156, 10, 0, false);
            }
         }
      }
   }

   public void removed() {
      ClientConfig.INSTANCE.save();
      super.removed();
   }
}
