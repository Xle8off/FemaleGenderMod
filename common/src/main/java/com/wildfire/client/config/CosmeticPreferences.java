package com.wildfire.client.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Client preferences; only the popup-only flag is synced to the chat server. */
public final class CosmeticPreferences {
    private static final Path FILE = Path.of("config", "springpluscosmetics-client.properties");
    private static boolean loaded;
    private static String cameraDevice="";
    private static int cameraWidth=640,cameraHeight=480,cameraFps=30;
    private static boolean cosmeticsVisible = true;
    private static boolean modHurtSound = true;
    private static boolean popupOnly;
    private static boolean notificationPopups = true;
    private static boolean hudBell = true;
    private static boolean hudTrack = true;
    private static boolean hudPlayer;
    private static boolean musicLabels = true, ownMusicLabel, shareListening = true;
    private static com.wildfire.client.overhead.OverheadMedia.Mode overheadMode=com.wildfire.client.overhead.OverheadMedia.Mode.MESSAGES;
    private static boolean overheadMessages=true,overheadCameras;

    private CosmeticPreferences() {}

    private static void load() {
        if (loaded) return;
        loaded = true;
        var properties = new Properties();
        if (Files.isRegularFile(FILE)) {
            try (var input = Files.newInputStream(FILE)) { properties.load(input); }
            catch (IOException ignored) { return; }
        }
        cosmeticsVisible = Boolean.parseBoolean(properties.getProperty("show-cosmetics", "true"));
        modHurtSound = Boolean.parseBoolean(properties.getProperty("mod-hurt-sound", "true"));
        popupOnly = Boolean.parseBoolean(properties.getProperty("notifications-popup-only", "false"));
        notificationPopups = Boolean.parseBoolean(properties.getProperty("notification-popups", "true"));
        hudBell = Boolean.parseBoolean(properties.getProperty("hud-bell", "true"));
        hudTrack = Boolean.parseBoolean(properties.getProperty("hud-track", "true"));
        hudPlayer = Boolean.parseBoolean(properties.getProperty("hud-player", "false"));
        musicLabels = Boolean.parseBoolean(properties.getProperty("music-labels", "true"));
        ownMusicLabel = Boolean.parseBoolean(properties.getProperty("own-music-label", "false"));
        shareListening = Boolean.parseBoolean(properties.getProperty("share-listening", "true"));
        try{overheadMode=com.wildfire.client.overhead.OverheadMedia.Mode.valueOf(properties.getProperty("overhead-mode","MESSAGES"));}catch(IllegalArgumentException ignored){}
        overheadMessages=Boolean.parseBoolean(properties.getProperty("overhead-messages","true"));
        overheadCameras=Boolean.parseBoolean(properties.getProperty("overhead-cameras","false"));
        cameraDevice=properties.getProperty("camera-device","");
        try{
            cameraWidth=Integer.parseInt(properties.getProperty("camera-width","640"));
            cameraHeight=Integer.parseInt(properties.getProperty("camera-height","480"));
            cameraFps=Integer.parseInt(properties.getProperty("camera-fps","30"));
        }catch(NumberFormatException ignored){cameraWidth=640;cameraHeight=480;cameraFps=30;}
    }

    public static boolean cosmeticsVisible() { load(); return cosmeticsVisible; }
    public static String cameraDevice(){load();return cameraDevice;}
    public static com.wildfire.client.overhead.CameraOptions.Mode cameraMode(){load();return new com.wildfire.client.overhead.CameraOptions.Mode(cameraWidth,cameraHeight,cameraFps);}
    public static void setCamera(String device,com.wildfire.client.overhead.CameraOptions.Mode mode){
        load();cameraDevice=device;cameraWidth=mode.width();cameraHeight=mode.height();cameraFps=mode.fps();save();
    }
    public static com.wildfire.client.overhead.OverheadMedia.Mode overheadMode(){load();return overheadMode;}
    public static boolean overheadMessages(){load();return overheadMessages;}
    public static boolean overheadCameras(){load();return overheadCameras;}
    public static void cycleOverheadMode(){load();var modes=com.wildfire.client.overhead.OverheadMedia.Mode.values();overheadMode=modes[(overheadMode.ordinal()+1)%modes.length];save();}
    public static void setOverheadMode(com.wildfire.client.overhead.OverheadMedia.Mode mode){load();if(overheadMode!=mode){overheadMode=mode;save();}}
    public static void toggleOverheadMessages(){load();overheadMessages=!overheadMessages;save();}
    public static void toggleOverheadCameras(){load();overheadCameras=!overheadCameras;save();}
    public static boolean modHurtSound() { load(); return modHurtSound; }
    public static boolean popupOnly() { load(); return popupOnly; }
    public static boolean notificationPopups() { load(); return notificationPopups; }
    public static void toggleNotificationPopups() { load(); notificationPopups = !notificationPopups; save(); }
    public static boolean hudBell() { load(); return hudBell; }
    public static boolean hudTrack() { load(); return hudTrack; }
    public static boolean hudPlayer() { load(); return hudPlayer; }
    public static void toggleHudPlayer() { load(); hudPlayer = !hudPlayer; save(); }
    public static void toggleCosmetics() { load(); cosmeticsVisible = !cosmeticsVisible; save(); }
    public static void toggleHurtSound() { load(); modHurtSound = !modHurtSound; save(); }
    public static void togglePopupOnly() { load(); popupOnly = !popupOnly; save(); }
    public static void toggleHudBell() { load(); hudBell = !hudBell; save(); }
    public static void toggleHudTrack() { load(); hudTrack = !hudTrack; save(); }
    public static boolean musicLabels() { load(); return musicLabels; }
    public static boolean ownMusicLabel() { load(); return ownMusicLabel; }
    public static boolean shareListening() { load(); return shareListening; }
    public static void toggleMusicLabels() { load(); musicLabels = !musicLabels; save(); }
    public static void toggleOwnMusicLabel() { load(); ownMusicLabel = !ownMusicLabel; save(); }
    public static void toggleShareListening() { load(); shareListening = !shareListening; save(); }

    private static void save() {
        var properties = new Properties();
        properties.setProperty("show-cosmetics", Boolean.toString(cosmeticsVisible));
        properties.setProperty("camera-device",cameraDevice);
        properties.setProperty("camera-width",Integer.toString(cameraWidth));
        properties.setProperty("camera-height",Integer.toString(cameraHeight));
        properties.setProperty("camera-fps",Integer.toString(cameraFps));
        properties.setProperty("mod-hurt-sound", Boolean.toString(modHurtSound));
        properties.setProperty("notifications-popup-only", Boolean.toString(popupOnly));
        properties.setProperty("notification-popups", Boolean.toString(notificationPopups));
        properties.setProperty("hud-bell", Boolean.toString(hudBell));
        properties.setProperty("hud-track", Boolean.toString(hudTrack));
        properties.setProperty("hud-player", Boolean.toString(hudPlayer));
        properties.setProperty("music-labels", Boolean.toString(musicLabels));
        properties.setProperty("own-music-label", Boolean.toString(ownMusicLabel));
        properties.setProperty("share-listening", Boolean.toString(shareListening));
        properties.setProperty("overhead-mode",overheadMode.name());
        properties.setProperty("overhead-messages",Boolean.toString(overheadMessages));
        properties.setProperty("overhead-cameras",Boolean.toString(overheadCameras));
        try {
            Files.createDirectories(FILE.getParent());
            try (var output = Files.newOutputStream(FILE)) { properties.store(output, "SpringPlusCosmetics client settings"); }
        } catch (IOException ignored) { /* Settings remain active for this session. */ }
    }
}
