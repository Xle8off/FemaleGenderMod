package com.wildfire.gui;

import java.time.*;
import java.time.format.DateTimeFormatter;

public final class LastSeenLabel {
    private LastSeenLabel(){}
    public static String format(long time,long now,ZoneId zone){
        return format(time,now,zone,(Boolean)null);
    }
    private static java.util.function.Function<String,Boolean> genders=name->null;
    public static void setGenderResolver(java.util.function.Function<String,Boolean> resolver){genders=resolver;}
    public static String format(long time,long now,ZoneId zone,String nickname){
        return format(time,now,zone,genders.apply(nickname));
    }
    public static String format(long time,long now,ZoneId zone,Boolean female){
        if(time<=0)return "не в сети";
        var seen=Instant.ofEpochMilli(time).atZone(zone);
        var today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String date=seen.toLocalDate().equals(today)?"сегодня":seen.toLocalDate().equals(today.minusDays(1))?"вчера":seen.format(DateTimeFormatter.ofPattern(seen.getYear()==today.getYear()?"dd.MM":"dd.MM.yyyy"));
        return (female==null?"Последний онлайн: ":female?"Была в сети: ":"Был в сети: ")+date+" в "+seen.format(DateTimeFormatter.ofPattern("H:mm"));
    }
}
