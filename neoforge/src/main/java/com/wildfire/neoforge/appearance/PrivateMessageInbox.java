package com.wildfire.neoforge.appearance;

import com.google.gson.JsonParser;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recent server history plus live conversations; full bodies never enter toast history. */
public final class PrivateMessageInbox {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("SpringPlus private messages");
    public record Message(long receivedAt, boolean outgoing, String body) {}
    public record Conversation(String partner, int unread, long updatedAt, boolean online, String role) {}
    private static final int MAX_PARTNERS = 40;
    private static final int MAX_MESSAGES = 100;
    private static final Map<String, Thread> THREADS = new LinkedHashMap<>();
    private static String openPartner = "";
    private static final Map<String, Long> LAST_SEEN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Long> TYPING = new java.util.concurrent.ConcurrentHashMap<>();
    private static long lastTypingSent;
    public static void typing(String partner) {
        long now = System.currentTimeMillis();
        if (!partner.matches("[A-Za-z0-9_]{1,16}") || now - lastTypingSent < 2000
            || net.minecraft.client.Minecraft.getInstance().getConnection() == null) return;
        lastTypingSent = now;
        ClientPacketDistributor.sendToServer(new MessagePackets.Request((byte) 3, partner));
    }
    public static void activity(String partner,String state) {
        if(!partner.matches("[A-Za-z0-9_]{1,16}") || net.minecraft.client.Minecraft.getInstance().getConnection()==null)return;
        byte kind=switch(state){case "typing" -> 3;case "sticker" -> 4;case "emoji" -> 5;default -> 6;};
        ClientPacketDistributor.sendToServer(new MessagePackets.Request(kind,partner));
    }
    public static void requestImageTicket(){ClientPacketDistributor.sendToServer(new MessagePackets.Request((byte)7,""));}
    public static String presence(String partner) {
        String activity=com.wildfire.gui.ChatActivity.label(partner,false);
        if(!activity.isEmpty())return activity;
        Thread thread = THREADS.get(partner.toLowerCase(java.util.Locale.ROOT));
        if (thread != null && thread.online && thread.dnd) return "Не беспокоить";
        if (thread != null && thread.online && thread.afk) return "AFK";
        if (thread != null && thread.online) return "в сети";
        return lastSeenLabel(partner);
    }
    public static String lastSeenLabel(String partner){
        long time=LAST_SEEN.getOrDefault(partner.toLowerCase(java.util.Locale.ROOT),0L);
        return com.wildfire.gui.LastSeenLabel.format(time,System.currentTimeMillis(),java.time.ZoneId.systemDefault(),GenderedNotifications.gender(partner));
    }
    private static final Map<String, Long> HISTORY_REQUESTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> HISTORY_LOADED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static String historyStatus(String partner) {
        String key = partner.toLowerCase(java.util.Locale.ROOT);
        if (TYPING.getOrDefault(key, 0L) > System.currentTimeMillis()) return "печатает…";
        long requested = HISTORY_REQUESTS.getOrDefault(key, 0L);
        if (requested != 0) return System.currentTimeMillis() - requested < 8000
            ? "Загружаем историю…" : "История недоступна. Откройте чат повторно.";
        return HISTORY_LOADED.contains(key) ? "Пока нет сообщений. Начните переписку." : "Ожидаем историю…";
    }

    private static final class Thread {
        final String partner;
        final List<Message> messages = new ArrayList<>();
        int unread;
        long updatedAt;
        boolean online;
        boolean afk;
        boolean dnd;
        String role = "";
        Thread(String partner) { this.partner = partner; }
    }

    private PrivateMessageInbox() {}

    public static synchronized boolean isAfk(String partner) {
        Thread thread = THREADS.get(partner.toLowerCase(java.util.Locale.ROOT));
        return thread != null && thread.online && thread.afk;
    }

    public static synchronized boolean isOnline(String partner) {
        Thread thread = THREADS.get(partner.toLowerCase(java.util.Locale.ROOT));
        return thread != null && thread.online;
    }

    public static synchronized boolean isDnd(String partner) {
        Thread thread = THREADS.get(partner.toLowerCase(java.util.Locale.ROOT));
        return thread != null && thread.online && thread.dnd;
    }

    public static int statusColor(String partner) {
        return isDnd(partner) ? 0xFFFF5965 : isAfk(partner) ? 0xFFFFC34D
            : isOnline(partner) ? 0xFF36CF79 : 0xFF929AA6;
    }

