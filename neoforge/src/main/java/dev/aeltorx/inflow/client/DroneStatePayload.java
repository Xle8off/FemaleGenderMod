package dev.aeltorx.inflow.client;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record DroneStatePayload(boolean ended) implements CustomPacketPayload {
    public static final Type<DroneStatePayload> TYPE=new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("windowsclient","pilot_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DroneStatePayload> CODEC=CustomPacketPayload.codec(
        (p,b)->b.writeBoolean(p.ended),b->new DroneStatePayload(b.readBoolean()));
    @Override public Type<DroneStatePayload> type(){return TYPE;}
}
