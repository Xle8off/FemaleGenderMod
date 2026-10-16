import java.lang.reflect.Method;
public final class PrivateStatusCheck {
    static Method accept, presence, color;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void update(boolean online,boolean afk,boolean dnd)throws Exception{
        accept.invoke(null,"{\"partner\":\"Alice\",\"presenceOnly\":true,\"presenceVerified\":true,\"online\":"+online+",\"afk\":"+afk+",\"dnd\":"+dnd+"}");
    }
    public static void main(String[] args)throws Exception{
        var zone=java.time.ZoneId.of("Europe/Kyiv");
        long now=java.time.ZonedDateTime.of(2026,10,11,2,30,0,0,zone).toInstant().toEpochMilli();
        long yesterday=java.time.ZonedDateTime.of(2026,10,10,0,7,0,0,zone).toInstant().toEpochMilli();
        check(com.wildfire.gui.LastSeenLabel.format(yesterday,now,zone).equals("Последний онлайн: вчера в 0:07"),"yesterday across midnight");
        check(com.wildfire.gui.LastSeenLabel.format(now,now,zone).equals("Последний онлайн: сегодня в 2:30"),"today");
        check(com.wildfire.gui.LastSeenLabel.format(now,now,zone,Boolean.TRUE).equals("Была в сети: сегодня в 2:30"),"female");
        check(com.wildfire.gui.LastSeenLabel.format(now,now,zone,Boolean.FALSE).equals("Был в сети: сегодня в 2:30"),"male");
        check(com.wildfire.gui.LastSeenLabel.format(yesterday-86400000,now,zone).equals("Последний онлайн: 09.10 в 0:07"),"date separator");
        var type=Class.forName(args[0]);accept=type.getMethod("accept",String.class);
        var isRead=type.getMethod("isRead",String.class,long.class);
        check(isRead.invoke(null,"Alice",100L).equals(false),"no fabricated read receipts");
        accept.invoke(null,"{\"partner\":\"Alice\",\"receiptOnly\":true,\"readThrough\":200}");
        check(isRead.invoke(null,"Alice",100L).equals(true)&&isRead.invoke(null,"Alice",201L).equals(false),"read-through bounded by confirmed timestamp");
        accept.invoke(null,"{\"partner\":\"Alice\",\"receiptOnly\":true,\"readThrough\":100}");
        check(isRead.invoke(null,"Alice",200L).equals(true),"late receipt cannot regress read state");
        presence=type.getMethod("presence",String.class);color=type.getMethod("statusColor",String.class);
        accept.invoke(null,"{\"partner\":\"\",\"contacts\":[{\"name\":\"Alice\",\"online\":true,\"presenceVerified\":true,\"afk\":true}]}");
        check(presence.invoke(null,"Alice").equals("AFK"),"AFK text");
        check(color.invoke(null,"Alice").equals(0xFFFFC34D),"Yellow AFK dot");
        update(true,true,true);
        check(presence.invoke(null,"Alice").equals("Не беспокоить"),"DND takes priority over AFK");
        check(color.invoke(null,"Alice").equals(0xFFFF5965),"Red DND dot");
        update(true,false,false);check(color.invoke(null,"Alice").equals(0xFF36CF79),"Normal online green");
        update(false,true,true);check(color.invoke(null,"Alice").equals(0xFF929AA6),"Offline ignores stale AFK/DND");
        accept.invoke(null,"{\"partner\":\"Alice\",\"presenceOnly\":true,\"online\":true,\"presenceVerified\":true}");
        check(color.invoke(null,"Alice").equals(0xFF36CF79),"Legacy snapshot defaults to ordinary online");
        System.out.println("Private status client checks passed: AFK yellow, DND red/priority, online green, offline grey, legacy compatibility.");
    }
}
