package com.wildfire.mixins;

import com.wildfire.gui.PublicChatControls;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Move the native suggestion hitbox and usage together, not just their drawing. */
@Mixin(CommandSuggestions.class)
abstract class PublicChatSuggestionsMixin {
    @Shadow @Final private Screen screen;
    @Shadow private int commandUsagePosition;

    @ModifyArg(method="showSuggestions", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/components/CommandSuggestions$SuggestionsList;<init>(Lnet/minecraft/client/gui/components/CommandSuggestions;IIILjava/util/List;Z)V"), index=1)
    private int springplus$listX(int x){
        return PublicChatControls.active()?x-4:x;
    }

    @Redirect(method="extractUsage", at=@At(value="FIELD", target="Lnet/minecraft/client/gui/components/CommandSuggestions;commandUsagePosition:I", opcode=180))
    private int springplus$usageX(CommandSuggestions owner){
        return commandUsagePosition-(PublicChatControls.active()?4:0);
    }

    @ModifyConstant(method="showSuggestions", constant=@Constant(intValue=12))
    private int springplus$listAnchor(int original){
        return screen.height-PublicChatControls.suggestionBottom(screen.height-original);
    }

    @ModifyConstant(method="extractUsage", constant=@Constant(intValue=27))
    private int springplus$usageAnchor(int original){
        return screen.height-PublicChatControls.suggestionBottom(screen.height-original+12)+12;
    }
}
