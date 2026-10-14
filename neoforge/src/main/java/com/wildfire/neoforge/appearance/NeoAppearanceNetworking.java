package com.wildfire.neoforge.appearance;

import com.wildfire.client.appearance.AppearanceState;
import com.wildfire.common.WildfireGender;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Optional SpringPlus protocol. The proxy plugins do not require this mod on their server. */
public final class NeoAppearanceNetworking {
    private static long lastSkinReady;
    private NeoAppearanceNetworking() {}

    /** Repeat readiness after Velocity backend switches, without requiring a new login. */
    public static void tickSkinReady() {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || ModUpdate.isDeclined()) return;
        com.wildfire.client.towns.TownsClient.announce();
        long now = System.nanoTime();
        if (now - lastSkinReady < 5_000_000_000L) return;
        lastSkinReady = now;
        ClientPacketDistributor.sendToServer(new AppearancePackets.SkinReady((byte) 1));
    }

    public static void resetSkinReady() { lastSkinReady = 0; }

    public static void register(IEventBus bus) {
        bus.addListener(RegisterPayloadHandlersEvent.class, NeoAppearanceNetworking::registerPackets);
    }

    private static void registerPackets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar packets = event.registrar("appearance_sync").optional();
        packets.playBidirectional(com.wildfire.client.towns.TownsPacket.TYPE,com.wildfire.client.towns.TownsPacket.CODEC,
            (packet,context)->context.enqueueWork(()->com.wildfire.client.towns.TownsClient.accept(packet.data())));
        com.wildfire.client.towns.TownsClient.configure(bytes->ClientPacketDistributor.sendToServer(new com.wildfire.client.towns.TownsPacket(bytes)),
            ()->Minecraft.getInstance().getConnection()!=null&&Minecraft.getInstance().getConnection().hasChannel(com.wildfire.client.towns.TownsPacket.TYPE));
        com.wildfire.gui.TownsScreen.setMessageOpener((parent,name)->Minecraft.getInstance().gui.setScreen(new com.wildfire.neoforge.client.NotificationCenterScreen(parent,name)));
        com.wildfire.gui.TownsScreen.setFriendAdder(name->SocialClient.action("add",name));
        com.wildfire.gui.LastSeenLabel.setGenderResolver(GenderedNotifications::gender);
        com.wildfire.client.towns.TownsClient.setOpenHandler(()->{var client=Minecraft.getInstance();if(!(client.gui.screen() instanceof com.wildfire.gui.TownsScreen))client.gui.setScreen(new com.wildfire.gui.TownsScreen(client.gui.screen()));});
        com.wildfire.client.towns.TownsClient.configureImageTicket(PrivateMessageInbox::requestImageTicket);
        packets.playBidirectional(MenuPacket.TYPE, MenuPacket.CODEC,
            (packet, context) -> context.enqueueWork(() -> com.wildfire.client.music.MenuPresence.accept(packet.json())));
        packets.playBidirectional(ListeningPacket.TYPE, ListeningPacket.CODEC,
            (packet, context) -> context.enqueueWork(() -> com.wildfire.client.music.ListeningState.accept(packet.json())));
        packets.playToClient(ChatStatePacket.TYPE, ChatStatePacket.CODEC,
            (packet, context) -> context.enqueueWork(() -> com.wildfire.gui.PublicChatState.acceptControl(packet.state())));
        packets.playToClient(AppearancePackets.Notification.TYPE, AppearancePackets.Notification.CODEC, (packet, context) ->
            context.enqueueWork(() -> {
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
        packets.playToServer(SocialPackets.Request.TYPE, SocialPackets.Request.CODEC, (packet, context) -> {});
        packets.playToServer(com.wildfire.client.music.CardBalancePackets.Request.TYPE,com.wildfire.client.music.CardBalancePackets.Request.CODEC,(packet,context)->{});
        com.wildfire.client.music.CardBalanceClient.configure(nonce->ClientPacketDistributor.sendToServer(new com.wildfire.client.music.CardBalancePackets.Request(nonce)));
        packets.playToClient(com.wildfire.client.music.CardBalancePackets.Snapshot.TYPE,com.wildfire.client.music.CardBalancePackets.Snapshot.CODEC,
            (packet,context)->context.enqueueWork(()->com.wildfire.client.music.CardBalanceClient.accept(packet.json())));
        packets.playToServer(SocialPackets.Action.TYPE, SocialPackets.Action.CODEC, (packet, context) -> {});
        packets.playToServer(MessagePackets.Request.TYPE, MessagePackets.Request.CODEC, (packet, context) -> {});
        packets.playToServer(com.wildfire.client.music.GroupPackets.Request.TYPE, com.wildfire.client.music.GroupPackets.Request.CODEC, (packet, context) -> {});
        packets.playToServer(AppearancePackets.NotificationPreference.TYPE, AppearancePackets.NotificationPreference.CODEC, (packet, context) -> {});
        packets.playToClient(AppearancePackets.NotificationProbe.TYPE, AppearancePackets.NotificationProbe.CODEC, (packet, context) -> {
            if (packet.value() == 1) ClientPacketDistributor.sendToServer(new AppearancePackets.NotificationAck((byte) 1));
        });
        packets.playToServer(AppearancePackets.NotificationAck.TYPE, AppearancePackets.NotificationAck.CODEC, (packet, context) -> {});
        packets.playToServer(com.wildfire.client.music.ApiNoticePackets.Ack.TYPE,com.wildfire.client.music.ApiNoticePackets.Ack.CODEC,(packet,context)->{});
        packets.playToServer(com.wildfire.client.music.CampaignControlPacket.TYPE,com.wildfire.client.music.CampaignControlPacket.CODEC,(packet,context)->{});
        com.wildfire.client.music.CampaignClient.configure(value->ClientPacketDistributor.sendToServer(new com.wildfire.client.music.CampaignControlPacket(value)),()->true);
        com.wildfire.client.music.ApiNoticeInbox.configure(value->ClientPacketDistributor.sendToServer(new com.wildfire.client.music.ApiNoticePackets.Ack(value)),
            (title,text)->{NotificationInbox.add("Система",title,text);ModUpdate.notificationFeedback("Система",title,text);});
        packets.playToClient(com.wildfire.client.music.ApiNoticePackets.Notice.TYPE,com.wildfire.client.music.ApiNoticePackets.Notice.CODEC,
            (packet,context)->context.enqueueWork(()->com.wildfire.client.music.ApiNoticeInbox.receive(packet.json())));
        packets.playToClient(SocialPackets.Snapshot.TYPE, SocialPackets.Snapshot.CODEC,
            (packet, context) -> context.enqueueWork(() -> SocialClient.accept(packet.json())));
        packets.playToClient(MessagePackets.Snapshot.TYPE, MessagePackets.Snapshot.CODEC,
            (packet, context) -> context.enqueueWork(() -> PrivateMessageInbox.accept(packet.json())));
        com.wildfire.client.music.GroupInbox.configure(json -> ClientPacketDistributor.sendToServer(new com.wildfire.client.music.GroupPackets.Request(json)),
            () -> net.minecraft.client.Minecraft.getInstance().player == null ? "" : net.minecraft.client.Minecraft.getInstance().player.getUUID().toString(),
            (title, message) -> ModUpdate.notificationFeedback("Группа", title, message));
        packets.playToClient(com.wildfire.client.music.GroupPackets.Snapshot.TYPE, com.wildfire.client.music.GroupPackets.Snapshot.CODEC,
            (packet, context) -> context.enqueueWork(() -> com.wildfire.client.music.GroupInbox.accept(packet.json())));
        packets.playToClient(MusicPackets.NowPlaying.TYPE, MusicPackets.NowPlaying.CODEC,
            (packet, context) -> context.enqueueWork(() -> MusicClient.accept(packet)));
        packets.playToClient(AppearancePackets.Hello.TYPE, AppearancePackets.Hello.CODEC, (packet, context) -> {
            // SkinSystem is a proxy service: a legacy lobby appearance protocol
            // must not suppress skin/cape transfers or prompt a false mod update.
            ClientPacketDistributor.sendToServer(new AppearancePackets.SkinReady((byte) 1));
            if (packet.version() > 0 && packet.version() < 10) {
                AppearanceState.deactivate();
                AppearanceState.resetProfiles();
                WildfireGender.LOGGER.info("Legacy appearance protocol {} on this server; HD skins and capes remain available", packet.version());
                return;
            }
            ModUpdate.check(packet.version(), () -> {
                AppearanceState.activate();
                AppearanceState.resetProfiles();
                ClientPacketDistributor.sendToServer(new AppearancePackets.HelloAck(packet.version()));
            }, () -> {
                AppearanceState.deactivate();
                AppearanceState.resetProfiles();
                HdSkinClient.clearAll();
                CapeClient.clearAll();
                GarmentClient.clearAll();
                CosmeticCatalogClient.reset();
                RelationshipClient.clear();
                ChibiSkinClient.clear();
                ClientPacketDistributor.sendToServer(new AppearancePackets.Disable((byte) 1));
            });
        });
        packets.playToClient(AppearancePackets.Snapshot.TYPE, AppearancePackets.Snapshot.CODEC, (packet, context) -> {
            if (!AppearanceState.isActive()) return;
            AppearanceState.resetProfiles();
            packet.entries().forEach(entry -> AppearanceState.apply(entry.uuid(), true, entry.female(), entry.breastSize(),
                entry.shape(), entry.shirt(), entry.pants(), entry.headwear(), entry.pet()));
        });
        packets.playToClient(AppearancePackets.Update.TYPE, AppearancePackets.Update.CODEC, (packet, context) -> {
            if (!AppearanceState.isActive()) return;
            if (!packet.visible()) {
                AppearanceState.apply(packet.uuid(), false, false, 0f, 0, 0, 0, 0, 0);
                return;
            }
            AppearanceState.apply(packet.uuid(), true, packet.female(), packet.breastSize(), packet.shape(),
                packet.shirt(), packet.pants(), packet.headwear(), packet.pet());
            ModUpdate.appearanceUpdated(packet.uuid());
        });
        packets.playToClient(AppearancePackets.Relationship.TYPE, AppearancePackets.Relationship.CODEC,
            (packet, context) -> { if (AppearanceState.isActive()) RelationshipClient.accept(packet.owner(), packet.spouses()); });
        packets.playToClient(AppearancePackets.OpenCustomization.TYPE, AppearancePackets.OpenCustomization.CODEC, (packet, context) -> {
            if (!AppearanceState.isActive()) return;
            if (Minecraft.getInstance().player != null) {
                var player = Minecraft.getInstance().player;
                AppearanceState.apply(player.getUUID(), true, packet.female(), packet.breastSize(),
                    packet.shape(), packet.shirt(), packet.pants(), packet.headwear(), packet.pet());
                Minecraft.getInstance().gui.setScreen(new WildfireBreastCustomizationScreen(null, player.getUUID(),
                    packet.token(), packet.female(), packet.figureEditable(), packet.breastSize(),
                    com.wildfire.client.appearance.BreastShape.fromId(packet.shape()),
                    new ServerAppearanceSync.Cosmetics(packet.shirt(), packet.pants(), packet.headwear(), packet.pet())));
            }
        });
        packets.playToServer(AppearancePackets.HelloAck.TYPE, AppearancePackets.HelloAck.CODEC, (packet, context) -> {});
        packets.playToServer(AppearancePackets.Disable.TYPE, AppearancePackets.Disable.CODEC, (packet, context) -> {});
        packets.playToServer(AppearancePackets.SkinReady.TYPE, AppearancePackets.SkinReady.CODEC, (packet, context) -> {});
        packets.playToServer(AppearancePackets.CustomizationResult.TYPE, AppearancePackets.CustomizationResult.CODEC, (packet, context) -> {});
        packets.playToClient(CosmeticCatalogPackets.Reset.TYPE, CosmeticCatalogPackets.Reset.CODEC,
            (packet,context)->Minecraft.getInstance().schedule(()->CosmeticCatalogClient.beginCatalog(packet.marker())));
        packets.playToClient(CosmeticCatalogPackets.Entry.TYPE, CosmeticCatalogPackets.Entry.CODEC,
            (packet,context)->Minecraft.getInstance().schedule(()->CosmeticCatalogClient.accept(packet)));
        packets.playToClient(CosmeticCatalogPackets.Chunk.TYPE, CosmeticCatalogPackets.Chunk.CODEC,
            (packet,context)->Minecraft.getInstance().schedule(()->CosmeticCatalogClient.accept(packet)));
        packets.playToServer(CosmeticCatalogPackets.Request.TYPE, CosmeticCatalogPackets.Request.CODEC,
            (packet,context)->{});

        packets.playToClient(HdSkinPackets.Chunk.TYPE, HdSkinPackets.Chunk.CODEC, (packet, context) -> HdSkinClient.receive(packet));
        packets.playToClient(HdSkinPackets.Clear.TYPE, HdSkinPackets.Clear.CODEC, (packet, context) -> HdSkinClient.clear(packet.player()));
        packets.playToClient(HdSkinPackets.CapeChunk.TYPE, HdSkinPackets.CapeChunk.CODEC, (packet, context) -> CapeClient.receive(packet));
        packets.playToClient(HdSkinPackets.CapeClear.TYPE, HdSkinPackets.CapeClear.CODEC, (packet, context) -> CapeClient.clear(packet.player()));
        packets.playToClient(HdSkinPackets.GarmentChunk.TYPE, HdSkinPackets.GarmentChunk.CODEC, (packet, context) -> GarmentClient.receive(packet));
        packets.playToClient(HdSkinPackets.GarmentClear.TYPE, HdSkinPackets.GarmentClear.CODEC, (packet, context) -> GarmentClient.clear(packet.player(), packet.slot()));
    }
}
