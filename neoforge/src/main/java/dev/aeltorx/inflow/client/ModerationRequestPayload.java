package dev.aeltorx.inflow.client;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record ModerationRequestPayload(String json) implements CustomPacketPayload {
    public static final Type<ModerationRequestPayload> TYPE=new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("windowsclient","moderation"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ModerationRequestPayload> CODEC=CustomPacketPayload.codec((p,b)->b.writeUtf(p.json,30000),b->new ModerationRequestPayload(b.readUtf(30000)));
    @Override public Type<ModerationRequestPayload> type(){return TYPE;}
}
