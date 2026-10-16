package com.wildfire.mixins;

import com.wildfire.gui.ChatReactions;
import com.wildfire.gui.FormattedChatInput;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MultiLineEditBox.class)
abstract class ChatInputEmojiMixin {
    @Redirect(method="extractContents",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private void springplus$emojiInput(GuiGraphicsExtractor graphics,Font font,String text,int x,int y,int color,boolean shadow){
        if(FormattedChatInput.contains(this))graphics.text(font,ChatReactions.input(text).getVisualOrderText(),x,y,color,shadow);
        else graphics.text(font,text,x,y,color,shadow);
    }
}
