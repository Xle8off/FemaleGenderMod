package com.wildfire.neoforge.common.mixins;

import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CapeLayer.class)
abstract class TransparentCapeMixin {
    @Redirect(method = "submit", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entitySolid(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType springplus$transparentCape(Identifier texture) {
        if (texture.getNamespace().equals("appearance_sync") && texture.getPath().startsWith("cape/"))
            return RenderTypes.entityTranslucent(texture);
        return RenderTypes.entitySolid(texture);
    }
}
