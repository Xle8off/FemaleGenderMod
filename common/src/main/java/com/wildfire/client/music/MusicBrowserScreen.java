package com.wildfire.client.music;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Direct entry to the last selected website, without an intermediate controls menu. */
public final class MusicBrowserScreen extends Screen {
    private final Screen parent;
    public MusicBrowserScreen(Screen parent) { super(Component.literal("Музыка")); this.parent = parent; }
    @Override protected void init() {
        minecraft.gui.setScreen(new SpotifyEmbeddedScreen(parent, SpotifyEmbeddedScreen.lastSite()));
    }
    @Override public boolean isPauseScreen() { return false; }
}
