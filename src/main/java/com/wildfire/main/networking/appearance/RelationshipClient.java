package com.wildfire.main.networking.appearance;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RelationshipClient {
    private static final Map<UUID, List<UUID>> SPOUSES = new ConcurrentHashMap<>();
    private RelationshipClient() {}
    public static List<UUID> spouses(UUID owner) { return SPOUSES.getOrDefault(owner, List.of()); }
    public static void accept(UUID owner, List<UUID> spouses) {
        List<UUID> unique = spouses.stream().filter(id -> id != null && !id.equals(owner)).distinct().limit(32).toList();
        if (unique.isEmpty()) SPOUSES.remove(owner);
        else SPOUSES.put(owner, unique);
    }
    public static void clear() { SPOUSES.clear(); }
}
