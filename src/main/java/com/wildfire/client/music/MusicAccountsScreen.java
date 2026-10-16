package com.wildfire.client.music;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Compatibility entry point; the retired API-control UI is no longer presented. */
public final class MusicAccountsScreen extends Screen {
    private final Screen parent;
    public MusicAccountsScreen(Screen parent) { super(Component.literal("Музыка")); this.parent = parent; }
    @Override protected void init() { minecraft.gui.setScreen(new MusicBrowserScreen(parent)); }
    @Override public boolean isPauseScreen() { return false; }
}