    public static synchronized int receive(String partner, boolean outgoing, String body) {
        if (!partner.matches("[A-Za-z0-9_]{1,16}") || body.isBlank()) return 0;
        String key = partner.toLowerCase(java.util.Locale.ROOT);
        Thread thread = THREADS.computeIfAbsent(key, ignored -> new Thread(partner));
        long now = System.currentTimeMillis();
        boolean duplicate = thread.messages.stream().anyMatch(message -> message.outgoing() == outgoing
            && message.body().equals(body) && Math.abs(now - message.receivedAt()) < 4_000L);
        if (duplicate) return thread.unread;
        thread.updatedAt = now;
        thread.messages.add(new Message(now, outgoing, body));
        while (thread.messages.size() > MAX_MESSAGES) thread.messages.removeFirst();
        if (!outgoing && !key.equals(openPartner)) thread.unread = Math.min(999, thread.unread + 1);
        if (THREADS.size() > MAX_PARTNERS) THREADS.entrySet().stream()
            .min(Comparator.comparingLong(entry -> entry.getValue().updatedAt))
            .ifPresent(entry -> THREADS.remove(entry.getKey()));
        return thread.unread;
    }

    public static synchronized List<Conversation> conversations() {
        // Only real message activity changes order; opening a chat never promotes it.
        return THREADS.values().stream()
            .sorted(Comparator.comparingLong((Thread value) -> value.updatedAt).reversed())
            .map(value -> new Conversation(value.partner, value.unread, value.updatedAt, value.online, value.role)).toList();
    }

    public static synchronized List<Message> messages(String partner) {
        Thread thread = THREADS.get(partner.toLowerCase(java.util.Locale.ROOT));
        return thread == null ? List.of() : List.copyOf(thread.messages);
    }

    public static synchronized void open(String partner) {
        com.wildfire.client.music.GroupInbox.close();
        openPartner = partner.toLowerCase(java.util.Locale.ROOT);
        Thread thread = THREADS.computeIfAbsent(openPartner, ignored -> new Thread(partner));
        thread.unread = 0;
    }

    public static synchronized void close() { openPartner = ""; com.wildfire.client.music.GroupInbox.close(); }
    private static long lastContactsSent;
    private static final java.util.Map<String, Long> lastHistorySent = new java.util.concurrent.ConcurrentHashMap<>();
    public static synchronized int unread() { return THREADS.values().stream().mapToInt(value -> value.unread).sum() + com.wildfire.client.music.GroupInbox.unread(); }
    public static synchronized void clear() { THREADS.clear(); openPartner = ""; HISTORY_REQUESTS.clear(); HISTORY_LOADED.clear(); LAST_SEEN.clear(); TYPING.clear(); com.wildfire.gui.ChatActivity.clear(); lastTypingSent = 0; lastContactsSent = 0; lastHistorySent.clear(); com.wildfire.client.music.GroupInbox.reset(); }

