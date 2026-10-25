package dev.aeltorx.inflow.client;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record ModerationReplyPayload(String json) implements CustomPacketPayload {
    public static final Type<ModerationReplyPayload> TYPE=new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("windowsclient","moderation_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ModerationReplyPayload> CODEC=CustomPacketPayload.codec((p,b)->b.writeUtf(p.json,30000),b->new ModerationReplyPayload(b.readUtf(30000)));
    @Override public Type<ModerationReplyPayload> type(){return TYPE;}
}
