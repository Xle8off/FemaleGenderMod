package dev.aeltorx.inflow.client.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccess {
    @Invoker("setTransformation") void windowsmenu$transform(Transformation value);
    @Invoker("setViewRange") void windowsmenu$viewRange(float value);
    @Invoker("setWidth") void windowsmenu$width(float value);
    @Invoker("setHeight") void windowsmenu$height(float value);
}
