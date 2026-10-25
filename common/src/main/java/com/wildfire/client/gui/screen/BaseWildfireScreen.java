package com.wildfire.client.gui.screen;

import com.wildfire.client.gui.IFancyFontRenderer;
import com.wildfire.client.gui.WildfireButton;
import com.wildfire.client.gui.WildfireSlider;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

public abstract class BaseWildfireScreen extends Screen implements IFancyFontRenderer {
   private static final float ENTITY_SCALE = 0.0625F;
   protected final UUID playerUUID;
   protected final @Nullable Screen parent;
   private long lastMSInitialized;

   protected BaseWildfireScreen(Component title, @Nullable Screen parent, UUID uuid) {
      super(title);
      this.parent = parent;
      this.playerUUID = uuid;
   }

   protected WildfireButton addButton(Consumer<WildfireButton.Builder> builder) {
      WildfireButton.Builder buttonBuilder = new WildfireButton.Builder();
      builder.accept(buttonBuilder);
      return (WildfireButton)this.addRenderableWidget(buttonBuilder.build(this.lastMSInitialized));
   }

   protected WildfireSlider addSlider(Consumer<WildfireSlider.Builder> builder) {
      WildfireSlider.Builder sliderBuilder = new WildfireSlider.Builder();
      sliderBuilder.save(var1x -> Objects.requireNonNull(this.getPlayer(), "getPlayer()").save());
      builder.accept(sliderBuilder);
      return (WildfireSlider)this.addRenderableWidget(sliderBuilder.build(this.lastMSInitialized));
   }

   public @Nullable PlayerConfigHolder getPlayer() {
      return WildfireGender.getPlayerById(this.playerUUID);
   }

   protected void renderPlayerInFrame(GuiGraphicsExtractor graphics, int xP, int yP, int mouseX, int mouseY) {
      LocalPlayer player = this.minecraft.player;
      if (player != null) {
         InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics, xP - 38, yP - 79, xP + 38, yP + 9, 70, getEntityScale(player, 0.4F), mouseX, mouseY, player
         );
      }
   }

   protected void init() {
      super.init();
      this.lastMSInitialized = Util.getMillis();
   }

   @Override
   public long getTimeOpened() {
      return this.lastMSInitialized;
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   protected static float getEntityScale(LivingEntity entity, float shift) {
      return getEntityScale(entity, shift, true);
   }

   protected static float getEntityScale(LivingEntity entity, float shift, boolean adjustByHeight) {
      float scale = 0.0625F;
      if (InventoryScreen.extractRenderState(entity) instanceof LivingEntityRenderState state && state.isUpsideDown) {
         scale = -scale;
         if (adjustByHeight) {
            state.boundingBoxWidth = state.boundingBoxWidth / state.scale;
            state.boundingBoxHeight = state.boundingBoxHeight / state.scale;
            scale -= state.boundingBoxHeight / 2.0F;
         }
      }

      return scale + shift;
   }
}
