package com.wildfire.client.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public interface ClientCommandHelper<SOURCE extends SharedSuggestionProvider> {
   LiteralArgumentBuilder<SOURCE> literalArgumentBuilder(String var1);

   <T> RequiredArgumentBuilder<SOURCE, T> argument(String var1, ArgumentType<T> var2);

   void sendSystemMessage(SOURCE var1, Component var2);

   void sendFailure(SOURCE var1, Component var2);

   Level getLevel(SOURCE var1);

   LocalPlayer getPlayer(SOURCE var1);

   Minecraft getMinecraft(SOURCE var1);
}
