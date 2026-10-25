package com.wildfire.client.gui.screen;

import com.wildfire.client.contributors.Contributor;
import com.wildfire.client.contributors.Contributors;
import com.wildfire.client.gui.FakeGUIPlayer;
import com.wildfire.client.gui.IFancyFontRenderer;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireLang;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.UnknownNullability;

public class WildfireCreditsScreen extends BaseWildfireScreen {
   private static final Identifier CREDIT_CONTAINER = WildfireGender.id("credits/credit_container");
   private static final Identifier CREDIT_OUTLINE = WildfireGender.id("credits/credit_outline");
   private static final Identifier BUTTON_CONTAINER = WildfireGender.id("credits/button_container");
   private static final Identifier TAB_CONTAINER = WildfireGender.id("credits/tab_container");
   private final FakeGUIPlayer[] C_GENERAL = Contributors.getContributors()
      .entrySet()
      .stream()
      .filter(it -> it.getValue().name() != null)
      .filter(it -> Boolean.TRUE.equals(it.getValue().showInCredits()))
      .filter(it -> it.getValue().getRole() != Contributor.Role.TRANSLATOR)
      .sorted(Comparator.comparing(it -> it.getValue().name()))
      .sorted(Comparator.comparing(it -> it.getValue().getRole()))
      .map(it -> new FakeGUIPlayer(it.getValue().name(), it.getKey(), FakeGUIPlayer.FEMALE_CHANGES))
      .toArray(FakeGUIPlayer[]::new);
   private final FakeGUIPlayer[] C_TRANSLATORS = Contributors.getContributors()
      .entrySet()
      .stream()
      .filter(it -> it.getValue().name() != null)
      .filter(it -> Boolean.TRUE.equals(it.getValue().showInCredits()))
      .filter(it -> it.getValue().getRole() == Contributor.Role.TRANSLATOR)
      .sorted(Comparator.comparing(it -> it.getValue().name()))
      .sorted(Comparator.comparing(it -> it.getValue().getRole()))
      .map(it -> new FakeGUIPlayer(it.getValue().name(), it.getKey(), FakeGUIPlayer.FEMALE_CHANGES))
      .toArray(FakeGUIPlayer[]::new);
   private static final int boxesPerPage = 12;
   private WildfireCreditsScreen.Category categoryTab = WildfireCreditsScreen.Category.GENERAL;
   private int creditsPage = 0;
   private int navigationY;

   public WildfireCreditsScreen(Screen parent, UUID uuid) {
      super(WildfireLang.CREDITS_TITLE.translate(), parent, uuid);
   }

   @Override
   public void init() {
      super.init();
      var ref = new Object() {
         @UnknownNullability AbstractWidget prevPage;
         @UnknownNullability AbstractWidget nextPage;
         @UnknownNullability AbstractWidget generalTab;
         @UnknownNullability AbstractWidget translatorTab;
      };
      this.navigationY = this.height / 2 + 82;
      ref.generalTab = this.addButton(
         builder -> builder.message(WildfireLang.CREDITS_GENERAL::translate)
            .position(this.width / 2 - 89, this.navigationY + 34)
            .size(87, 13)
            .active(this.categoryTab == WildfireCreditsScreen.Category.TRANSLATORS)
            .onPress(var2x -> {
               this.categoryTab = WildfireCreditsScreen.Category.GENERAL;
               this.creditsPage = 0;
               ref.prevPage.active = false;
               ref.nextPage.active = this.creditsPage < this.getTotalPages() - 1;
               ref.generalTab.active = false;
               ref.translatorTab.active = true;
            })
      );
      ref.translatorTab = this.addButton(
         builder -> builder.message(WildfireLang.CREDITS_TRANSLATORS::translate)
            .position(this.width / 2 + 2, this.navigationY + 34)
            .size(87, 13)
            .active(this.categoryTab == WildfireCreditsScreen.Category.GENERAL)
            .onPress(var2x -> {
               this.categoryTab = WildfireCreditsScreen.Category.TRANSLATORS;
               this.creditsPage = 0;
               ref.prevPage.active = false;
               ref.nextPage.active = this.creditsPage < this.getTotalPages() - 1;
               ref.generalTab.active = true;
               ref.translatorTab.active = false;
            })
      );
      this.addButton(
         builder -> builder.message(WildfireLang.DETAILS_BACK::translate)
            .position(this.width / 2 - 25, this.navigationY + 6)
            .size(50, 13)
            .onPress(var1x -> this.onClose())
      );
      ref.nextPage = this.addButton(
         builder -> builder.message(WildfireLang.DETAILS_NEXT_PAGE::translate)
            .position(this.width / 2 + 29, this.navigationY + 6)
            .size(60, 13)
            .active(this.creditsPage < this.getTotalPages() - 1)
            .onPress(var2x -> {
               if (this.creditsPage < this.getTotalPages() - 1) {
                  this.creditsPage++;
               }

               ref.prevPage.active = this.creditsPage != 0;
               ref.nextPage.active = this.creditsPage < this.getTotalPages() - 1;
            })
      );
      ref.prevPage = this.addButton(
         builder -> builder.message(WildfireLang.DETAILS_PREV_PAGE::translate)
            .position(this.width / 2 - 89, this.navigationY + 6)
            .size(60, 13)
            .active(this.creditsPage != 0)
            .onPress(var2x -> {
               if (this.creditsPage > 0) {
                  this.creditsPage--;
               }

               ref.prevPage.active = this.creditsPage != 0;
               ref.nextPage.active = this.creditsPage < this.getTotalPages();
            })
      );
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      this.extractTransparentBackground(graphics);
   }

