package com.wildfire.client.gui.screen;

import com.wildfire.api.uvs.BreastTypes;
import com.wildfire.api.uvs.UVDirection;
import com.wildfire.api.uvs.UVLayout;
import com.wildfire.api.uvs.UVQuad;
import com.wildfire.client.gui.IFancyFontRenderer;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireLang;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.UnknownNullability;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;

public class WildfireBreastUVEditorScreen extends BaseWildfireScreen {
   private static final Identifier TEXTURE_ADD = WildfireGender.id("widgets/add");
   private static final Identifier TEXTURE_SUBTRACT = WildfireGender.id("widgets/subtract");
   private @Nullable UVLayout selectedUVs = null;
   private BreastTypes selectedBreastIndex = BreastTypes.LEFT;
   private @Nullable UVDirection selectedDirection = null;
   private @UnknownNullability Vector2i winElementPos;
   private @UnknownNullability Vector2i uvWindowPos;
   private static final int sidebarWidth = 180;
   private static final int textureDrawWidth = 196;
   private static final int textureSourceWidth = 64;
   private static final float uvWindowScaleFactor = 3.0625F;

   public WildfireBreastUVEditorScreen(Screen parent, UUID uuid) {
      super(WildfireLang.UV_EDITOR.translate(), parent, uuid);
   }

   @Override
   public void init() {
      super.init();
      this.uvWindowPos = new Vector2i(5, this.height / 2 - 98);
      this.winElementPos = new Vector2i(this.width - 180 + 7, 32);
      int x = this.width - 180;
      int y = 0;
      this.addButton(
         builder -> builder.message(WildfireLang.UV_EDITOR_RESET_ALL::translate).position(x + 5, y + 5).size(this.width - x - 10, 20).onPress(var1x -> {
            PlayerConfigHolder player = Objects.requireNonNull(this.getPlayer(), "getPlayer()");
            if (player.uvs().reset()) {
               player.save();
            }
         })
      );
      this.addButton(
         builder -> builder.message(WildfireLang.UV_EDITOR_LB::translate)
            .position(this.winElementPos.x(), this.winElementPos.y() + 13)
            .size(40, 15)
            .active(this.selectedBreastIndex != BreastTypes.LEFT)
            .onPress(var1x -> this.selectBreastUVMap(BreastTypes.LEFT))
      );
      this.addButton(
         builder -> builder.message(WildfireLang.UV_EDITOR_RB::translate)
            .position(this.winElementPos.x() + 45 - 3, this.winElementPos.y() + 13)
            .size(39, 15)
            .active(this.selectedBreastIndex != BreastTypes.RIGHT)
            .onPress(var1x -> this.selectBreastUVMap(BreastTypes.RIGHT))
      );
      this.addButton(
         builder -> builder.message(WildfireLang.UV_EDITOR_LB_OVERLAY::translate)
            .position(this.winElementPos.x(), this.winElementPos.y() + 44)
            .size(40, 15)
            .active(this.selectedBreastIndex != BreastTypes.LEFT_OVERLAY)
            .onPress(var1x -> this.selectBreastUVMap(BreastTypes.LEFT_OVERLAY))
      );
      this.addButton(
         builder -> builder.message(WildfireLang.UV_EDITOR_RB_OVERLAY::translate)
            .position(this.winElementPos.x() + 45 - 3, this.winElementPos.y() + 44)
            .size(39, 15)
            .active(this.selectedBreastIndex != BreastTypes.RIGHT_OVERLAY)
            .onPress(var1x -> this.selectBreastUVMap(BreastTypes.RIGHT_OVERLAY))
      );
      if (this.selectedDirection != null) {
         int uvPositionWindowX = this.width - 130 + 5;
         int buttonArrayY = 52;

         for (int i = 0; i < 8; i++) {
            boolean isAdd = i % 2 == 1;
            int uvIndex = i / 2;
            int delta = isAdd ? 1 : -1;
            int xOffset = isAdd ? 106 : 92;
            int yOffset = i / 2 * 14;
            this.addButton(
               builder -> builder.renderer(
                     (button, ctx, var4x, var5x, var6x) -> {
                        int increment = this.getPositionIncrement();

                        int color = (switch (increment) {
                           case 10 -> TextColor.AQUA;
                           case 20 -> TextColor.BLUE;
                           default -> TextColor.WHITE;
                        }).getValue();
                        ctx.blitSprite(
                           RenderPipelines.GUI_TEXTURED,
                           isAdd ? TEXTURE_ADD : TEXTURE_SUBTRACT,
                           button.getX() + button.getWidth() / 2 - 3,
                           button.getY() + button.getHeight() / 2 - 3,
                           6,
                           6,
                           ARGB.opaque(color)
                        );
                     }
                  )
                  .message((isAdd ? WildfireLang.UV_EDITOR_ADD : WildfireLang.UV_EDITOR_REMOVE)::translate)
                  .position(uvPositionWindowX + xOffset, y + buttonArrayY + yOffset)
                  .size(12, 12)
                  .onPress(var3x -> {
                     if (this.selectedDirection != null && this.selectedUVs != null) {
                        PlayerConfigHolder player = Objects.requireNonNull(this.getPlayer(), "getPlayer()");
                        UVQuad quad = this.selectedUVs.getAllSides().get(this.selectedDirection);
                        assert quad != null;
                        int increment = this.getPositionIncrement();
                        int toAdd = delta * increment;

                        quad = switch (uvIndex) {
                           case 0 -> quad.addX1(toAdd).addX2(toAdd);
                           case 1 -> quad.addY1(toAdd).addY2(toAdd);
                           case 2 -> quad.addX2(toAdd);
                           default -> quad.addY2(toAdd);
                        };
                        this.selectedUVs.put(this.selectedDirection, quad);
                        player.save();
                     }
                  })
            );
         }
      }
   }

