package com.wildfire.client.gui.screen;

import com.wildfire.api.Gender;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.client.gui.IFancyFontRenderer;
import com.wildfire.client.gui.WildfireSlider;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireLang;
import com.wildfire.common.config.GenderConfigTranslations;
import com.wildfire.common.config.value.ConfigValue;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.UnknownNullability;

public class WildfireBreastCustomizationScreen extends BaseWildfireScreen {
   private static final int FULL_WIDTH = 166;
   private static final int HALF_WIDTH = 81;
   private static final Component ENABLED = WildfireLang.LABEL_ENABLED.translateColored(TextColor.GREEN);
   private static final Component DISABLED = WildfireLang.LABEL_DISABLED.translateColored(TextColor.RED);
   private static final Identifier BACKGROUND_FEMALE = WildfireGender.id("textures/gui/breast_customization.png");
   private static final Identifier BACKGROUND_OTHER = WildfireGender.id("textures/gui/breast_customization_other.png");
   private static final Identifier BACKGROUND_CUSTOMIZATION = WildfireGender.id("textures/gui/tabs/breast_customization_tab.png");
   private static final Identifier BACKGROUND_PHYSICS = WildfireGender.id("textures/gui/tabs/breast_physics_tab.png");
   private static final Identifier BACKGROUND_MISC = WildfireGender.id("textures/gui/tabs/miscellaneous_tab.png");
   private WildfireBreastCustomizationScreen.Tab currentTab = WildfireBreastCustomizationScreen.Tab.CUSTOMIZATION;

   public WildfireBreastCustomizationScreen(Screen parent, UUID uuid) {
      super(WildfireLang.APPEARANCE_SETTINGS_TITLE.translate(), parent, uuid);
   }

   @Override
   public void init() {
      super.init();
      int y = this.height / 2 - 11;
      this.addButton(
         builder -> builder.message(WildfireLang.CUSTOMIZATION_TAB_CUSTOMIZATION::translate)
            .position(this.width / 2 - 130, y - 52)
            .size(84, 12)
            .onPress(var1x -> {
               this.currentTab = WildfireBreastCustomizationScreen.Tab.CUSTOMIZATION;
               this.rebuildWidgets();
            })
            .active(this.currentTab != WildfireBreastCustomizationScreen.Tab.CUSTOMIZATION)
      );
      this.addButton(
         builder -> builder.message(WildfireLang.CUSTOMIZATION_TAB_PHYSICS::translate).position(this.width / 2 - 42, y - 52).size(84, 12).onPress(var1x -> {
            this.currentTab = WildfireBreastCustomizationScreen.Tab.PHYSICS;
            this.rebuildWidgets();
         }).active(this.currentTab != WildfireBreastCustomizationScreen.Tab.PHYSICS)
      );
      this.addButton(
         builder -> builder.message(WildfireLang.CUSTOMIZATION_TAB_MISC::translate).position(this.width / 2 + 46, y - 52).size(84, 12).onPress(var1x -> {
            this.currentTab = WildfireBreastCustomizationScreen.Tab.MISC;
            this.rebuildWidgets();
         }).active(this.currentTab != WildfireBreastCustomizationScreen.Tab.MISC)
      );
      int tabOffsetY = y - 3 - 21;
      switch (this.currentTab) {
         case CUSTOMIZATION:
            this.initCustomizationTab(tabOffsetY);
            break;
         case PHYSICS:
            this.initPhysicsTab(tabOffsetY);
            break;
         case MISC:
            this.initMiscTab(tabOffsetY);
      }

      this.disableJump();
   }

   public void removed() {
      this.disableJump();
   }

