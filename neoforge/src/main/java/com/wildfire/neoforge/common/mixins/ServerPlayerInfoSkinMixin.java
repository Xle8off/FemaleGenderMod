package com.wildfire.neoforge.common.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.wildfire.neoforge.appearance.HdSkinClient;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Apply after normal provider hooks; no dependency on launcher-specific mod IDs. */
@Mixin(value = PlayerInfo.class, priority = 100)
abstract class ServerPlayerInfoSkinMixin {
    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin springplus$authoritativeSkin(PlayerSkin original) {
        return HdSkinClient.skin(((PlayerInfo) (Object) this).getProfile().id(), original);
    }
}
