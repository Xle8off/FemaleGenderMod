package dev.aeltorx.inflow.common;

/** Strict common command-safe duration parser: seconds, minutes, hours, days, up to one year. */
public final class PunishmentDuration {
    private PunishmentDuration() { }
    public static boolean valid(String value,boolean permanent) {
        if(permanent&&"permanent".equals(value))return true;
        if(value==null||!value.matches("[1-9][0-9]{0,7}[smhd]"))return false;
        long n=Long.parseLong(value.substring(0,value.length()-1));
        long factor=switch(value.charAt(value.length()-1)){case 's'->1;case 'm'->60;case 'h'->3600;default->86400;};
        return n*factor<=365L*86400;
    }
}
