package com.wildfire.neoforge.appearance;

import com.wildfire.client.appearance.AppearanceState;
import com.wildfire.client.appearance.BreastShape;
import com.wildfire.common.WildfireGender;
import java.util.UUID;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** NeoForge-side adapter for the SpringPlus appearance UI. */
public final class ServerAppearanceSync {
    public record Cosmetics(int shirt, int pants, int headwear, int pet) {
        public static final Cosmetics NONE = new Cosmetics(0, 0, 0, 0);
        public Cosmetics(AppearanceState.Cosmetics data) {
            this(data.shirt(), data.pants(), data.headwear(), data.pet());
        }
        public AppearanceState.Cosmetics shared() {
            return new AppearanceState.Cosmetics(shirt, pants, headwear, pet);
        }
    }

    private ServerAppearanceSync() {}
    public static boolean isActive() { return AppearanceState.isActive(); }
    public static BreastShape shape(UUID uuid) { return AppearanceState.shape(uuid); }
    public static Cosmetics cosmetics(UUID uuid) { return new Cosmetics(AppearanceState.cosmetics(uuid)); }
    public static void preview(UUID uuid, BreastShape shape) { AppearanceState.preview(uuid, shape); }
    public static void preview(UUID uuid, Cosmetics cosmetics) { AppearanceState.preview(uuid, cosmetics.shared()); }

    public static boolean submit(UUID token, float breastSize, BreastShape shape, Cosmetics original, Cosmetics cosmetics) {
        if (!isActive() || !Float.isFinite(breastSize) || breastSize < 0f || breastSize > 0.8f) return false;
        OptionalPayloadSender.sendToServer(new AppearancePackets.CustomizationResult(token, breastSize, shape.id(),
            cosmetics.shirt(), cosmetics.pants(), cosmetics.headwear(), cosmetics.pet()));
        return true;
    }
}
