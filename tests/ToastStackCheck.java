import java.lang.reflect.*;
import java.util.List;

/** Stack bounds, independent actions, progress coalescing and expiration, without a game window. */
public final class ToastStackCheck {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName(args[0]);
        Class<?> actionType = Class.forName(args[0] + "$PopupAction");
        Method push = type.getDeclaredMethod("pushToast", String.class, String.class, float.class, actionType, long.class);
        push.setAccessible(true);
        Method snapshot = type.getDeclaredMethod("visibleToasts"); snapshot.setAccessible(true);
        Class<?> releaseType = Class.forName(args[0] + "$Release");
        Method stage = type.getDeclaredMethod("stage", releaseType);
        check(!Modifier.isSynchronized(stage.getModifiers()), "download must not hold HUD class monitor");
        Field stagingField = type.getDeclaredField("stagingLock"); stagingField.setAccessible(true);
        Object stagingLock = stagingField.get(null);
        check(stagingLock != type, "separate download serialization lock");
        var locked = new java.util.concurrent.CountDownLatch(1);
        var releaseLock = new java.util.concurrent.CountDownLatch(1);
        Thread downloader = Thread.ofPlatform().daemon().start(() -> {
            synchronized (stagingLock) {
                locked.countDown();
                try { releaseLock.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
        });
        var renderWorker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r); thread.setDaemon(true); return thread;
        });
        try {
            check(locked.await(2, java.util.concurrent.TimeUnit.SECONDS), "simulated download started");
            renderWorker.submit(() -> {
                snapshot.invoke(null);
                push.invoke(null, "download progress", "", .1f, null, 45L);
                type.getMethod("dismissPopup", long.class).invoke(null, -1L);
                return null;
            }).get(2, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            releaseLock.countDown(); renderWorker.shutdownNow(); downloader.join(2000);
        }
        Field stack = type.getDeclaredField("toasts"); stack.setAccessible(true);
        List<?> entries = (List<?>) stack.get(null); entries.clear();
        Object action = actionType.getConstructor(String.class, String.class).newInstance("message", "Player1");
        push.invoke(null, "first", "Player1", -1f, action, 12L);
        push.invoke(null, "second", "Player2", -1f, null, 7L);
        push.invoke(null, "third", "Player3", -1f, null, 7L);
        check(((List<?>)snapshot.invoke(null)).size() == 3, "three visible cards");
        check(type.getMethod("popupAction", int.class).invoke(null, 0).equals(action), "action belongs to first card");
        check(type.getMethod("popupAction", int.class).invoke(null, 1) == null, "no action leaks to second card");
        long staleId = (long) type.getMethod("popupId", int.class).invoke(null, 0);
        push.invoke(null, "fourth", "Player4", -1f, null, 7L);
        check(entries.size() == 3, "fourth card evicts oldest");
        check((long)type.getMethod("popupId", int.class).invoke(null, 0) != staleId, "stable distinct identity");
        type.getMethod("dismissPopup", long.class).invoke(null, staleId);
        check(entries.size() == 3, "stale click cannot dismiss replacement");
        long id = (long)type.getMethod("popupId", int.class).invoke(null, 1);
        type.getMethod("dismissPopup", long.class).invoke(null, id);
        check(entries.size() == 2, "dismiss only selected card");
        entries.clear();
        push.invoke(null, "download 10%", "", .1f, null, 45L);
        push.invoke(null, "download 20%", "", .2f, null, 45L);
        check(entries.size() == 1, "download progress updates same card");
        Object toast = entries.getFirst();
        Constructor<?> constructor = toast.getClass().getDeclaredConstructors()[0]; constructor.setAccessible(true);
        ((List<Object>)(List<?>)entries).set(0, constructor.newInstance(99L, "expired", "", -1f, null, 0L, 0L));
        check(((List<?>)snapshot.invoke(null)).isEmpty(), "expired cards removed");
        entries.clear();
        type.getMethod("notificationFeedback",String.class,String.class,String.class).invoke(null,"Друзья","Друзья","Заявка от Player1");
        Object friendAction=type.getMethod("popupAction",int.class).invoke(null,0);
        check(actionType.getMethod("kind").invoke(friendAction).equals("friend_message"),"friend notification only opens private messages");
        check(actionType.getMethod("target").invoke(friendAction).equals("Player1"),"friend message recipient preserved");
        Class<?> preferences=Class.forName("com.wildfire.client.config.CosmeticPreferences");
        Field popups=preferences.getDeclaredField("notificationPopups");popups.setAccessible(true);
        boolean previous=popups.getBoolean(null);
        try{
            popups.setBoolean(null,false);
            check(((List<?>)snapshot.invoke(null)).isEmpty(),"disabled popups remove existing notifications");
            push.invoke(null,"hidden","Player1",-1f,action,12L);
            check(((List<?>)snapshot.invoke(null)).isEmpty(),"disabled notifications do not reappear");
            push.invoke(null,"download progress","",.5f,null,45L);
            check(((List<?>)snapshot.invoke(null)).size()==1,"update feedback remains available");
        }finally{popups.setBoolean(null,previous);entries.clear();}
        entries.clear();
        type.getMethod("notificationFeedback",String.class,String.class,String.class).invoke(null,"Друзья","Друзья","Player2 теперь в сети");
        friendAction=type.getMethod("popupAction",int.class).invoke(null,0);
        check(actionType.getMethod("kind").invoke(friendAction).equals("friend_message"),"friend presence notification also opens private messages");
        System.out.println("Toast stack checks passed: max 3, eviction, actions, stable IDs, progress updates, expiration, friend messages");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
