package com.wildfire.client.render.debug;

import com.wildfire.client.physics.BothBreastsPhysics;
import com.wildfire.client.physics.BreastPhysics;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

public class PhysicsDebugHudEntry implements DebugScreenEntry {
   public static final Identifier ID = WildfireGender.id("physics");

   public void display(DebugScreenDisplayer lines, @Nullable Level world, @Nullable LevelChunk clientChunk, @Nullable LevelChunk chunk) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         PlayerConfigHolder config = WildfireGender.getPlayerById(player.getUUID());
         if (config != null) {
            BothBreastsPhysics breastPhysics = config.breastPhysics();
            List<String> info = new ArrayList<>();
            if (config.breasts().physics().uniboob().get()) {
               info.add(ChatFormatting.UNDERLINE + "Breast Physics");
               this.add(info, breastPhysics.left());
            } else {
               info.add(ChatFormatting.UNDERLINE + "Left Breast Physics");
               this.add(info, breastPhysics.left());
               info.add("");
               info.add(ChatFormatting.UNDERLINE + "Right Breast Physics");
               this.add(info, breastPhysics.right());
            }

            lines.addToGroup(ID, info);
         }
      }
   }

   private void add(List<String> lines, BreastPhysics physics) {
      lines.add("Breast size: " + physics.getBreastSize());
      lines.add("Position: (" + physics.getPositionX() + ", " + physics.getPositionY() + ")");
      lines.add("Rotation: " + physics.getBounceRotation());
   }
}