   private boolean disableJump() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && player.autoJumpTime > 0) {
         player.autoJumpTime = 0;
         return true;
      } else {
         return false;
      }
   }

   private void initCustomizationTab(int tabOffsetY) {
      PlayerConfigHolder plr = Objects.requireNonNull(this.getPlayer(), "getPlayer()");
      this.addSlider(
         builder -> builder.message(value -> WildfireLang.WARDROBE_SLIDER_BREAST_SIZE.translate(Math.round(value * 1.25F * 100.0F)))
            .position(this.width / 2 - 36, tabOffsetY - 2)
            .size(166, 20)
            .forConfig(() -> plr.breasts().bustSize())
            .step(0.01)
            .mouseStep(0.001)
      );
      this.addSlider(
         builder -> builder.message(value -> WildfireLang.WARDROBE_SLIDER_SEPARATION.translate(Math.round(Math.round(value * 100.0F) / 100.0F * 10.0F)))
            .position(this.width / 2 - 36, tabOffsetY + 22)
            .size(81, 20)
            .forConfig(() -> plr.breasts().xOffset())
            .mouseStep(0.05)
      );
      this.addSlider(
         builder -> builder.message(value -> WildfireLang.WARDROBE_SLIDER_HEIGHT.translate(Math.round(Math.round(value * 100.0F) / 100.0F * 10.0F)))
            .position(this.width / 2 - 36 + 81 + 4, tabOffsetY + 22)
            .size(81, 20)
            .forConfig(() -> plr.breasts().yOffset())
            .mouseStep(0.05)
      );
      this.addSlider(
         builder -> builder.message(value -> WildfireLang.WARDROBE_SLIDER_DEPTH.translate(Math.round(Math.round(value * 100.0F) / 100.0F * 10.0F)))
            .position(this.width / 2 - 36, tabOffsetY + 46)
            .size(81, 20)
            .forConfig(() -> plr.breasts().zOffset())
            .step(0.1)
            .mouseStep(0.05)
      );
      this.addSlider(
         builder -> builder.message(value -> WildfireLang.WARDROBE_SLIDER_ROTATION.translate(Math.round(Math.round(value * 100.0F) / 100.0F * 100.0F)))
            .position(this.width / 2 - 36 + 81 + 4, tabOffsetY + 46)
            .size(81, 20)
            .forConfig(() -> plr.breasts().cleavage())
            .step(0.1)
            .mouseStep(0.1)
      );
      this.addButton(
         builder -> builder.message(WildfireLang.UV_EDITOR::translate)
            .position(this.width / 2 - 36, tabOffsetY + 70)
            .size(166, 20)
            .onPress(var1x -> this.minecraft.gui.setScreen(new WildfireBreastUVEditorScreen(this, this.playerUUID)))
      );
   }

   private void initPhysicsTab(int tabOffsetY) {
      PlayerConfigHolder plr = Objects.requireNonNull(this.getPlayer(), "getPlayer()");
      var ref = new Object() {
         @UnknownNullability AbstractWidget bounceSlider;
         @UnknownNullability AbstractWidget floppySlider;
         @UnknownNullability AbstractWidget overridePhysics;
         @UnknownNullability AbstractWidget dualPhysics;
      };
      this.addButton(
         builder -> builder.message(WildfireLang.CHAR_SETTINGS_JUMP::translate)
            .position(this.width / 2 - 130, this.height / 2 + 65)
            .size(80, 15)
            .onPress(button -> {
               if (this.disableJump()) {
                  button.setMessage(WildfireLang.CHAR_SETTINGS_JUMP.translate());
               } else {
                  LocalPlayer player = Minecraft.getInstance().player;
                  if (player != null) {
                     player.autoJumpTime = 24000;
                     button.setMessage(WildfireLang.CHAR_SETTINGS_JUMPING.translate());
                  }
               }
            })
      );
      this.addButton(
         builder -> builder.message(() -> WildfireLang.CHAR_SETTINGS_PHYSICS.translate(plr.breasts().physics().enabled().get() ? ENABLED : DISABLED))
            .position(this.width / 2 - 36, tabOffsetY - 2)
            .size(166, 20)
            .onPress(button -> {
               if (plr.breasts().physics().enabled().update(ConfigValue.TOGGLE)) {
                  plr.save();
                  button.updateMessage();
                  boolean breastPhysics = plr.breasts().physics().enabled().get();
                  ref.bounceSlider.active = breastPhysics;
                  ref.floppySlider.active = breastPhysics;
                  ref.overridePhysics.active = breastPhysics;
                  ref.dualPhysics.active = breastPhysics;
               }
            })
      );
      ref.dualPhysics = this.addButton(
         builder -> builder.message(
               () -> WildfireLang.CUSTOMIZATION_DUAL_PHYSICS
                  .translate(plr.breasts().physics().uniboob().get() ? CommonComponents.GUI_NO : CommonComponents.GUI_YES)
            )
            .position(this.width / 2 - 36, tabOffsetY + 22)
            .size(166, 20)
            .onPress(button -> {
               if (plr.breasts().physics().uniboob().update(ConfigValue.TOGGLE)) {
                  plr.save();
                  button.updateMessage();
               }
            })
            .active(plr.breasts().physics().enabled())
      );
      ref.overridePhysics = this.addButton(
         builder -> builder.message(
               () -> WildfireLang.CHAR_SETTINGS_OVERRIDE_PHYSICS.translate(ClientConfig.config().overrides().armorPhysics().get() ? ENABLED : DISABLED)
            )
            .position(this.width / 2 - 36, tabOffsetY + 70)
            .size(166, 20)
            .onPress(button -> {
               if (ClientConfig.config().overrides().armorPhysics().update(ConfigValue.TOGGLE)) {
                  ClientConfig.INSTANCE.save();
                  button.updateMessage();
               }
            })
            .tooltip(Tooltip.create(GenderConfigTranslations.CLIENT_OVERRIDE_ARMOR_PHYSICS.tooltipComponent()))
            .active(plr.breasts().physics().enabled())
      );
      ref.bounceSlider = this.addSlider(
         builder -> builder.message(value -> WildfireLang.WARDROBE_SLIDER_BOUNCE.translate(Math.round(3.0F * value * 100.0F)))
            .position(this.width / 2 - 36, tabOffsetY + 46)
            .size(81, 20)
            .forConfig(() -> plr.breasts().physics().bounceMultiplier())
            .step(0.005)
            .active(plr.breasts().physics().enabled())
      );
      ref.floppySlider = this.addSlider(
         builder -> builder.message(value -> WildfireLang.WARDROBE_SLIDER_FLOPPY.translate(Math.round(value * 100.0F)))
            .position(this.width / 2 - 36 + 81 + 2, tabOffsetY + 46)
            .size(81, 20)
            .forConfig(() -> plr.breasts().physics().floppiness())
            .step(0.01)
            .active(plr.breasts().physics().enabled())
      );
   }

   private void initMiscTab(int tabOffsetY) {
      PlayerConfigHolder plr = Objects.requireNonNull(this.getPlayer(), "getPlayer()");
      var ref = new Object() {
         @UnknownNullability AbstractWidget pitchSlider;
      };
      this.addButton(
         builder -> builder.message(() -> WildfireLang.CHAR_SETTINGS_HURT_SOUNDS.translate(plr.sounds().hurt().get() ? ENABLED : DISABLED))
            .position(this.width / 2 - 36, tabOffsetY - 2)
            .size(166, 20)
            .onPress(button -> {
               if (plr.sounds().hurt().update(ConfigValue.TOGGLE)) {
                  plr.save();
                  ref.pitchSlider.active = plr.sounds().hurt().get();
                  button.updateMessage();
               }
            })
            .tooltip(Tooltip.create(WildfireLang.CHAR_SETTINGS_HURT_SOUNDS_TOOLTIP.translate()))
      );
      ref.pitchSlider = this.addSlider(
         builder -> builder.message(value -> WildfireLang.WARDROBE_SLIDER_PITCH.translate(Math.round(value * 100.0F)))
            .position(this.width / 2 - 36, tabOffsetY + 22)
            .size(81, 20)
            .forConfig(() -> plr.sounds().voicePitch())
            .save(var2x -> {
               plr.save();
               LocalPlayer clientPlayer = Objects.requireNonNull(this.minecraft).player;
               if (clientPlayer != null) {
                  plr.tryPlayHurtSound(clientPlayer);
               }
            })
            .step(0.01)
            .active(plr.sounds().hurt())
      );
      this.addButton(
         builder -> builder.message(() -> WildfireLang.CHAR_SETTINGS_HIDE_IN_ARMOR.translate(plr.showBreastsInArmor().get() ? DISABLED : ENABLED))
            .position(this.width / 2 - 36, tabOffsetY + 46)
            .size(166, 20)
            .onPress(button -> {
               if (plr.showBreastsInArmor().update(ConfigValue.TOGGLE)) {
                  plr.save();
                  button.updateMessage();
               }
            })
      );
      this.addButton(
         builder -> builder.message(() -> WildfireLang.CHAR_SETTINGS_ARMOR_STAT.translate(ClientConfig.config().armorStat().get() ? ENABLED : DISABLED))
            .position(this.width / 2 - 36, tabOffsetY + 70)
            .size(166, 20)
            .onPress(button -> {
               if (ClientConfig.config().armorStat().update(ConfigValue.TOGGLE)) {
                  ClientConfig.INSTANCE.save();
                  button.updateMessage();
               }
            })
      );
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      this.extractTransparentBackground(graphics);
      PlayerConfigHolder plr = this.getPlayer();
      if (plr != null) {
         Identifier backgroundTexture = switch ((Gender)plr.gender().get()) {
            case FEMALE -> BACKGROUND_FEMALE;
            case OTHER -> BACKGROUND_OTHER;
            default -> null;
         };
         if (backgroundTexture != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, backgroundTexture, (this.width - 272) / 2, (this.height - 138) / 2, 0.0F, 0.0F, 272, 130, 512, 512);
         }

         graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            this.currentTab.background,
            this.width / 2 - 42,
            this.height / 2 - 43,
            0.0F,
            0.0F,
            178,
            this.currentTab.backgroundHeight,
            512,
            512
         );
         this.drawScrollingString(
            graphics, this.getTitle(), 0, this.height / 2 - 82, IFancyFontRenderer.TextAlignment.CENTER, -1, graphics.guiWidth(), 5, false
         );
         this.renderPlayerInFrame(graphics, this.width / 2 - 90, this.height / 2 + 44, mouseX, mouseY);
      }
   }

   public boolean mouseReleased(MouseButtonEvent arg) {
      this.children().forEach(child -> {
         if (child instanceof WildfireSlider slider) {
            slider.save();
         }
      });
      return super.mouseReleased(arg);
   }

   private enum Tab {
      CUSTOMIZATION(WildfireBreastCustomizationScreen.BACKGROUND_CUSTOMIZATION, 104),
      PHYSICS(WildfireBreastCustomizationScreen.BACKGROUND_PHYSICS, 104),
      MISC(WildfireBreastCustomizationScreen.BACKGROUND_MISC, 104);

      final Identifier background;
      final int backgroundHeight;

      Tab(Identifier background, int backgroundHeight) {
         this.background = background;
         this.backgroundHeight = backgroundHeight;
      }
   }
}
