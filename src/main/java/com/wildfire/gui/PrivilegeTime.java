package com.wildfire.gui;
/** Absolute server expiry, counted down locally without polling every second. */
public final class PrivilegeTime {
    private PrivilegeTime(){}
    public static String label(long expires,long now){
        if(expires<0)return "";
        if(expires==0)return "Бессрочно";
        if(expires<=now)return "Срок истёк";
        long remaining=expires-now,minutes=Math.max(1,remaining/60000+(remaining%60000==0?0:1)),hours=minutes/60,days=hours/24;
        return days>0?days+"д "+hours%24+"ч":hours>0?hours+"ч "+minutes%60+"м":minutes+"м";
    }
}
