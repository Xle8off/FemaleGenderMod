package dev.aeltorx.inflow.client.mixin;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Mannequin.class)
public interface MannequinAccess {
    @Invoker("setHideDescription") void windowsmenu$hideDescription(boolean hide);
}
