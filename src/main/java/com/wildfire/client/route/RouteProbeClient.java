package com.wildfire.client.route;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RouteProbeClient {
    public record Snapshot(long measuredAt, Map<String, Long> results) {}
    private record Node(String host, int port) {}
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static volatile Snapshot latest;
    private static volatile long lastStarted;

    private RouteProbeClient() {}

    public static void tick(Minecraft client) {
        Object screen = client.gui.screen();
        if (screen == null || client.getConnection() != null) return;
        String screenName = screen.getClass().getSimpleName();
        if (!screenName.contains("Multiplayer") && !screenName.contains("ServerSelection") && !screenName.contains("JoinMultiplayer")) return;
        long now = System.currentTimeMillis();
        if (now - lastStarted < 30_000L || !RUNNING.compareAndSet(false, true)) return;
        lastStarted = now;
        EXECUTOR.submit(() -> {
            try {
                Map<String, Node> nodes = loadNodes();
                Map<String, Long> results = new ConcurrentHashMap<>();
                var jobs = nodes.entrySet().stream().map(entry -> EXECUTOR.submit(() -> {
                    Long ping = sample(entry.getValue());
                    if (ping != null) results.put(entry.getKey().toLowerCase(java.util.Locale.ROOT), ping);
                })).toList();
                for (var job : jobs) job.get();
                if (!results.isEmpty()) latest = new Snapshot(System.currentTimeMillis(), Map.copyOf(results));
            } catch (Exception ignored) {
            } finally {
                RUNNING.set(false);
            }
        });
    }

    public static byte[] freshPayload() {
        Snapshot snapshot = latest;
        if (snapshot == null || System.currentTimeMillis() - snapshot.measuredAt() > 120_000L) return null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(1);
                output.writeLong(snapshot.measuredAt());
                output.writeInt(snapshot.results().size());
                for (var result : snapshot.results().entrySet()) {
                    byte[] id = result.getKey().getBytes(StandardCharsets.UTF_8);
                    output.writeShort(id.length);
                    output.write(id);
                    output.writeLong(result.getValue());
                }
            }
            return bytes.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Long sample(Node node) {
        if (node == null || node.host() == null || node.host().isBlank() || node.port() < 1 || node.port() > 65535) return null;
        var samples = new ArrayList<Long>(3);
        for (int i = 0; i < 3; i++) {
            long started = System.nanoTime();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(node.host(), node.port()), 900);
                samples.add((System.nanoTime() - started) / 1_000_000L);
            } catch (Exception ignored) {}
        }
        if (samples.isEmpty()) return null;
        samples.sort(Long::compareTo);
        return samples.get(samples.size() / 2);
    }

    private static Map<String, Node> loadNodes() throws Exception {
        var path = Path.of("config", "springplus-route-nodes.json");
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Map<String, Node> defaults = new LinkedHashMap<>();
            defaults.put("main", new Node("5.83.140.255", 25565));
            defaults.put("ru", new Node("157.228.137.224", 25565));
            Files.writeString(path, GSON.toJson(defaults));
            return defaults;
        }
        java.lang.reflect.Type type = new TypeToken<LinkedHashMap<String, Node>>() {}.getType();
        Map<String, Node> loaded = GSON.fromJson(Files.readString(path), type);
        return loaded == null ? Map.of() : loaded;
    }
}
