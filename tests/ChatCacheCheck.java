import com.wildfire.gui.PublicChatState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.List;
import java.util.Map;

public final class ChatCacheCheck {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var field=PublicChatState.class.getDeclaredField("history");field.setAccessible(true);
        var history=(List<PublicChatState.Row>)field.get(null);
        var cacheField=PublicChatState.class.getDeclaredField("textCache");cacheField.setAccessible(true);
        var cache=(Map<?,?>)cacheField.get(null);
        PublicChatState.clear();
        var source=Component.literal("Name: hello").withStyle(Style.EMPTY.withColor(0x123456).withInsertion("original"));
        history.add(new PublicChatState.Row(source,"global",0));
        var first=PublicChatState.formatted(source);
        for(int frame=0;frame<1000;frame++){
            if(PublicChatState.formatted(source)!=first)throw new AssertionError("Repeated frame rebuilt text");
        }
        if(!first.getString().equals(source.getString()))throw new AssertionError("Text changed");
        first.visit((style,value)->{
            if(!value.isEmpty()&&(!"original".equals(style.getInsertion())||style.getColor()==null||style.getColor().getValue()!=0x123456))
                throw new AssertionError("Component styles lost");
            return java.util.Optional.empty();
        },Style.EMPTY);
        var prefix=PublicChatState.prefix(source,6);
        if(prefix!=PublicChatState.prefix(source,6)||!prefix.getString().equals("Name: "))throw new AssertionError("Prefix not cached");
        if(!PublicChatState.prefix(source,4).getString().equals("Name"))throw new AssertionError("Prefix length not invalidated");
        for(int i=0;i<1000;i++)PublicChatState.formatted(Component.literal("temporary "+i));
        if(cache.size()!=1)throw new AssertionError("Temporary components retained");
        var epoch=PublicChatState.class.getDeclaredField("resourceEpoch");epoch.setAccessible(true);
        epoch.setLong(null,epoch.getLong(null)+1);
        if(PublicChatState.formatted(source)==first)throw new AssertionError("Resource reload retained old formatting");
        PublicChatState.clear();
        if(!cache.isEmpty()||!history.isEmpty())throw new AssertionError("Clear retained history/cache");
        if(PublicChatState.formatted(source)==first)throw new AssertionError("Clear did not invalidate text");
        System.out.println("Chat cache: reuse, style preservation, prefix changes, retention and clear passed");
    }
}
