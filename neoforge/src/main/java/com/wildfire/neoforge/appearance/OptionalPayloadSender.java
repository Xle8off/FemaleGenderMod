package com.wildfire.neoforge.appearance;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.function.BooleanSupplier;

/** Optional plugin channels must never be sent before negotiation or after removal. */
public final class OptionalPayloadSender {
    private OptionalPayloadSender() {}
    public static boolean sendToServer(CustomPacketPayload payload) {
        var connection=Minecraft.getInstance().getConnection();
        if(connection==null)return false;
        return attempt(()->Minecraft.getInstance().getConnection()==connection&&connection.hasChannel(payload.type()),
            ()->connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(payload)));
    }
    static boolean attempt(BooleanSupplier supported,Runnable send) {
        if(!supported.getAsBoolean())return false;
        try{send.run();return true;}
        catch(UnsupportedOperationException unavailable){
            // NeoForge can reject a channel removed between capability check and send.
            // Do not cache rejection: a backend switch may announce it again later.
            return false;
        }
    }
}
