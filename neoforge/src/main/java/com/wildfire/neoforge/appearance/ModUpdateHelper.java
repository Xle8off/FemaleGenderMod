package com.wildfire.neoforge.appearance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Installs the newest verified pending release after Minecraft releases its JAR. */
public final class ModUpdateHelper {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) return;
        long parent = Long.parseLong(args[0]);
        Path current = Path.of(args[1]).toAbsolutePath().normalize();
        Path staged = Path.of(args[2]).toAbsolutePath().normalize();
        Path manifest = Path.of(args[3]).toAbsolutePath().normalize();
        if (!current.getParent().equals(staged.getParent()) || !current.getParent().equals(manifest.getParent())
            || !staged.getFileName().toString().equals(".springplus-update-neoforge-pending.bin")
            || !manifest.getFileName().toString().equals(".springplus-update-neoforge-pending.txt")) return;
        ProcessHandle.of(parent).ifPresent(process -> { try { process.onExit().get(); } catch (Exception ignored) {} });
        var lines = Files.readAllLines(manifest);
        if (lines.size() != 2 || !lines.get(0).matches("SpringPlusCosmetics-neoforge-[0-9]+\\.[0-9]+\\.[0-9]+-beta\\.[0-9]+\\+mc26\\.2\\.jar")
            || !lines.get(1).matches("[a-f0-9]{64}")) return;
        Path target = current.resolveSibling(lines.get(0));
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(staged)));
        if (!digest.equals(lines.get(1))) return;
        Path backup = current.resolveSibling(current.getFileName() + ".previous");
        boolean installed = false;
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                Files.move(current, backup, StandardCopyOption.REPLACE_EXISTING);
                try { Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING); }
                catch (Exception failure) { Files.move(backup, current, StandardCopyOption.REPLACE_EXISTING); throw failure; }
                installed = true;
                break;
            } catch (Exception failure) { Thread.sleep(1000); }
        }
        if (!installed) return;
        try { Files.deleteIfExists(manifest); }
        catch (Exception failure) { System.err.println("SpringPlusCosmetics: could not remove update manifest " + manifest); }
        for (int attempt = 0; attempt < 30; attempt++) {
            try { Files.deleteIfExists(backup); return; }
            catch (Exception failure) { Thread.sleep(1000); }
        }
        System.err.println("SpringPlusCosmetics: installed update, but could not remove old JAR " + backup);
    }
}
