package com.wildfire.main.networking.appearance;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record MenuPacket(String json) implements CustomPacketPayload {
    public static final Type<MenuPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("appearance_sync", "menu_presence"));
    public static final StreamCodec<ByteBuf, MenuPacket> CODEC = new StreamCodec<>() {
        public MenuPacket decode(ByteBuf input) { return new MenuPacket(new FriendlyByteBuf(input).readUtf(1024)); }
        public void encode(ByteBuf output, MenuPacket value) { new FriendlyByteBuf(output).writeUtf(value.json,1024); }
    };
    public Type<MenuPacket> type() { return TYPE; }
}
