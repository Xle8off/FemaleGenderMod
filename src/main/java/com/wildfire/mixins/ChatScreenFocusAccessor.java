package com.wildfire.mixins;
import com.wildfire.gui.ChatScreenFocusAccess;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(AbstractContainerEventHandler.class)
public interface ChatScreenFocusAccessor extends ChatScreenFocusAccess {
    @Accessor("focused") void springplus$setFocusedListener(GuiEventListener listener);
}
