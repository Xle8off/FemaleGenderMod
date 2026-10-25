package com.wildfire.gui;
import java.util.*;
/** Transient activity only; never saved in history or unread counts. */
public final class ChatActivity {
    private record Entry(String name,String state,long expires) {}
    private static final Map<String,Entry> entries = new LinkedHashMap<>();
    public static void accept(String chat,String name,String state) {
        entries.entrySet().removeIf(e -> e.getValue().expires < System.currentTimeMillis());
        String key=chat.toLowerCase(Locale.ROOT)+"\u0000"+name.toLowerCase(Locale.ROOT);
        if(state.equals("idle")){entries.remove(key);return;}
        if(!Set.of("typing","sticker","emoji").contains(state)||!name.matches("[A-Za-z0-9_]{1,16}"))return;
        entries.put(key,new Entry(name,state,System.currentTimeMillis()+5000));
        while(entries.size()>400)entries.remove(entries.keySet().iterator().next());
    }
    public static String label(String chat,boolean group) {
        String prefix=chat.toLowerCase(Locale.ROOT)+"\u0000";long now=System.currentTimeMillis();
        var active=entries.entrySet().stream().filter(e -> e.getKey().startsWith(prefix)&&e.getValue().expires>now).map(Map.Entry::getValue).toList();
        if(active.isEmpty())return "";
        Entry entry=active.getLast();String text=switch(entry.state){case "sticker" -> "Выбирает стикер…";case "emoji" -> "Выбирает эмодзи…";default -> "Набирает сообщение…";};
        return group?entry.name+" · "+text+(active.size()>1?" (+"+(active.size()-1)+")":""):text;
    }
    public static void clear(){entries.clear();}
}
