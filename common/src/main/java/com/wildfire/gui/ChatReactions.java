package com.wildfire.gui;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Versioned offline emoji catalog; messages retain ordinary Unicode on the wire. */
public final class ChatReactions {
    public record Reaction(String id, String unicode, String label) {
        public String alias() { return ":" + id + ":"; }
        public String sticker() { return "[Стикер: " + label + "]"; }
    }
    public static final List<Reaction> LEGACY = List.of(
        new Reaction("smile", "😀", "Привет!"), new Reaction("laugh", "😂", "Ахаха"),
        new Reaction("love", "🥰", "Обнимаю"), new Reaction("cool", "😎", "Круто"),
        new Reaction("cry", "😭", "Плачу"), new Reaction("angry", "😡", "Злюсь"),
        new Reaction("think", "🤔", "Хм…"), new Reaction("like", "👍", "Согласен"),
        new Reaction("heart", "❤", "Любовь"), new Reaction("fire", "🔥", "Огонь"),
        new Reaction("party", "🎉", "Ура!"), new Reaction("skull", "💀", "Умер со смеху"));
    public static final List<Reaction> ALL = EmojiCatalog.load(LEGACY);
    private ChatReactions() {}
    /** Only the unselected tab is clickable; loading the catalog never disables switching. */
    public static boolean pickerTabActive(boolean stickersSelected, boolean stickersTab) {
        return stickersSelected != stickersTab;
    }
    public static Reaction sticker(String body) {
        return LEGACY.stream().filter(entry -> entry.sticker().equals(body)).findFirst().orElse(null);
    }
    /** Convert supported aliases, preserving all unsupported text as ordinary text. */
    public static Component text(String body) { return text(body, true); }
    public static Component input(String body) { return text(body, false); }
    private static Component text(String body, boolean aliases) {
        if (aliases) for (var entry : LEGACY) body = body.replace(entry.alias(), entry.unicode());
        return EmojiCatalog.text(body, !aliases);
    }
    public static void draw(GuiGraphicsExtractor graphics, Reaction reaction, int x, int y, int size) {
        EmojiCatalog.draw(graphics, reaction, x, y, size);
    }
}
