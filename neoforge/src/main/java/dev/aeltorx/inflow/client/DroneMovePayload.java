package dev.aeltorx.inflow.client;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Binary plugin message, never a logged player command. */
public record DroneMovePayload(double x,double y,double z,float yaw,float pitch) implements CustomPacketPayload {
    public static final Type<DroneMovePayload> TYPE=new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("windowsclient","pilot"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DroneMovePayload> CODEC=CustomPacketPayload.codec(
        (p,b)->{b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeFloat(p.yaw);b.writeFloat(p.pitch);},
        b->new DroneMovePayload(b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat()));
    @Override public Type<DroneMovePayload> type(){return TYPE;}
}
