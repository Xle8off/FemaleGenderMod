package com.wildfire.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.wildfire.main.networking.appearance.HdSkinClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Apply after normal provider hooks; no dependency on launcher-specific mod IDs. */
@Mixin(value = AbstractClientPlayer.class, priority = 100)
abstract class ServerPlayerSkinMixin {
    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin springplus$authoritativeSkin(PlayerSkin original) {
        return HdSkinClient.skin(((AbstractClientPlayer) (Object) this).getUUID(), original);
    }
}
