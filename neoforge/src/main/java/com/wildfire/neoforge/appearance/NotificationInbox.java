package com.wildfire.neoforge.appearance;

import java.util.ArrayDeque;
import java.util.List;

/** Client-side, bounded history of SpringPlus events. */
public final class NotificationInbox {
    public record Entry(long receivedAt, String category, String title, String message) {}
    private static final int LIMIT = 100;
    private static final ArrayDeque<Entry> ENTRIES = new ArrayDeque<>();
    private static final java.util.Set<Entry> UNREAD = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private NotificationInbox() {}

    public static String sender(String category, String title, String message) {
        if ("ЛС".equals(category) && title.matches("[A-Za-z0-9_]{1,16}")) return title;
        if (!"Друзья".equals(category)) return "";
        var named = java.util.regex.Pattern.compile("^(?:Заявка от |Ваша заявка принята: )([A-Za-z0-9_]{1,16})$").matcher(message);
        if (named.matches()) return named.group(1);
        var presence = java.util.regex.Pattern.compile("^([A-Za-z0-9_]{1,16}) (?:вошёл в сеть|вошла в сеть|вышел из сети|вышла из сети|теперь в сети|теперь не в сети|принял вашу заявку|приняла вашу заявку)$").matcher(message);
        return presence.matches() ? presence.group(1) : "";
    }

    public static String playerIdentity(String name) {
        String role = SocialClient.rows().stream().filter(row -> row.name().equalsIgnoreCase(name))
            .map(row -> row.role()).filter(value -> value != null && !value.isBlank()).findFirst().orElse("");
        if (role.isBlank()) role = PrivateMessageInbox.conversations().stream()
            .filter(row -> row.partner().equalsIgnoreCase(name)).map(row -> row.role())
            .filter(value -> value != null && !value.isBlank()).findFirst().orElse("");
        return role.isBlank() ? name : role + " " + name;
    }
    public static synchronized void add(String category, String title, String message) {
        Entry entry = new Entry(System.currentTimeMillis(), category, title, message);
        ENTRIES.addFirst(entry);
        UNREAD.add(entry);
        while (ENTRIES.size() > LIMIT) UNREAD.remove(ENTRIES.removeLast());
    }

    public static synchronized void privateUnread(String partner, int count) {
        removePrivate(partner);
        if (count <= 0) return;
        add("ЛС", partner, "Непрочитанных личных сообщений: " + count);
    }

    public static synchronized void privateRead(String partner) {
        removePrivate(partner);
    }

    private static void removePrivate(String partner) {
        ENTRIES.removeIf(entry -> {
            boolean remove = entry.category.equals("ЛС") && entry.title.equalsIgnoreCase(partner);
            if (remove) UNREAD.remove(entry);
            return remove;
        });
    }
    public static synchronized List<Entry> entries() { return List.copyOf(ENTRIES); }
    public static synchronized int unread() { return UNREAD.size(); }
    public static synchronized void markRead() { UNREAD.clear(); }
    public static synchronized void markRead(List<Entry> visible) {
        visible.forEach(UNREAD::remove);
    }
}
