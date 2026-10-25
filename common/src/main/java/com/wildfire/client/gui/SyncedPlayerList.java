package com.wildfire.client.gui;

import com.wildfire.api.Gender;
import com.wildfire.client.contributors.Contributors;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireLang;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType;

public final class SyncedPlayerList {
   private static int ticks = 0;
   private static volatile List<SyncedPlayerList.SyncedPlayer> syncedPlayers = Collections.emptyList();
   private static long TIME_STARTED = -1L;
   private static final IFancyFontRenderer FALLBACK_FONT_RENDERER = () -> TIME_STARTED;

   private SyncedPlayerList() {
      throw new UnsupportedOperationException();
   }

   public static void resetTimer() {
      TIME_STARTED = -1L;
   }

   public static void drawSyncedPlayers(GuiGraphicsExtractor graphics) {
      if (TIME_STARTED == -1L) {
         TIME_STARTED = Util.getMillis();
      }

      int screenWidth = graphics.guiWidth();
      int width = screenWidth;
      if (Minecraft.getInstance().gui.hud.getTabList().visible) {
         int maxLineWidth = getTabOverlayMaxLineWidth(screenWidth);
         if (maxLineWidth > 0) {
            width = screenWidth / 2 - maxLineWidth / 2 - 1;
         }
      }

      drawSyncedPlayers(FALLBACK_FONT_RENDERER, graphics, width, width);
   }

   public static void drawSyncedPlayers(IFancyFontRenderer fontRenderer, GuiGraphicsExtractor graphics, int titleWidth, int playerWidth) {
      if (!syncedPlayers.isEmpty()) {
         MutableComponent header = WildfireLang.WARDROBE_PLAYERS_USING.translateColored(TextColor.AQUA);
         fontRenderer.drawScrollingString(graphics, header, 0, 5, IFancyFontRenderer.TextAlignment.LEFT, -1, titleWidth, 5, false);
         int yPos = 18;

         for (SyncedPlayerList.SyncedPlayer entry : syncedPlayers) {
            Component text = WildfireLang.GENERIC_DASH_EXPLANATION.translate(entry.coloredName(), entry.gender().getDisplayName());
            fontRenderer.drawScrollingString(graphics, text, 5, yPos, IFancyFontRenderer.TextAlignment.LEFT, -1, playerWidth - 5, 5, false);
            yPos += 10;
         }
      }
   }

   public static void onTick(Minecraft minecraft) {
      if (ticks++ % 5 == 0) {
         LocalPlayer clientPlayer = minecraft.player;
         if (clientPlayer == null) {
            syncedPlayers = Collections.emptyList();
         } else {
            ArrayList<SyncedPlayerList.SyncedPlayer> list = new ArrayList<>();

            for (PlayerInfo entry : clientPlayer.connection.getListedOnlinePlayers()) {
               if (!Objects.equals(entry.getProfile().id(), clientPlayer.getUUID())) {
                  PlayerConfigHolder config = WildfireGender.getPlayerById(entry.getProfile().id());
                  if (config != null && config.syncStatus != PlayerConfigHolder.SyncStatus.UNKNOWN) {
                     TextColor color = Contributors.getColor(entry.getProfile().id());
                     list.add(new SyncedPlayerList.SyncedPlayer(entry.getProfile().name(), color == null ? TextColor.WHITE : color, config.gender().get()));
                     if (list.size() >= 40) {
                        break;
                     }
                  }
               }
            }

            syncedPlayers = list;
         }
      }
   }

   private static int getTabOverlayMaxLineWidth(int screenWidth) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return 0;
      }

      PlayerTabOverlay tabList = mc.gui.hud.getTabList();
      Scoreboard scoreboard = mc.level.getScoreboard();
      Objective displayObjective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
      List<PlayerInfo> playerInfos = tabList.getPlayerInfos();
      int spacerWidth = mc.font.width(" ");
      int maxNameWidth = 0;
      int maxScoreWidth = 0;

      for (PlayerInfo info : playerInfos) {
         maxNameWidth = Math.max(maxNameWidth, mc.font.width(tabList.getNameForDisplay(info)));
         int playerScoreWidth = 0;
         if (displayObjective != null && displayObjective.getRenderType() != RenderType.HEARTS) {
            ScoreHolder scoreHolder = ScoreHolder.fromGameProfile(info.getProfile());
            ReadOnlyScoreInfo scoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, displayObjective);
            if (scoreInfo != null) {
               NumberFormat objectiveDefaultFormat = displayObjective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT);
               playerScoreWidth = mc.font.width(scoreInfo.formatValue(objectiveDefaultFormat));
            }

            maxScoreWidth = Math.max(maxScoreWidth, playerScoreWidth > 0 ? spacerWidth + playerScoreWidth : 0);
         }
      }

      int slots = playerInfos.size();
      int rows = slots;

      int cols;
      for (cols = 1; rows > 20; rows = (slots + cols - 1) / cols) {
         cols++;
      }

      boolean showHead = mc.getConnection().onlineMode();
      int widthForScore;
      if (displayObjective != null) {
         if (displayObjective.getRenderType() == RenderType.HEARTS) {
            widthForScore = 90;
         } else {
            widthForScore = maxScoreWidth;
         }
      } else {
         widthForScore = 0;
      }

      int slotWidth = Math.min(cols * ((showHead ? 9 : 0) + maxNameWidth + widthForScore + 13), screenWidth - 50) / cols;
      int maxLineWidth = slotWidth * cols + (cols - 1) * 5;
      if (tabList.header != null) {
         for (FormattedCharSequence line : mc.font.split(tabList.header, screenWidth - 50)) {
            maxLineWidth = Math.max(maxLineWidth, mc.font.width(line));
         }
      }

      if (tabList.footer != null) {
         for (FormattedCharSequence line : mc.font.split(tabList.footer, screenWidth - 50)) {
            maxLineWidth = Math.max(maxLineWidth, mc.font.width(line));
         }
      }

      return maxLineWidth;
   }

   private record SyncedPlayer(String name, TextColor color, Gender gender) {
      public Component coloredName() {
         return Component.literal(this.name).withColor(this.color);
      }
   }
}
