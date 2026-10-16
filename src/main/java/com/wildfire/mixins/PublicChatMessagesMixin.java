package com.wildfire.mixins;
import com.wildfire.gui.PublicChatState;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChatComponent.class)
abstract class PublicChatMessagesMixin {
    @Inject(method="captureClickableText",at=@At("HEAD"),cancellable=true)
    private void springplus$clickable(net.minecraft.client.gui.ActiveTextCollector collector,int x,int y,ChatComponent.DisplayMode mode,CallbackInfo ci){if(com.wildfire.client.music.SpringPlusSession.enabled())ci.cancel();}
    @ModifyVariable(method="addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",at=@At("HEAD"),argsOnly=true)
    private Component springplus$emoji(Component text){return com.wildfire.client.music.SpringPlusSession.enabled()?PublicChatState.formatted(text).copy().withStyle(text.getStyle()):text;}
    @Inject(method="extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",at=@At("HEAD"),cancellable=true)
    private void springplus$focused(GuiGraphicsExtractor graphics,net.minecraft.client.gui.Font font,int tick,int x,int y,ChatComponent.DisplayMode mode,boolean focused,CallbackInfo ci){
        if(!com.wildfire.client.music.SpringPlusSession.enabled())return;
        if(!com.wildfire.gui.PublicChatControls.active())com.wildfire.gui.PublicChatHud.render(graphics);
        ci.cancel();
    }
    @Inject(method="addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",at=@At("HEAD"),cancellable=true)
    private void springplus$message(Component text,net.minecraft.network.chat.MessageSignature signature,net.minecraft.client.multiplayer.chat.GuiMessageSource source,net.minecraft.client.multiplayer.chat.GuiMessageTag tag,CallbackInfo ci){
        String insertion=com.wildfire.gui.ChatChannelMetadata.controlInsertion(text);
        if((insertion!=null&&insertion.startsWith("springplus:chat-state:")||com.wildfire.client.music.SpringPlusSession.enabled())&&PublicChatState.accept(text))ci.cancel();
    }
    @Inject(method="clearMessages",at=@At("HEAD"))
    private void springplus$clear(boolean history,CallbackInfo ci){PublicChatState.clear();}
}
