import java.lang.reflect.*;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.*;

public final class ServerSkinPriorityCheck {
    public static void main(String[] args) throws Exception {
        Class<?> client = Class.forName(args[0]);
        Class<?> entry = Class.forName(args[0] + "$Skin");
        var constructor = entry.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var field = client.getDeclaredField("SKINS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID, Object> skins = (Map<UUID, Object>) field.get(null);
        var method = client.getMethod("skin", UUID.class, PlayerSkin.class);
        UUID player = UUID.randomUUID();
        Identifier server = Identifier.fromNamespaceAndPath("appearance_sync", "test");
        var foreign = new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("foreign", "skin"), Identifier.fromNamespaceAndPath("foreign", "skin"));
        PlayerSkin original = new PlayerSkin(foreign, foreign, foreign, PlayerModelType.WIDE, false);
        if (method.invoke(null, player, original) != original) throw new AssertionError("Unmanaged player changed");
        try {
            skins.put(player, constructor.newInstance(1, 1, true, server, null));
            PlayerSkin result = (PlayerSkin) method.invoke(null, player, original);
            if (!result.body().texturePath().equals(server) || result.model() != PlayerModelType.SLIM)
                throw new AssertionError("Server skin/model did not win");
            if (result.cape() != null || result.elytra() != null) throw new AssertionError("Foreign cape leaked");
            PlayerSkin repeated = (PlayerSkin) method.invoke(null, player, result);
            if (!repeated.equals(result)) throw new AssertionError("Repeated hooks not idempotent");
        } finally { skins.remove(player); }
        if (method.invoke(null, player, original) != original) throw new AssertionError("Provider not restored after clear");
        System.out.println("Server skin priority: unmanaged passthrough, authoritative body/model, foreign cape removal, repeated hooks and clear passed");
    }
}
