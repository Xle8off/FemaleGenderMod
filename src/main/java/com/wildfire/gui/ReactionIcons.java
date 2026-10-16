package com.wildfire.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Font-independent, centered 16-pixel icons for reaction history. */
public final class ReactionIcons {
    private static final String[] CLOCK = {
        "     ######     ","   ###    ###   ","  ##        ##  "," ##          ## ",
        " #     #      # ","##     #      ##","#      #       #","#      #       #",
        "#      ####    #","#              #","##            ##"," #            # ",
        " ##          ## ","  ##        ##  ","   ###    ###   ","     ######     "
    };
    private static final String[] STAR = {
        "       ##       ","       ##       ","      ####      ","      ####      ",
        "     ######     ","################"," ############## ","  ############  ",
        "   ##########   ","    ########    ","   ##########   ","   ####  ####   ",
        "  ####    ####  ","  ###      ###  ","  ##        ##  ","                "
    };
    private ReactionIcons() {}
    public static void draw(GuiGraphicsExtractor graphics,boolean favorite,int x,int y,int width,int height,int color) {
        int left=x+(width-16)/2,top=y+(height-16)/2;
        Identifier icon=Identifier.fromNamespaceAndPath("appearance_sync","textures/gui/reactions/"+(favorite?"favorites":"recent")+".png");
        graphics.blit(RenderPipelines.GUI_TEXTURED,icon,left,top,0f,0f,16,16,16,16,16,16);
    }
}
