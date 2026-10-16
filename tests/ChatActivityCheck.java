import com.wildfire.gui.ChatActivity;
public class ChatActivityCheck {
    static void check(boolean result,String label){if(!result)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        ChatActivity.clear();
        ChatActivity.accept("Alex","Alex","typing");
        check(ChatActivity.label("alex",false).equals("Набирает сообщение…"),"case-insensitive typing");
        check(ChatActivity.label("Other",false).isEmpty(),"private chat isolation");
        ChatActivity.accept("Alex","Alex","sticker");
        check(ChatActivity.label("Alex",false).equals("Выбирает стикер…"),"sticker status replaces typing");
        ChatActivity.accept("Alex","Alex","emoji");
        check(ChatActivity.label("Alex",false).equals("Выбирает эмодзи…"),"emoji status");
        ChatActivity.accept("Alex","Alex","idle");
        check(ChatActivity.label("Alex",false).isEmpty(),"stop clears status immediately");
        ChatActivity.accept("#group","Alex","typing");ChatActivity.accept("#group","Bob","sticker");
        check(ChatActivity.label("#group",true).contains("Bob")&&ChatActivity.label("#group",true).contains("(+1)"),"multiple group participants");
        ChatActivity.accept("#group","Invalid name","emoji");
        ChatActivity.accept("#group","Bob","unsupported");
        check(ChatActivity.label("#group",true).contains("стикер"),"malformed updates rejected");
        var entries=ChatActivity.class.getDeclaredField("entries");entries.setAccessible(true);
        for(int i=0;i<500;i++)ChatActivity.accept("Chat"+i,"Alex","typing");
        check(((java.util.Map<?,?>)entries.get(null)).size()<=400,"activity memory bounded");
        ChatActivity.clear();check(ChatActivity.label("#group",true).isEmpty(),"disconnect reset");
        System.out.println("Chat activity checks passed: states, stop, isolation, groups, validation, bounds, reset");
    }
}
