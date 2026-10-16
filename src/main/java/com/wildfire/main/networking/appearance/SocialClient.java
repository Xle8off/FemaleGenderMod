package com.wildfire.main.networking.appearance;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.ArrayList;
import java.util.List;

public final class SocialClient {
    public record Row(String kind, String name, String status, String role) {}
    private static volatile List<Row> rows = List.of();
    private static volatile boolean loaded;
    private static volatile String roleName = "";
    private static volatile long expires=-1;
    public static String expiryLabel(){return com.wildfire.gui.PrivilegeTime.label(expires,System.currentTimeMillis());}
    private static volatile long updatedAt;
    private static volatile long requestedAt;
    private static volatile boolean available = true;
    private static volatile long actionUntil;
    private static volatile String actionTarget = "";
    public static boolean busy(String name) { return name.equalsIgnoreCase(actionTarget) && System.currentTimeMillis() < actionUntil; }

    private SocialClient() {}
    public static List<Row> rows() { return rows; }
    public static boolean loaded() { return loaded; }
    public static String roleName() { return roleName; }
    public static long updatedAt() { return updatedAt; }
    public static String status() {
        if (!available) return "Прокси не объявил канал друзей";
        if (requestedAt > updatedAt && System.currentTimeMillis() - requestedAt > 6_000L) return "Нет ответа от прокси";
        return loaded ? "данные получены" : "ожидаем прокси";
    }

    public static void request() {
        if (System.currentTimeMillis() - requestedAt < 10_000L) return;
        available = ClientPlayNetworking.canSend(SocialPackets.Request.TYPE);
        requestedAt = System.currentTimeMillis();
        if (available) ClientPlayNetworking.send(new SocialPackets.Request((byte) 1));
        else System.err.println("SpringPlusCosmetics friends: proxy does not advertise appearance_sync:social_request");
    }
    public static void action(String action, String target) {
        if (!target.matches("[A-Za-z0-9_]{1,16}") || busy(target)) return;
        if (ClientPlayNetworking.canSend(SocialPackets.Action.TYPE)) {
            actionTarget = target;
            actionUntil = System.currentTimeMillis() + 6000;
            ClientPlayNetworking.send(new SocialPackets.Action(action, target));
            ModUpdate.transientFeedback("Отправляем действие: " + target, -1f, 3);
        } else ModUpdate.transientFeedback("Сервис друзей недоступен", -1f, 5);
    }
    public static void accept(String json) {
        try {
            var object = JsonParser.parseString(json).getAsJsonObject();
            com.wildfire.gui.ModPresence.accept(object);
            expires=object.has("expires")&&!object.get("expires").isJsonNull()?object.get("expires").getAsLong():-1;
            roleName = object.has("roleName") && !object.get("roleName").isJsonNull()
                ? object.get("roleName").getAsString() : "";
            List<Row> next = new ArrayList<>();
            read(next, object.getAsJsonArray("incomingRequests"), "incoming", "Ожидает ответа");
            read(next, object.getAsJsonArray("friends"), "friend", "");
            read(next, object.getAsJsonArray("outgoingRequests"), "outgoing", "Заявка отправлена");
            rows = List.copyOf(next);
            loaded = true;
            available = true;
            updatedAt = System.currentTimeMillis();
            actionUntil = 0;
        } catch (Exception error) {
            ModUpdate.cosmeticFeedback("Не удалось прочитать список друзей", -1f, 7);
        }
    }
    public static void clear() { rows = List.of(); roleName = ""; expires=-1; loaded = false; requestedAt = 0; updatedAt = 0; available = true; }
    private static void read(List<Row> rows, com.google.gson.JsonArray array, String kind, String fallback) {
        if (array == null) return;
        for (var item : array) {
            if (rows.size() >= 200) break;
            var object = item.getAsJsonObject();
            String name = object.has("nickname") ? object.get("nickname").getAsString()
                : object.has("senderName") && "incoming".equals(kind) ? object.get("senderName").getAsString()
                : object.has("targetName") ? object.get("targetName").getAsString() : "?";
            String status = object.has("presenceStatus") ? object.get("presenceStatus").getAsString() : fallback;
            String role = object.has("role") && !object.get("role").isJsonNull() ? object.get("role").getAsString() : "";
            rows.add(new Row(kind, name, status, role));
        }
    }
}
