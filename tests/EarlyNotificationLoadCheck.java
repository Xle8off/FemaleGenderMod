/** Run without registry bootstrap, matching the early Screen.Init event. */
public final class EarlyNotificationLoadCheck {
    public static void main(String[] args) throws Exception {
        Class.forName("com.wildfire.neoforge.client.NotificationChatButton");
        System.out.println("NotificationChatButton loads before item registry bootstrap");
    }
}