   private void selectBreastUVMap(BreastTypes breast) {
      this.selectedBreastIndex = breast;
      this.selectedDirection = null;
      this.rebuildWidgets();
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      this.extractTransparentBackground(graphics);
      int w = this.width - (this.width - 180) - 10;
      graphics.fill(this.width - 180, 0, this.width, this.height, ARGB.black(204));
      graphics.fill(this.width - 180 + 5, 30, this.width - w / 2 - 5, 93, ARGB.black(102));
      graphics.fill(this.width - w / 2, 30, this.width - 5, 128, ARGB.black(102));
      graphics.fill(this.uvWindowPos.x() - 2, this.uvWindowPos.y() - 2, this.uvWindowPos.x() + 196 + 2, this.uvWindowPos.y() + 196 + 2, ARGB.black(204));
      graphics.fill(this.uvWindowPos.x(), this.uvWindowPos.y(), this.uvWindowPos.x() + 196, this.uvWindowPos.y() + 196, -1);
   }

   public void tick() {
      super.tick();
      PlayerConfigHolder player = this.getPlayer();
      if (player != null) {
         this.selectedUVs = switch (this.selectedBreastIndex) {
            case RIGHT -> (UVLayout)player.uvs().skin().right().get();
            case LEFT_OVERLAY -> (UVLayout)player.uvs().overlay().left().get();
            case RIGHT_OVERLAY -> (UVLayout)player.uvs().overlay().right().get();
            default -> (UVLayout)player.uvs().skin().left().get();
         };
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      if (this.minecraft.level != null && this.minecraft.player != null) {
         PlayerConfigHolder player = this.getPlayer();
         if (player != null && this.selectedUVs != null) {
            graphics.blit(
               RenderPipelines.GUI_TEXTURED,
               this.minecraft.player.getSkin().body().texturePath(),
               this.uvWindowPos.x(),
               this.uvWindowPos.y(),
               0.0F,
               0.0F,
               196,
               196,
               196,
               196
            );

            for (UVLayout eachBreast : player.uvs()) {
               this.drawFaceBorders(graphics, eachBreast, mouseX, mouseY, true);
            }

            this.drawFaceBorders(graphics, this.selectedUVs, mouseX, mouseY, false);
         }

         this.drawScrollingString(
            graphics,
            WildfireLang.UV_EDITOR_BODY_LAYER.translate(),
            this.winElementPos.x(),
            this.winElementPos.y() + 2,
            IFancyFontRenderer.TextAlignment.CENTER,
            -1,
            81,
            0,
            false
         );
         this.drawScrollingString(
            graphics,
            WildfireLang.UV_EDITOR_JACKET_LAYER.translate(),
            this.winElementPos.x(),
            this.winElementPos.y() + 32,
            IFancyFontRenderer.TextAlignment.CENTER,
            -1,
            81,
            0,
            false
         );
         int positionBoxX = this.width - 45;
         if (this.selectedDirection == null) {
            this.drawCenteredTextWrapped(graphics, WildfireLang.UV_EDITOR_NO_FACE.translate(), positionBoxX, 60, 70, -7829368);
         } else {
            this.drawScrollingString(
               graphics,
               this.selectedDirection.getDirectionText(this.selectedBreastIndex).withColor(TextColor.GOLD),
               positionBoxX - 40,
               37,
               IFancyFontRenderer.TextAlignment.CENTER,
               -1,
               80,
               2,
               false
            );
            this.drawScrollingString(
               graphics, WildfireLang.UV_EDITOR_X_POS.translate(), positionBoxX - 35, 55, IFancyFontRenderer.TextAlignment.LEFT, -1, 45, 0, false
            );
            this.drawScrollingString(
               graphics, WildfireLang.UV_EDITOR_Y_POS.translate(), positionBoxX - 35, 69, IFancyFontRenderer.TextAlignment.LEFT, -1, 45, 0, false
            );
            this.drawScrollingString(
               graphics, WildfireLang.UV_EDITOR_WIDTH.translate(), positionBoxX - 35, 83, IFancyFontRenderer.TextAlignment.LEFT, -1, 45, 0, false
            );
            this.drawScrollingString(
               graphics, WildfireLang.UV_EDITOR_HEIGHT.translate(), positionBoxX - 35, 97, IFancyFontRenderer.TextAlignment.LEFT, -1, 45, 0, false
            );
            this.drawScaledScrollingString(
               graphics,
               WildfireLang.UV_EDITOR_INCREMENT.line(1).withColor(TextColor.AQUA),
               positionBoxX - 40,
               109,
               IFancyFontRenderer.TextAlignment.LEFT,
               -1,
               80,
               2,
               false,
               0.75F
            );
            this.drawScaledScrollingString(
               graphics,
               WildfireLang.UV_EDITOR_INCREMENT.line(2).withColor(TextColor.BLUE),
               positionBoxX - 40,
               119,
               IFancyFontRenderer.TextAlignment.LEFT,
               -1,
               80,
               2,
               false,
               0.75F
            );
         }

         int modelScale = 120;
         if (Minecraft.getInstance().getWindow().getScreenWidth() < 1920) {
            modelScale = 60;
         } else if (Minecraft.getInstance().getWindow().getScreenWidth() >= 2560) {
            modelScale = 200;
         }

         LocalPlayer entity = this.minecraft.player;
         InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics,
            this.width / 2 - modelScale,
            this.height / 2 - modelScale,
            this.width / 2 + modelScale,
            this.height / 2 + modelScale,
            modelScale,
            getEntityScale(entity, 0.0F, false),
            mouseX,
            mouseY,
            entity
         );
         this.drawScrollingString(graphics, this.getTitle(), this.uvWindowPos.x(), 20, IFancyFontRenderer.TextAlignment.CENTER, -1, 196, 2, false);
         super.extractRenderState(graphics, mouseX, mouseY, delta);
      }
   }

