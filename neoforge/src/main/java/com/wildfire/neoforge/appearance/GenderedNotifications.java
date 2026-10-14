package com.wildfire.neoforge.appearance;

import com.wildfire.common.WildfireGender;
import net.minecraft.client.Minecraft;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Only rewrites our exact system presence templates, never player-written messages. */
public final class GenderedNotifications {
    private static final Map<String, Boolean> FEMALE = new ConcurrentHashMap<>();
    private GenderedNotifications() {}
    private static Object connectionKey;
    public static Boolean gender(String name){
        capture();
        return name==null?null:FEMALE.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    public static void capture() {
        var connection = Minecraft.getInstance().getConnection();
        if(connection!=connectionKey){connectionKey=connection;FEMALE.clear();}
        if (connection == null) return;
        for (var info : connection.getListedOnlinePlayers()) {
            var config = WildfireGender.getPlayerById(info.getProfile().id());
            if (config != null) {
                var gender=config.gender().get();
                String key=info.getProfile().name().toLowerCase(java.util.Locale.ROOT);
                if(gender.name().equals("OTHER"))FEMALE.remove(key);
                else FEMALE.put(key,gender.name().equals("FEMALE"));
            }
        }
    }

    public static String format(String category, String message) {
        if("Группа".equals(category)){
            var group=java.util.regex.Pattern.compile("^([A-Za-z0-9_]{1,16}) (добавил вас в групповой чат|вышел из группового чата)(.*)$").matcher(message);
            if(group.matches()){
                Boolean female=gender(group.group(1));
                boolean added=group.group(2).startsWith("добавил");
                String phrase=female==null?(added?"приглашает вас в групповой чат":"больше не состоит в групповом чате"):
                    added?(female?"добавила вас в групповой чат":"добавил вас в групповой чат"):(female?"вышла из группового чата":"вышел из группового чата");
                return group.group(1)+" "+phrase+group.group(3);
            }
        }
        if (!"Друзья".equals(category)) return message;
        var match = java.util.regex.Pattern.compile("^([A-Za-z0-9_]{1,16}) (вошёл в сеть|вышел из сети|принял вашу заявку)$").matcher(message);
        if (!match.matches()) return message;
        capture();
        String name = match.group(1);
        Boolean female = FEMALE.get(name.toLowerCase(java.util.Locale.ROOT));
        if (match.group(2).equals("принял вашу заявку")) {
            if (female == null) return "Ваша заявка принята: " + name;
            return name + (female ? " приняла вашу заявку" : " принял вашу заявку");
        }
        boolean online = match.group(2).equals("вошёл в сеть");
        if (female == null) return name + (online ? " теперь в сети" : " теперь не в сети");
        return name + (online ? (female ? " вошла в сеть" : " вошёл в сеть")
            : (female ? " вышла из сети" : " вышел из сети"));
    }
}
