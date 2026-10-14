package com.wildfire.neoforge.common.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.client.music.MenuArmBend;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs before optional layer mesh replacements; leaves every non-gesture part untouched. */
@Mixin(value=ModelPart.class,priority=2000)
abstract class MenuSleeveRenderMixin {
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",at=@At("HEAD"),cancellable=true)
    private void springplus$bentSleeve(PoseStack pose,VertexConsumer vertices,int light,int overlay,int color,CallbackInfo ci){
        if(MenuArmBend.renderSleeve((ModelPart)(Object)this,pose,vertices,light,overlay,color))ci.cancel();
    }
}
