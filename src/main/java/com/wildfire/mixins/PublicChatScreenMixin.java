package com.wildfire.mixins;
import com.wildfire.gui.PublicChatControls;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ChatScreen.class)
abstract class PublicChatScreenMixin extends Screen {
    protected PublicChatScreenMixin(Component title){super(title);}
    @Shadow protected EditBox input;
    @Shadow private net.minecraft.client.gui.components.CommandSuggestions commandSuggestions;
    @Shadow public abstract void moveInHistory(int direction);
    @Inject(method="mouseClicked",at=@At("RETURN"))
    private void springplus$restoreInput(MouseButtonEvent event,boolean doubled,CallbackInfoReturnable<Boolean> ci){
        if(springplus$controls!=null&&minecraft.gui.screen()==(Object)this)springplus$controls.focusComposer();
    }
    @Unique private PublicChatControls springplus$controls;
    @Inject(method="init",at=@At("TAIL"))
    private void springplus$init(CallbackInfo ci){if(com.wildfire.client.music.SpringPlusSession.enabled()){springplus$controls=new PublicChatControls((ChatScreen)(Object)this,input,this::addRenderableWidget,event->defaultHandleGameClickEvent(event,minecraft,this));}}
    @Inject(method="formatChat",at=@At("HEAD"),cancellable=true)
    private void springplus$emoji(String value,int position,CallbackInfoReturnable<net.minecraft.util.FormattedCharSequence> ci){if(springplus$controls!=null&&!value.startsWith("/"))ci.setReturnValue(com.wildfire.gui.ChatReactions.input(value).getVisualOrderText());}
    @Redirect(method="extractRenderState",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void springplus$inputBackground(GuiGraphicsExtractor g,int x1,int y1,int x2,int y2,int color){
        if(springplus$controls!=null&&springplus$controls.vanilla())com.wildfire.gui.FlatButtons.joinedBackdrop(g,springplus$controls.rowLeft(),springplus$controls.rowTop(),springplus$controls.rowRight(),height-2,0x78000000,6,false,true);
        else if(springplus$controls!=null&&com.wildfire.client.music.SpringPlusSession.enabled())com.wildfire.gui.FlatButtons.plate(g,springplus$controls.rowLeft(),springplus$controls.rowTop(),springplus$controls.rowWidth(),height-2-springplus$controls.rowTop(),true,true,false,com.wildfire.gui.UiTheme.ACCENT);
        else g.fill(x1,y1,x2,y2,color);
    }
    @Inject(method="extractRenderState",at=@At("HEAD"))
    private void springplus$render(GuiGraphicsExtractor g,int mx,int my,float delta,CallbackInfo ci){if(springplus$controls!=null&&com.wildfire.client.music.SpringPlusSession.enabled())springplus$controls.render(g,mx,my);}
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void springplus$click(MouseButtonEvent event,boolean doubled,CallbackInfoReturnable<Boolean> ci){
        if(springplus$controls!=null&&springplus$controls.searchClick(event,doubled)){
            ci.setReturnValue(true);return;
        }
        // Native suggestions float above history. Give them the click before
        // the custom history panel consumes its otherwise-empty background.
        if(springplus$controls!=null&&commandSuggestions.mouseClicked(event)){
            if(minecraft.gui.screen()==(Object)this)springplus$controls.focusComposer();
            ci.setReturnValue(true);
            return;
        }
        if(springplus$controls!=null&&springplus$controls.click(event.x(),event.y(),event.button())){
            if(springplus$controls.historyDragging())setDragging(true);
            if(minecraft.gui.screen()==(Object)this)springplus$controls.focusComposer();
            ci.setReturnValue(true);
        }
    }
    @Inject(method="extractRenderState",at=@At("TAIL"))
    private void springplus$frame(GuiGraphicsExtractor g,int mx,int my,float delta,CallbackInfo ci){if(springplus$controls!=null&&com.wildfire.client.music.SpringPlusSession.enabled())springplus$controls.renderFrame(g);}
    @Inject(method="mouseScrolled",at=@At("HEAD"),cancellable=true)
    private void springplus$scroll(double x,double y,double sx,double sy,CallbackInfoReturnable<Boolean> ci){
        // Native suggestions consume the wheel only inside their actual rectangle.
        if(springplus$controls!=null&&commandSuggestions.mouseScrolled(sy)){
            ci.setReturnValue(true);return;
        }
        if(springplus$controls!=null&&springplus$controls.scroll(x,y,sy))ci.setReturnValue(true);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(springplus$controls!=null&&springplus$controls.dragHistory(event.y(),event.button()))return true;
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(springplus$controls!=null&&springplus$controls.releaseHistory(event.button())){setDragging(false);return true;}
        return super.mouseReleased(event);
    }
    @Inject(method="setInitialFocus",at=@At("RETURN"))
    private void springplus$focus(CallbackInfo ci){if(springplus$controls!=null)springplus$controls.focusComposer();}
    @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true)
    private void springplus$key(net.minecraft.client.input.KeyEvent event,CallbackInfoReturnable<Boolean> ci){
        if(springplus$controls==null)return;
        if(springplus$controls.searchKey(event)){ci.setReturnValue(true);return;}
        springplus$controls.focusComposer();
        int key=event.key();
        // Screen's directional navigation runs before ChatScreen's history handling.
        // Consume arrows here, after giving native/mention suggestions first priority.
        if(key>=262&&key<=265){
            if(!commandSuggestions.keyPressed(event)){
                if(key==264||key==265)moveInHistory(key==265?-1:1);
                else if(input.getValue().startsWith("/"))input.keyPressed(event);
                else springplus$controls.composerKey(event);
            }
            springplus$controls.focusComposer();
            ci.setReturnValue(true);
        }else if(!input.getValue().startsWith("/")&&event.isConfirmation())setFocused(input);
        else if(springplus$controls.composerKey(event))ci.setReturnValue(true);
    }
    @Inject(method="keyPressed",at=@At("RETURN"))
    private void springplus$refocus(net.minecraft.client.input.KeyEvent event,CallbackInfoReturnable<Boolean> ci){
        if(springplus$controls!=null)springplus$controls.focusComposer();
    }
}
