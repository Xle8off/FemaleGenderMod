import com.wildfire.client.moderation.InFlowHostLayout;
import java.nio.file.*;

public final class InFlowHostCheck {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        for (int width : new int[]{64, 127, 128, 220, 320, 640, 1920}) {
            for (int height : new int[]{32, 63, 64, 240, 1080}) {
                var p = InFlowHostLayout.overlay(width, height);
                if (p.visible()) {
                    check(p.panelX() >= 0 && p.panelX() + 20 <= p.wheelX(), "Buttons overlap");
                    check(p.wheelX() + 20 <= width && p.y() + 20 <= height, "Button outside screen");
                    check(p.y() + 20 < height - 20, "Button covers chat composer");
                }
                check(!InFlowHostLayout.visible(false, true, p.visible()), "Unauthorized shortcut visible");
                check(!InFlowHostLayout.visible(true, false, p.visible()), "Stale screen shortcut visible");
            }
        }
        Path root = Path.of(args[0]);
        String integration = Files.readString(root.resolve("src/main/java/com/wildfire/client/moderation/InFlowIntegration.java"));
        check(integration.contains("InFlowApi.openPanel(parent)") && integration.contains("InFlowApi.openWheel()"), "Host must call guarded API");
        check(!integration.contains("PayloadTypeRegistry") && !integration.contains("ClientTickEvents") && !integration.contains("KeyMappingHelper"), "Host duplicated InFlow bootstrap");
        check(integration.contains("extends ChatOverlayButton"), "Shortcuts must not steal keyboard focus from chat");
        check(integration.contains("if (!(screen instanceof NotificationCenterScreen)) return;"),"No floating moderation shortcuts in chat or pause menu");
        check(integration.contains("if (!(screen instanceof NotificationCenterScreen)) Screens.getWidgets(screen).add(wheel)"),
            "Drawer must not register wheel over the close button");
        check(integration.contains("NotificationCenterScreen?18:InFlowHostLayout.SIZE"),
            "Drawer shield must fit its 18px sidebar cell");
        check(integration.contains("(getWidth()-12)/2"),"Glyph must remain centered in compact button");
        String init = Files.readString(root.resolve("src/main/java/com/wildfire/main/WildfireGenderClient.java"));
        check(init.contains("InFlowIntegration.register()"), "Host binding not initialized");
        System.out.println("PASS: InFlow host layout, permission visibility, guarded routing and single bootstrap ownership");
    }
}
