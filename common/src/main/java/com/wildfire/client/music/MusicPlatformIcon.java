package com.wildfire.client.music;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Bundled provider marks; does not download icons or inspect other applications. */
public final class MusicPlatformIcon {
    private MusicPlatformIcon() {}
    public static void draw(GuiGraphicsExtractor g, int accent, int x, int y) {
        if (accent == 0xFF1DB954 || accent == 0xFFFFCC33 || accent == 0xFFFF3434) {
            boolean spotify = accent == 0xFF1DB954;
            g.blit(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("appearance_sync",
                "textures/gui/music/" + (spotify ? "spotify" : accent == 0xFFFF3434 ? "youtube" : "yandex") + ".png"),
                x, y, 0f, 0f, spotify ? 15 : 16, 16, 250, spotify ? 258 : 250, 250, spotify ? 258 : 250);
        } else if (accent == 0xFFFF5500) {
            for(int column=0;column<7;column++) {
                int h=2+column/2;
                g.fill(x+column*2,y+11-h,x+column*2+1,y+12,accent);
            }
            g.fill(x+7,y+5,x+13,y+12,accent);g.fill(x+5,y+8,x+16,y+12,accent);
        } else g.item(new ItemStack(Items.MUSIC_DISC_13),x,y);
    }
}