   public void tick() {
      super.tick();

      for (FakeGUIPlayer player : this.getActiveBoxes()) {
         player.tick();
      }
   }

   private int getTotalPages() {
      return Mth.ceil(this.getActiveBoxes().length / 12.0);
   }

   private FakeGUIPlayer[] getActiveBoxes() {
      return this.categoryTab == WildfireCreditsScreen.Category.TRANSLATORS ? this.C_TRANSLATORS : this.C_GENERAL;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      this.drawScrollingString(graphics, this.getTitle(), 0, this.height / 2 - 125, IFancyFontRenderer.TextAlignment.CENTER, -1, graphics.guiWidth(), 5, false);
      this.drawScrollingString(
         graphics,
         Component.translatable("springpluscosmetics.credits.original"),
         0,
         this.height / 2 - 111,
         IFancyFontRenderer.TextAlignment.CENTER,
         -5592406,
         graphics.guiWidth(),
         5,
         false
      );
      this.drawScrollingString(
         graphics,
         Component.translatable("springpluscosmetics.credits.springplus"),
         0,
         this.height / 2 - 98,
         IFancyFontRenderer.TextAlignment.CENTER,
         -22300,
         graphics.guiWidth(),
         5,
         false
      );
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BUTTON_CONTAINER, this.width / 2 - 95, this.navigationY, 190, 25);
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TAB_CONTAINER, this.width / 2 - 95, this.navigationY + 28, 190, 25);
      int columns = 6;
      int boxW = 60;
      int boxH = 74;
      int startIndex = this.creditsPage * 12;
      int endIndex = Math.min(startIndex + 12, this.getActiveBoxes().length);
      int startY = this.height / 2 - 2 * boxH / 2 + 4;

      for (int i = startIndex; i < endIndex; i++) {
         FakeGUIPlayer creditBox = this.getActiveBoxes()[i];
         int localIndex = i - startIndex;
         int col = localIndex % columns;
         int row = localIndex / columns;
         int remaining = Math.min(endIndex - startIndex - row * columns, columns);
         int rowWidth = remaining * boxW;
         int startX = this.width / 2 - rowWidth / 2 + 4;
         int creditBoxX = startX + col * boxW;
         int creditBoxY = startY + row * boxH;
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CREDIT_CONTAINER, creditBoxX, creditBoxY, 52, 68);
         Contributor.Role role = creditBox.getRoleOrGeneric();
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CREDIT_OUTLINE, creditBoxX + 3, creditBoxY + 3, 46, 53, ARGB.opaque(role.getColor().getValue()));
         int xP = creditBoxX + 26;
         int yP = creditBoxY + 34;
         ClientMannequin entity = creditBox.getEntity();
         InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics, xP - 38, yP - 29, xP + 38, yP + 20, 40, getEntityScale(entity, 0.5F), mouseX, mouseY, entity
         );
         this.drawScaledScrollingString(
            graphics, Component.literal(creditBox.getName()), creditBoxX + 3, yP + 23, IFancyFontRenderer.TextAlignment.CENTER, -1, 46, 1, false, 0.55F
         );
         if (mouseX > xP - 24 && mouseX < xP + 23 && mouseY > yP + 22 && mouseY < yP + 31) {
            List<Component> txtList = new ArrayList<>();
            txtList.add(
               WildfireLang.GENERIC_DASH_EXPLANATION
                  .translateColored(TextColor.DARK_GRAY, role.withColor(Component.literal(creditBox.getName())), role.withColor(role.shortName()))
            );
            if (creditBox.getDescription() != null) {
               txtList.add(creditBox.getDescription().copy().withColor(TextColor.GRAY));
            }

            graphics.setComponentTooltipForNextFrame(this.font, txtList, mouseX, mouseY);
         }
      }

      super.extractRenderState(graphics, mouseX, mouseY, delta);
   }

   private enum Category {
      GENERAL,
      TRANSLATORS;
   }
}
