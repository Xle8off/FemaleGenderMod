package com.wildfire.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Compact font-independent icons for menu headings. */
public final class MenuTitleIcons {
    private MenuTitleIcons(){}
    private static final String[][] ICONS={
        {"      ##      ","     ####     ","   ##    ##   ","  ##      ##  ","  #        #  ","  #        #  ","  #        #  ","  #        #  ","  #        #  "," ##        ## ","##############","              ","     ####     ","      ##      "},
        {"   ###  ###   ","  #   ##   #  ","  #   ##   #  ","  #   ##   #  ","   ###  ###   ","              ","  ####  ####  "," #    ##    # ","#     ##     #","#     ##     #","#     ##     #","######  ######","              ","              "},
        {"              ","  ##########  "," #          # "," #          # "," #  ##  ##  # "," #          # "," #  ######  # "," #          # "," #          # "," #   ######## "," #  #         "," # #          "," ##           ","              "},
        {"     ####     "," ##  #  #  ## "," # ##    ## # ","  #        #  ","  #  ####  #  ","##  #    #  ##","#   #    #   #","#   #    #   #","##  #    #  ##","  #  ####  #  ","  #        #  "," # ##    ## # "," ##  #  #  ## ","     ####     "}
    };
    public static void draw(GuiGraphicsExtractor g,int kind,int x,int y,int color){
        var rows=ICONS[Math.clamp(kind,0,3)];
        for(int row=0;row<rows.length;row++)for(int column=0;column<rows[row].length();column++)
            if(rows[row].charAt(column)=='#')g.fill(x+column,y+row,x+column+1,y+row+1,color);
    }
}
