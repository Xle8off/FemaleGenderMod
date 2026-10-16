package com.wildfire.gui;

import net.minecraft.client.Minecraft;

/** Shared return transition, measured in real time rather than frames. */
public final class HudReveal {
    private static boolean menu;
    private static long started;
    private HudReveal(){}
    public static boolean menuVisible(){
        var screen=Minecraft.getInstance().gui.screen();
        return screen!=null&&screen.getClass().getSimpleName().equals("NotificationCenterScreen");
    }
    public static void observe(){
        boolean next=menuVisible();
        if(menu&&!next)started=System.nanoTime();
        menu=next;
    }
    public static double remaining(){
        observe();
        if(started==0)return 0;
        double t=Math.clamp((System.nanoTime()-started)/220_000_000d,0d,1d);
        return Math.pow(1-t,3);
    }
}
