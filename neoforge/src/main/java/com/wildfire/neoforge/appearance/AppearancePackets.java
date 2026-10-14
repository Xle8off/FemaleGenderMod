package com.wildfire.neoforge.appearance;


import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.VarInt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AppearancePackets {
    public static final int VERSION = 12;

    private AppearancePackets() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("appearance_sync", path));
    }

    public record Notification(String category, String title, String message) implements CustomPacketPayload {
        public static final Type<Notification> TYPE = AppearancePackets.type("notification");
        public static final StreamCodec<ByteBuf, Notification> CODEC = new StreamCodec<>() {
            public Notification decode(ByteBuf input) {
                FriendlyByteBuf buffer = new FriendlyByteBuf(input);
                return new Notification(buffer.readUtf(32), buffer.readUtf(80), buffer.readUtf(512));
            }
            public void encode(ByteBuf output, Notification value) {
                FriendlyByteBuf buffer = new FriendlyByteBuf(output);
                buffer.writeUtf(value.category, 32);
                buffer.writeUtf(value.title, 80);
                buffer.writeUtf(value.message, 512);
            }
        };
        public Type<Notification> type() { return TYPE; }
    }

    public record NotificationPreference(byte popupOnly) implements CustomPacketPayload {
        public static final Type<NotificationPreference> TYPE = AppearancePackets.type("notification_pref");
        public static final StreamCodec<ByteBuf, NotificationPreference> CODEC = new StreamCodec<>() {
            public NotificationPreference decode(ByteBuf input) { return new NotificationPreference(input.readByte()); }
            public void encode(ByteBuf output, NotificationPreference value) { output.writeByte(value.popupOnly); }
        };
        public Type<NotificationPreference> type() { return TYPE; }
    }

    public record NotificationProbe(byte value) implements CustomPacketPayload {
        public static final Type<NotificationProbe> TYPE = AppearancePackets.type("notification_probe");
        public static final StreamCodec<ByteBuf, NotificationProbe> CODEC = new StreamCodec<>() {
            public NotificationProbe decode(ByteBuf input) { return new NotificationProbe(input.readByte()); }
            public void encode(ByteBuf output, NotificationProbe value) { output.writeByte(value.value); }
        };
        public Type<NotificationProbe> type() { return TYPE; }
    }
    public record NotificationAck(byte value) implements CustomPacketPayload {
        public static final Type<NotificationAck> TYPE = AppearancePackets.type("notification_ack");
        public static final StreamCodec<ByteBuf, NotificationAck> CODEC = new StreamCodec<>() {
            public NotificationAck decode(ByteBuf input) { return new NotificationAck(input.readByte()); }
            public void encode(ByteBuf output, NotificationAck value) { output.writeByte(value.value); }
        };
        public Type<NotificationAck> type() { return TYPE; }
    }

    public record Hello(int version) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = AppearancePackets.type("hello");
        public static final StreamCodec<ByteBuf, Hello> CODEC = new StreamCodec<>() {
            public Hello decode(ByteBuf input) { return new Hello(VarInt.read(input)); }
            public void encode(ByteBuf output, Hello value) { VarInt.write(output, value.version); }
        };
        public Type<Hello> type() { return TYPE; }
    }

    public record HelloAck(int version) implements CustomPacketPayload {
        public static final Type<HelloAck> TYPE = AppearancePackets.type("hello_ack");
        public static final StreamCodec<ByteBuf, HelloAck> CODEC = new StreamCodec<>() {
            public HelloAck decode(ByteBuf input) { return new HelloAck(VarInt.read(input)); }
            public void encode(ByteBuf output, HelloAck value) { VarInt.write(output, value.version); }
        };
        public Type<HelloAck> type() { return TYPE; }
    }

    public record Disable(byte value) implements CustomPacketPayload {
        public static final Type<Disable> TYPE = AppearancePackets.type("disable");
        public static final StreamCodec<ByteBuf, Disable> CODEC = new StreamCodec<>() {
            public Disable decode(ByteBuf input) { return new Disable(input.readByte()); }
            public void encode(ByteBuf output, Disable value) { output.writeByte(value.value); }
        };
        public Type<Disable> type() { return TYPE; }
    }

    public record SkinReady(byte value) implements CustomPacketPayload {
        public static final Type<SkinReady> TYPE = AppearancePackets.type("skin_ready");
        public static final StreamCodec<ByteBuf, SkinReady> CODEC = new StreamCodec<>() {
            public SkinReady decode(ByteBuf input) { return new SkinReady(input.readByte()); }
            public void encode(ByteBuf output, SkinReady value) { output.writeByte(value.value); }
        };
        public Type<SkinReady> type() { return TYPE; }
    }

    public record Relationship(UUID owner, List<UUID> spouses) implements CustomPacketPayload {
        public static final Type<Relationship> TYPE = AppearancePackets.type("relationship");
        public static final StreamCodec<ByteBuf, Relationship> CODEC = new StreamCodec<>() {
            public Relationship decode(ByteBuf input) {
                UUID owner = UUIDUtil.STREAM_CODEC.decode(input);
                int count = VarInt.read(input);
                if (count < 0 || count > 32) throw new IllegalArgumentException("Invalid spouse count: " + count);
                List<UUID> spouses = new ArrayList<>(count);
                for (int i = 0; i < count; i++) spouses.add(UUIDUtil.STREAM_CODEC.decode(input));
                return new Relationship(owner, List.copyOf(spouses));
            }
            public void encode(ByteBuf output, Relationship value) {
                UUIDUtil.STREAM_CODEC.encode(output, value.owner);
                VarInt.write(output, value.spouses.size());
                value.spouses.forEach(uuid -> UUIDUtil.STREAM_CODEC.encode(output, uuid));
            }
        };
        public Type<Relationship> type() { return TYPE; }
    }

    public record OpenCustomization(UUID token, boolean female, boolean figureEditable, float breastSize, int shape, int shirt, int pants,
                                    int headwear, int pet) implements CustomPacketPayload {
        public static final Type<OpenCustomization> TYPE = AppearancePackets.type("open_customization");
        public static final StreamCodec<ByteBuf, OpenCustomization> CODEC = new StreamCodec<>() {
            public OpenCustomization decode(ByteBuf input) {
                UUID token = UUIDUtil.STREAM_CODEC.decode(input);
                boolean female = input.readBoolean();
                boolean figureEditable = input.readBoolean();
                AppearanceData data = readAppearance(input);
                return new OpenCustomization(token, female, figureEditable, data.size, data.shape, data.shirt, data.pants,
                    data.headwear, data.pet);
            }
            public void encode(ByteBuf output, OpenCustomization value) {
                UUIDUtil.STREAM_CODEC.encode(output, value.token);
                output.writeBoolean(value.female);
                output.writeBoolean(value.figureEditable);
                writeAppearance(output, value.breastSize, value.shape, value.shirt, value.pants,
                    value.headwear, value.pet);
            }
        };
        public Type<OpenCustomization> type() { return TYPE; }
    }

    public record Entry(UUID uuid, boolean female, float breastSize, int shape, int shirt, int pants,
                        int headwear, int pet) {}

    public record Snapshot(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = AppearancePackets.type("snapshot");
        public static final StreamCodec<ByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            public Snapshot decode(ByteBuf input) {
                int count = VarInt.read(input);
                if (count < 0 || count > 4096) throw new IllegalArgumentException("Invalid appearance count: " + count);
                List<Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    UUID uuid = UUIDUtil.STREAM_CODEC.decode(input);
                    boolean female = input.readBoolean();
                    AppearanceData data = readAppearance(input);
                    entries.add(new Entry(uuid, female, data.size, data.shape, data.shirt, data.pants,
                        data.headwear, data.pet));
                }
                return new Snapshot(List.copyOf(entries));
            }
            public void encode(ByteBuf output, Snapshot value) {
                VarInt.write(output, value.entries.size());
                for (Entry entry : value.entries) {
                    UUIDUtil.STREAM_CODEC.encode(output, entry.uuid);
                    output.writeBoolean(entry.female);
                    writeAppearance(output, entry.breastSize, entry.shape, entry.shirt, entry.pants,
                        entry.headwear, entry.pet);
                }
            }
        };
        public Type<Snapshot> type() { return TYPE; }
    }

    public record Update(UUID uuid, boolean visible, boolean female, float breastSize, int shape, int shirt, int pants,
                         int headwear, int pet) implements CustomPacketPayload {
        public static final Type<Update> TYPE = AppearancePackets.type("update");
        public static final StreamCodec<ByteBuf, Update> CODEC = new StreamCodec<>() {
            public Update decode(ByteBuf input) {
                UUID uuid = UUIDUtil.STREAM_CODEC.decode(input);
                if (!input.readBoolean()) return new Update(uuid, false, false, 0f, 0, 0, 0, 0, 0);
                boolean female = input.readBoolean();
                float size = input.readFloat();
                int shape = VarInt.read(input), shirt = VarInt.read(input), pants = VarInt.read(input);
                int headwear = VarInt.read(input), pet = VarInt.read(input);
                return new Update(uuid, true, female, size, shape, shirt, pants, headwear, pet);
            }
            public void encode(ByteBuf output, Update value) {
                UUIDUtil.STREAM_CODEC.encode(output, value.uuid);
                output.writeBoolean(value.visible);
                if (value.visible) {
                    output.writeBoolean(value.female);
                    output.writeFloat(value.breastSize);
                    VarInt.write(output, value.shape);
                    VarInt.write(output, value.shirt);
                    VarInt.write(output, value.pants);
                    VarInt.write(output, value.headwear);
                    VarInt.write(output, value.pet);
                }
            }
        };
        public Type<Update> type() { return TYPE; }
    }

    public record CustomizationResult(UUID token, float breastSize, int shape, int shirt, int pants,
                                      int headwear, int pet) implements CustomPacketPayload {
        public static final Type<CustomizationResult> TYPE = AppearancePackets.type("customization_result");
        public static final StreamCodec<ByteBuf, CustomizationResult> CODEC = new StreamCodec<>() {
            public CustomizationResult decode(ByteBuf input) {
                UUID token = UUIDUtil.STREAM_CODEC.decode(input);
                float size = input.readFloat();
                int shape = VarInt.read(input), shirt = VarInt.read(input), pants = VarInt.read(input);
                int headwear = VarInt.read(input), pet = VarInt.read(input);
                return new CustomizationResult(token, size, shape, shirt, pants, headwear, pet);
            }
            public void encode(ByteBuf output, CustomizationResult value) {
                UUIDUtil.STREAM_CODEC.encode(output, value.token);
                output.writeFloat(value.breastSize);
                VarInt.write(output, value.shape);
                VarInt.write(output, value.shirt);
                VarInt.write(output, value.pants);
                VarInt.write(output, value.headwear);
                VarInt.write(output, value.pet);
            }
        };
        public Type<CustomizationResult> type() { return TYPE; }
    }

    private record AppearanceData(float size, int shape, int shirt, int pants, int headwear, int pet) {}

    private static AppearanceData readAppearance(ByteBuf input) {
        float size = input.readFloat();
        int shape = VarInt.read(input), shirt = VarInt.read(input), pants = VarInt.read(input);
        int headwear = VarInt.read(input), pet = VarInt.read(input);
        return new AppearanceData(size, shape, shirt, pants, headwear, pet);
    }

    private static void writeAppearance(ByteBuf output, float size, int shape, int shirt, int pants,
                                        int headwear, int pet) {
        output.writeFloat(size);
        VarInt.write(output, shape);
        VarInt.write(output, shirt);
        VarInt.write(output, pants);
        VarInt.write(output, headwear);
        VarInt.write(output, pet);
    }
}
