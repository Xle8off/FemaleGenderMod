package dev.aeltorx.inflow.client.mixin;
import net.minecraft.world.entity.Display;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccess {
    @Invoker("setText") void windowsmenu$text(Component text);
    @Invoker("setBackgroundColor") void windowsmenu$background(int color);
    @Invoker("setLineWidth") void windowsmenu$lineWidth(int width);
}