   private void drawFaceBorders(GuiGraphicsExtractor graphics, UVLayout uvList, int mouseX, int mouseY, boolean faded) {
      for (Entry<UVDirection, UVQuad> entry : uvList.getAllSides().entrySet()) {
         UVDirection direction = entry.getKey();
         UVQuad quad = entry.getValue();
         int borderColor = this.selectedDirection == direction && !faded ? -1 : direction.getFaceColor(faded);
         Component faceName = direction.getShortName();
         if (quad.x1() != 0 || quad.y1() != 0 || quad.x2() != 0 || quad.y2() != 0) {
            int rectX1 = (int)(this.uvWindowPos.x() + quad.x1() * 3.0625F);
            int rectY1 = (int)(this.uvWindowPos.y() + (quad.y1() - 1) * 3.0625F);
            int rectX2 = (int)(this.uvWindowPos.x() + quad.x2() * 3.0625F);
            int rectY2 = (int)(this.uvWindowPos.y() + (quad.y2() - 1) * 3.0625F);
            if (mouseX >= rectX1 && mouseX <= rectX2 && mouseY >= rectY1 && mouseY <= rectY2) {
               List<FormattedCharSequence> array = new ArrayList<>();
               array.add(
                  WildfireLang.UV_SELECTED_DIRECTION
                     .translateColored(TextColor.GOLD, direction.getDirectionText(this.selectedBreastIndex), faceName)
                     .getVisualOrderText()
               );
               array.add(WildfireLang.UV_QUAD.translateColored(TextColor.AQUA, quad.x1(), quad.y1(), quad.x2(), quad.y2()).getVisualOrderText());
               graphics.setTooltipForNextFrame(array, mouseX, mouseY);
            }

            int borderThickness = 1;
            graphics.fill(rectX1, rectY1, rectX2, rectY1 + borderThickness, borderColor);
            graphics.fill(rectX1, rectY2 - borderThickness, rectX2, rectY2, borderColor);
            graphics.fill(rectX1, rectY1, rectX1 + borderThickness, rectY2, borderColor);
            graphics.fill(rectX2 - borderThickness, rectY1, rectX2, rectY2, borderColor);
            this.drawScaledScrollingString(
               graphics,
               faceName,
               rectX1 + borderThickness,
               rectY1 + borderThickness,
               rectX2 - borderThickness,
               rectY2 - borderThickness,
               IFancyFontRenderer.TextAlignment.CENTER,
               -1,
               true,
               0.6F
            );
         }
      }
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (this.selectedUVs == null) {
         return super.mouseClicked(click, doubled);
      }

      for (Entry<UVDirection, UVQuad> entry : this.selectedUVs.getAllSides().entrySet()) {
         UVDirection direction = entry.getKey();
         UVQuad quad = entry.getValue();
         if (quad.x1() != 0 || quad.y1() != 0 || quad.x2() != 0 || quad.y2() != 0) {
            int rectX1 = (int)(this.uvWindowPos.x() + quad.x1() * 3.0625F);
            int rectY1 = (int)(this.uvWindowPos.y() + (quad.y1() - 1) * 3.0625F);
            int rectX2 = (int)(this.uvWindowPos.x() + quad.x2() * 3.0625F);
            int rectY2 = (int)(this.uvWindowPos.y() + (quad.y2() - 1) * 3.0625F);
            if (click.x() >= rectX1 && click.x() <= rectX2 && click.y() >= rectY1 && click.y() <= rectY2) {
               if (click.button() == 0) {
                  if (this.selectedDirection != direction) {
                     Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                     this.selectedDirection = direction;
                     this.rebuildWidgets();
                  }
               } else if (click.button() == 1 && this.selectedDirection != null) {
                  this.selectedDirection = null;
                  Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                  this.rebuildWidgets();
               }

               return true;
            }
         }
      }

      return super.mouseClicked(click, doubled);
   }

   private int getPositionIncrement() {
      Objects.requireNonNull(this.minecraft);
      if (this.minecraft.hasShiftDown() && this.minecraft.hasControlDown()) {
         return 20;
      } else {
         return this.minecraft.hasShiftDown() ? 10 : 1;
      }
   }
}