    public static void requestContacts() {
        long now = System.currentTimeMillis();
        if (now - lastContactsSent < 10_000L) return;
        if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) return;
        lastContactsSent = now;
        if (net.minecraft.client.Minecraft.getInstance().getConnection() != null)
            ClientPacketDistributor.sendToServer(new MessagePackets.Request((byte) 1, ""));
        else LOGGER.warn("Cannot request private message contacts: not connected");
    }

    public static void requestHistory(String partner) {
        if (!partner.matches("[A-Za-z0-9_]{1,16}") || net.minecraft.client.Minecraft.getInstance().getConnection() == null) return;
        String key = partner.toLowerCase(java.util.Locale.ROOT);
        long now = System.currentTimeMillis();
        if (now - lastHistorySent.getOrDefault(key, 0L) < 5_000L) return;
        if (lastHistorySent.size() > 512) lastHistorySent.clear();
        lastHistorySent.put(key, now);
        if (partner.matches("[A-Za-z0-9_]{1,16}")) HISTORY_REQUESTS.put(partner.toLowerCase(java.util.Locale.ROOT), System.currentTimeMillis());
        if (partner.matches("[A-Za-z0-9_]{1,16}") && net.minecraft.client.Minecraft.getInstance().getConnection() != null)
            ClientPacketDistributor.sendToServer(new MessagePackets.Request((byte) 2, partner));
        else LOGGER.warn("Cannot request private message history for {}: not connected", partner);
    }

    public static synchronized void accept(String json) {
        try {
            var root = JsonParser.parseString(json).getAsJsonObject();
            if(root.has("imageTicket")){com.wildfire.gui.ChatImages.ticket(root);return;}
            String partner = root.get("partner").getAsString();
            if (root.has("presenceOnly")) {
                String key = partner.toLowerCase(java.util.Locale.ROOT);
                Thread current = THREADS.get(key);
                if (current != null) {
                    current.online = root.has("online") && root.get("online").getAsBoolean();
                    current.afk = current.online && root.has("afk") && root.get("afk").getAsBoolean();
                    current.dnd = current.online && root.has("dnd") && root.get("dnd").getAsBoolean();
                    LAST_SEEN.put(key, root.has("lastSeen") ? root.get("lastSeen").getAsLong() : 0L);
                    if (!current.online) TYPING.remove(key);
                }
                return;
            }
            if (root.has("typing")) {
                String state=root.has("activity")?root.get("activity").getAsString():root.get("typing").getAsBoolean()?"typing":"idle";
                com.wildfire.gui.ChatActivity.accept(partner,partner,state);
                if (partner.matches("[A-Za-z0-9_]{1,16}")) {
                    if(state.equals("idle"))TYPING.remove(partner.toLowerCase(java.util.Locale.ROOT));
                    else TYPING.put(partner.toLowerCase(java.util.Locale.ROOT), System.currentTimeMillis() + 5000);
                }
                TYPING.entrySet().removeIf(entry -> entry.getValue() < System.currentTimeMillis());
                return;
            }
            if (partner.isEmpty()) {
                for (var item : root.getAsJsonArray("contacts")) {
                    var row = item.getAsJsonObject();
                    String name = row.get("name").getAsString();
                    if (!name.matches("[A-Za-z0-9_]{1,16}")) continue;
                    Thread thread = THREADS.computeIfAbsent(name.toLowerCase(java.util.Locale.ROOT), ignored -> new Thread(name));
                    if (row.has("updatedAt")) thread.updatedAt = Math.max(thread.updatedAt, row.get("updatedAt").getAsLong());
                    if (row.has("presenceVerified") && row.get("presenceVerified").getAsBoolean()) {
                        thread.online = row.has("online") && row.get("online").getAsBoolean();
                        thread.afk = thread.online && row.has("afk") && row.get("afk").getAsBoolean();
                        thread.dnd = thread.online && row.has("dnd") && row.get("dnd").getAsBoolean();
                        LAST_SEEN.put(name.toLowerCase(java.util.Locale.ROOT), row.has("lastSeen") ? row.get("lastSeen").getAsLong() : 0L);
                    }
                    if (row.has("role") && !row.get("role").getAsString().isBlank())
                        thread.role = row.get("role").getAsString();
                }
                LOGGER.info("Private message contacts received: {}", root.getAsJsonArray("contacts").size());
                return;
            }
            if (!partner.matches("[A-Za-z0-9_]{1,16}")) return;
            HISTORY_REQUESTS.remove(partner.toLowerCase(java.util.Locale.ROOT));
            HISTORY_LOADED.add(partner.toLowerCase(java.util.Locale.ROOT));
            Thread thread = THREADS.computeIfAbsent(partner.toLowerCase(java.util.Locale.ROOT), ignored -> new Thread(partner));
            if (root.has("presenceVerified") && root.get("presenceVerified").getAsBoolean()) {
                thread.online = root.has("online") && root.get("online").getAsBoolean();
                thread.afk = thread.online && root.has("afk") && root.get("afk").getAsBoolean();
                thread.dnd = thread.online && root.has("dnd") && root.get("dnd").getAsBoolean();
                LAST_SEEN.put(partner.toLowerCase(java.util.Locale.ROOT), root.has("lastSeen") ? root.get("lastSeen").getAsLong() : 0L);
            }
            if (root.has("role") && !root.get("role").getAsString().isBlank())
                thread.role = root.get("role").getAsString();
            for (var item : root.getAsJsonArray("messages")) {
                var row = item.getAsJsonObject();
                long time = row.get("createdAtEpochMillis").getAsLong();
                boolean outgoing = !row.get("incoming").getAsBoolean();
                String body = row.get("body").getAsString();
                if (thread.messages.stream().noneMatch(message -> message.outgoing() == outgoing
                    && message.body().equals(body) && Math.abs(time - message.receivedAt()) < 4_000L))
                    thread.messages.add(new Message(time, outgoing, body));
                thread.updatedAt = Math.max(thread.updatedAt, time);
            }
            thread.messages.sort(Comparator.comparingLong(Message::receivedAt));
            while (thread.messages.size() > MAX_MESSAGES) thread.messages.removeFirst();
            LOGGER.info("Private message history received: partner={} count={}", partner, root.getAsJsonArray("messages").size());
        } catch (Exception error) { LOGGER.warn("Could not read private message snapshot", error); }
    }
}
