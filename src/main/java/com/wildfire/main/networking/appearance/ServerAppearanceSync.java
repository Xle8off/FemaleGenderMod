package com.wildfire.main.networking.appearance;

import com.wildfire.gui.screen.WildfireBreastCustomizationScreen;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.config.enums.Gender;
import com.wildfire.main.entitydata.PlayerConfigHolder;
import com.wildfire.render.CosmeticLayer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerAppearanceSync {
    private static volatile boolean active;
    private static long lastSkinReady;
    private static boolean loggedSkinReady;
    private static final Map<UUID, BreastShape> SHAPES = new ConcurrentHashMap<>();
    private static final Map<UUID, Cosmetics> COSMETICS = new ConcurrentHashMap<>();

    public record Cosmetics(int shirt, int pants, int headwear, int pet) {
        public static final Cosmetics NONE = new Cosmetics(0, 0, 0, 0);
    }

    private ServerAppearanceSync() {}

    public static boolean isActive() {
        return active;
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(RouteProbePacket.TYPE, RouteProbePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(com.wildfire.client.screens.ScreenPacket.TYPE,com.wildfire.client.screens.ScreenPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(com.wildfire.client.screens.ScreenPacket.TYPE,com.wildfire.client.screens.ScreenPacket.CODEC);
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vivecraft")){
            PayloadTypeRegistry.clientboundPlay().register(com.wildfire.client.vr.VrPosePacket.TYPE,com.wildfire.client.vr.VrPosePacket.CODEC);
            PayloadTypeRegistry.serverboundPlay().register(com.wildfire.client.vr.VrPosePacket.TYPE,com.wildfire.client.vr.VrPosePacket.CODEC);
        }
        PayloadTypeRegistry.serverboundPlay().register(com.wildfire.client.overhead.CameraPacket.TYPE,com.wildfire.client.overhead.CameraPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(com.wildfire.client.overhead.CameraPacket.TYPE,com.wildfire.client.overhead.CameraPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(com.wildfire.client.towns.TownsPacket.TYPE,com.wildfire.client.towns.TownsPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(com.wildfire.client.towns.TownsPacket.TYPE,com.wildfire.client.towns.TownsPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(com.wildfire.client.music.CardBalancePackets.Request.TYPE,com.wildfire.client.music.CardBalancePackets.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(com.wildfire.client.music.CardBalancePackets.Snapshot.TYPE,com.wildfire.client.music.CardBalancePackets.Snapshot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MenuPacket.TYPE, MenuPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MenuPacket.TYPE, MenuPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ListeningPacket.TYPE, ListeningPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChatStatePacket.TYPE, ChatStatePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ListeningPacket.TYPE, ListeningPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AppearancePackets.Hello.TYPE, AppearancePackets.Hello.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AppearancePackets.Notification.TYPE, AppearancePackets.Notification.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(com.wildfire.client.music.ApiNoticePackets.Notice.TYPE,com.wildfire.client.music.ApiNoticePackets.Notice.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(com.wildfire.client.music.ApiNoticePackets.Ack.TYPE,com.wildfire.client.music.ApiNoticePackets.Ack.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(com.wildfire.client.music.CampaignControlPacket.TYPE,com.wildfire.client.music.CampaignControlPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SocialPackets.Request.TYPE, SocialPackets.Request.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SocialPackets.Action.TYPE, SocialPackets.Action.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MessagePackets.Request.TYPE, MessagePackets.Request.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(com.wildfire.client.music.GroupPackets.Request.TYPE, com.wildfire.client.music.GroupPackets.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(com.wildfire.client.music.GroupPackets.Snapshot.TYPE, com.wildfire.client.music.GroupPackets.Snapshot.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AppearancePackets.NotificationPreference.TYPE, AppearancePackets.NotificationPreference.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AppearancePackets.NotificationProbe.TYPE, AppearancePackets.NotificationProbe.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AppearancePackets.NotificationAck.TYPE, AppearancePackets.NotificationAck.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SocialPackets.Snapshot.TYPE, SocialPackets.Snapshot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MessagePackets.Snapshot.TYPE, MessagePackets.Snapshot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MusicPackets.NowPlaying.TYPE, MusicPackets.NowPlaying.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AppearancePackets.HelloAck.TYPE, AppearancePackets.HelloAck.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AppearancePackets.Disable.TYPE, AppearancePackets.Disable.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AppearancePackets.SkinReady.TYPE, AppearancePackets.SkinReady.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AppearancePackets.OpenCustomization.TYPE, AppearancePackets.OpenCustomization.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AppearancePackets.Snapshot.TYPE, AppearancePackets.Snapshot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AppearancePackets.Update.TYPE, AppearancePackets.Update.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AppearancePackets.Relationship.TYPE, AppearancePackets.Relationship.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AppearancePackets.CustomizationResult.TYPE, AppearancePackets.CustomizationResult.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CosmeticCatalogPackets.Reset.TYPE, CosmeticCatalogPackets.Reset.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CosmeticCatalogPackets.Entry.TYPE, CosmeticCatalogPackets.Entry.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CosmeticCatalogPackets.Chunk.TYPE, CosmeticCatalogPackets.Chunk.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CosmeticCatalogPackets.Request.TYPE, CosmeticCatalogPackets.Request.CODEC);
    }

    public static void registerClient() {
        com.wildfire.client.vr.VrPoseClient.configure(()->net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vivecraft"));
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vivecraft")){
            com.wildfire.client.vr.VivecraftObserver.configure(bytes->ClientPlayNetworking.send(new com.wildfire.client.vr.VrPosePacket(bytes)),()->ClientPlayNetworking.canSend(com.wildfire.client.vr.VrPosePacket.TYPE));
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->com.wildfire.client.vr.VivecraftObserver.tick());
        }
        com.wildfire.gui.LastSeenLabel.setGenderResolver(GenderedNotifications::gender);
        ClientPlayConnectionEvents.INIT.register((listener, client) -> {
            if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vivecraft"))ClientPlayNetworking.registerReceiver(com.wildfire.client.vr.VrPosePacket.TYPE,(packet,context)->context.client().execute(()->com.wildfire.client.vr.VivecraftObserver.accept(packet.data())));
            com.wildfire.client.towns.TownsClient.configure(bytes->ClientPlayNetworking.send(new com.wildfire.client.towns.TownsPacket(bytes)),
                ()->ClientPlayNetworking.canSend(com.wildfire.client.towns.TownsPacket.TYPE));
            com.wildfire.client.towns.TownsClient.setOpenHandler(()->{if(!(client.gui.screen() instanceof com.wildfire.gui.TownsScreen))client.gui.setScreen(new com.wildfire.gui.TownsScreen(client.gui.screen()));});
            com.wildfire.client.towns.TownsClient.configureImageTicket(PrivateMessageInbox::requestImageTicket);
            ClientPlayNetworking.registerReceiver(com.wildfire.client.towns.TownsPacket.TYPE,(packet,context)->context.client().execute(()->com.wildfire.client.towns.TownsClient.accept(packet.data())));
            com.wildfire.client.screens.ScreenClient.configure(bytes->ClientPlayNetworking.send(new com.wildfire.client.screens.ScreenPacket(bytes)),()->ClientPlayNetworking.canSend(com.wildfire.client.screens.ScreenPacket.TYPE));
            ClientPlayNetworking.registerReceiver(com.wildfire.client.screens.ScreenPacket.TYPE,(packet,context)->context.client().execute(()->com.wildfire.client.screens.ScreenClient.accept(packet.data())));
            com.wildfire.client.music.CardBalanceClient.configure(nonce->{
                if(!ClientPlayNetworking.canSend(com.wildfire.client.music.CardBalancePackets.Request.TYPE))throw new IllegalStateException("Unavailable");
                ClientPlayNetworking.send(new com.wildfire.client.music.CardBalancePackets.Request(nonce));
            });
            ClientPlayNetworking.registerReceiver(com.wildfire.client.music.CardBalancePackets.Snapshot.TYPE,(packet,context)->context.client().execute(()->com.wildfire.client.music.CardBalanceClient.accept(packet.json())));
            ClientPlayNetworking.registerReceiver(MenuPacket.TYPE, (packet, context) -> context.client().schedule(() -> com.wildfire.client.music.MenuPresence.accept(packet.json())));
            ClientPlayNetworking.registerReceiver(ListeningPacket.TYPE, (packet, context) -> context.client().schedule(() -> com.wildfire.client.music.ListeningState.accept(packet.json())));
            ClientPlayNetworking.registerReceiver(ChatStatePacket.TYPE, (packet, context) -> context.client().execute(() -> com.wildfire.gui.PublicChatState.acceptControl(packet.state())));
            ClientPlayNetworking.registerReceiver(AppearancePackets.Notification.TYPE, (packet, context) ->
                context.client().schedule(() -> {
                    if (packet.category().equals("ЛС") || packet.category().equals("ЛС исходящее")) {
                        boolean outgoing = packet.category().equals("ЛС исходящее");
                        int unread = PrivateMessageInbox.receive(packet.title(), outgoing, packet.message());
                        if (!outgoing && unread > 0) {
                            NotificationInbox.privateUnread(packet.title(), unread);
                            ModUpdate.notificationFeedback("ЛС", packet.title(), "Непрочитанных: " + unread);
                        }
                        return;
                    }
                    String message = GenderedNotifications.format(packet.category(), packet.message());
                    NotificationInbox.add(packet.category(), packet.title(), message);
                    ModUpdate.notificationFeedback(packet.category(), packet.title(), message);
                }));
            ClientPlayNetworking.registerReceiver(AppearancePackets.NotificationProbe.TYPE, (packet, context) -> {
                if (packet.value() == 1) ClientPlayNetworking.send(new AppearancePackets.NotificationAck((byte) 1));
            });
            com.wildfire.client.music.ApiNoticeInbox.configure(value->{if(!ClientPlayNetworking.canSend(com.wildfire.client.music.ApiNoticePackets.Ack.TYPE))throw new IllegalStateException("Unavailable");ClientPlayNetworking.send(new com.wildfire.client.music.ApiNoticePackets.Ack(value));},
                (title,text)->{NotificationInbox.add("Система",title,text);ModUpdate.notificationFeedback("Система",title,text);});
            com.wildfire.client.music.CampaignClient.configure(value->ClientPlayNetworking.send(new com.wildfire.client.music.CampaignControlPacket(value)),
                ()->ClientPlayNetworking.canSend(com.wildfire.client.music.CampaignControlPacket.TYPE));
            ClientPlayNetworking.registerReceiver(com.wildfire.client.overhead.CameraPacket.TYPE,(packet,context)->context.client().execute(()->com.wildfire.client.overhead.CameraClient.accept(packet.json())));
            com.wildfire.client.overhead.CameraClient.configure(value->ClientPlayNetworking.send(new com.wildfire.client.overhead.CameraPacket(value)),()->ClientPlayNetworking.canSend(com.wildfire.client.overhead.CameraPacket.TYPE));
            ClientPlayNetworking.registerReceiver(com.wildfire.client.music.ApiNoticePackets.Notice.TYPE,(packet,context)->context.client().execute(()->com.wildfire.client.music.ApiNoticeInbox.receive(packet.json())));
            ClientPlayNetworking.registerReceiver(SocialPackets.Snapshot.TYPE, (packet, context) ->
                context.client().schedule(() -> SocialClient.accept(packet.json())));
            ClientPlayNetworking.registerReceiver(MessagePackets.Snapshot.TYPE, (packet, context) ->
                context.client().schedule(() -> PrivateMessageInbox.accept(packet.json())));
            com.wildfire.client.music.GroupInbox.configure(json -> ClientPlayNetworking.send(new com.wildfire.client.music.GroupPackets.Request(json)),
                () -> Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getUUID().toString(),
                (title, message) -> ModUpdate.notificationFeedback("Группа", title, message));
            ClientPlayNetworking.registerReceiver(com.wildfire.client.music.GroupPackets.Snapshot.TYPE, (packet, context) ->
                context.client().schedule(() -> com.wildfire.client.music.GroupInbox.accept(packet.json())));
            ClientPlayNetworking.registerReceiver(MusicPackets.NowPlaying.TYPE, (packet, context) ->
                context.client().schedule(() -> MusicClient.accept(packet)));
            ClientPlayNetworking.registerReceiver(AppearancePackets.Hello.TYPE, (packet, context) -> {
                com.wildfire.client.vr.VivecraftObserver.reset();
                // SkinSystem lives on the proxy and uses its own texture protocol.
                // A legacy lobby may advertise appearance protocol 2/7, but it must
                // still receive skin/cape readiness and must not trigger a mod update.
                ClientPlayNetworking.send(new AppearancePackets.SkinReady((byte) 1));
                if (packet.version() > 0 && packet.version() < 10) {
                    active = false;
                    resetOnlinePlayers(context.client());
                    WildfireGender.LOGGER.info("Legacy appearance protocol {} on this server; HD skins and capes remain available", packet.version());
                    return;
                }
                ModUpdate.check(packet.version(), () -> {
                    active = true;
                    resetOnlinePlayers(context.client());
                    ClientPlayNetworking.send(new AppearancePackets.HelloAck(packet.version()));
                }, () -> {
                    active = false;
                    resetOnlinePlayers(context.client());
                    HdSkinClient.clearAll();
                    CapeClient.clearAll();
                    GarmentClient.clearAll();
                    CosmeticCatalogClient.reset();
                    ClientPlayNetworking.send(new AppearancePackets.Disable((byte) 1));
                });
            });
            ClientPlayNetworking.registerReceiver(AppearancePackets.Snapshot.TYPE, (packet, context) -> {
                if (!active) return;
                resetOnlinePlayers(context.client());
                packet.entries().forEach(entry -> apply(entry.uuid(), true, entry.female(), entry.breastSize(), entry.shape(),
                    entry.shirt(), entry.pants(), entry.headwear(), entry.pet()));
            });
            ClientPlayNetworking.registerReceiver(AppearancePackets.Update.TYPE, (packet, context) -> {
                if (!active) return;
                if (!packet.visible()) {
                    apply(packet.uuid(), false, false, 0f, 0, 0, 0, 0, 0);
                    return;
                }
                apply(packet.uuid(), true, packet.female(), packet.breastSize(), packet.shape(), packet.shirt(), packet.pants(),
                    packet.headwear(), packet.pet());
            });
            ClientPlayNetworking.registerReceiver(AppearancePackets.Relationship.TYPE, (packet, context) -> {
                if (active) RelationshipClient.accept(packet.owner(), packet.spouses());
            });
            ClientPlayNetworking.registerReceiver(AppearancePackets.OpenCustomization.TYPE, (packet, context) -> {
                if (!active) return;
                var player = context.player();
                apply(player.getUUID(), true, packet.female(), packet.breastSize(), packet.shape(), packet.shirt(), packet.pants(), packet.headwear(), packet.pet());
                //~ if >=26.2 'context.client().setScreen' -> 'context.client().gui.setScreen'
                context.client().schedule(() -> context.client().gui.setScreen(
                    new WildfireBreastCustomizationScreen(null, player.getUUID(), packet.token(), packet.female(), packet.figureEditable(), packet.breastSize(),
                        BreastShape.fromId(packet.shape()), new Cosmetics(packet.shirt(), packet.pants(), packet.headwear(), packet.pet()))
                ));
            });
            ClientPlayNetworking.registerReceiver(CosmeticCatalogPackets.Reset.TYPE,
                (packet,context)->context.client().schedule(()->CosmeticCatalogClient.beginCatalog(packet.marker())));
            ClientPlayNetworking.registerReceiver(CosmeticCatalogPackets.Entry.TYPE,
                (packet,context)->context.client().schedule(()->CosmeticCatalogClient.accept(packet)));
            ClientPlayNetworking.registerReceiver(CosmeticCatalogPackets.Chunk.TYPE,
                (packet,context)->context.client().schedule(()->CosmeticCatalogClient.accept(packet)));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            active = false;
            client.execute(() -> {
            com.wildfire.client.towns.TownsClient.reset();
            com.wildfire.client.screens.ScreenClient.reset();
            com.wildfire.gui.ChatAvatars.clear();
            com.wildfire.client.overhead.OverheadCameraTextures.clear();
            com.wildfire.client.overhead.CameraClient.reset();
            com.wildfire.client.music.CardBalanceClient.reset();
            active = false;
            lastSkinReady = 0;
            loggedSkinReady = false;
            ModUpdate.reset();
            CosmeticLayer.clearDynamicCache();
            CosmeticCatalogClient.reset();
            RelationshipClient.clear();
            ChibiSkinClient.clear();
            });
        });
    }

    /** Velocity backend switches can clear proxy readiness without a new client login. */
    public static void tickSkinReady() {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || ModUpdate.isDeclined()) return;
        com.wildfire.client.towns.TownsClient.announce();
        long now = System.nanoTime();
        if (now - lastSkinReady < 5_000_000_000L) return;
        lastSkinReady = now;
        ClientPlayNetworking.send(new AppearancePackets.SkinReady((byte) 1));
        if (!loggedSkinReady) {
            loggedSkinReady = true;
            WildfireGender.LOGGER.info("[skin-sync] Sent skin_ready to proxy for {}", client.player.getUUID());
        }
    }

    public static boolean submit(UUID token, float breastSize, BreastShape shape, Cosmetics original, Cosmetics cosmetics) {
        if (!active || !Float.isFinite(breastSize) || breastSize < 0f || breastSize > 0.8f) return false;
        ClientPlayNetworking.send(new AppearancePackets.CustomizationResult(token, breastSize, shape.id(),
            cosmetics.shirt(), cosmetics.pants(), cosmetics.headwear(), cosmetics.pet()));
        return true;
    }

    public static BreastShape shape(UUID uuid) { return SHAPES.getOrDefault(uuid, BreastShape.ROUND); }
    public static void preview(UUID uuid, BreastShape shape) { SHAPES.put(uuid, shape); }
    public static Cosmetics cosmetics(UUID uuid) { return COSMETICS.getOrDefault(uuid, Cosmetics.NONE); }
    public static void preview(UUID uuid, Cosmetics cosmetics) { COSMETICS.put(uuid, cosmetics); }

    private static void resetOnlinePlayers(Minecraft client) {
        if (client.getConnection() == null) return;
        SHAPES.clear();
        COSMETICS.clear();
        client.getConnection().getOnlinePlayers().forEach(info -> apply(info.getProfile().id(), false, false, 0f, 0, 0, 0, 0, 0));
    }

    private static void apply(UUID uuid, boolean visible, boolean female, float size, int shape, int shirt, int pants, int headwear, int pet) {
        PlayerConfigHolder holder = WildfireGender.getOrAddPlayerById(uuid);
        holder.gender().update(visible && female ? Gender.FEMALE : Gender.MALE);
        holder.breasts().bustSize().update(visible && female ? Math.clamp(size, 0f, 0.8f) : 0f);
        if (visible) SHAPES.put(uuid, BreastShape.fromId(shape)); else SHAPES.remove(uuid);
        if (visible) COSMETICS.put(uuid, new Cosmetics(shirt, pants,
            Math.clamp(headwear, 0, 100000), Math.clamp(pet, 0, 100000))); else COSMETICS.remove(uuid);
        if (visible) ModUpdate.appearanceUpdated(uuid);
        holder.syncStatus = PlayerConfigHolder.SyncStatus.SYNCED;
        holder.needsSync = false;
        holder.needsCloudSync = false;
    }
}
