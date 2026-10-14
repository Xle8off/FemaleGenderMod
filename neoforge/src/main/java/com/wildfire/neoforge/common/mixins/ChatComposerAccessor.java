package com.wildfire.neoforge.common.mixins;

import com.wildfire.gui.ChatComposerAccess;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiLineEditBox.class)
public interface ChatComposerAccessor extends ChatComposerAccess {
    @Accessor("textField") MultilineTextField springplus$textField();
}
