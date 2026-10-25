package dev.aeltorx.inflow.client;
/** Display-only names: protocol values and server commands remain unchanged. */
public final class ModerationLabels {
 private ModerationLabels(){}
 private static final java.util.regex.Pattern TOKENS=java.util.regex.Pattern.compile("\\b(ACCOUNT_TEMPBAN|ACCOUNT_BAN|IP_TEMPBAN|IP_BAN|WARN|TEMP_MUTE|MUTE|ACTIVE|EXPIRED|REVOKED|LIFTED)\\b");
 public static String readable(String value){
  if(value==null)return "—";
  return TOKENS.matcher(value).replaceAll(m->switch(m.group()){
   case "ACCOUNT_TEMPBAN"->"Временный бан";case "ACCOUNT_BAN"->"Постоянный бан";
   case "IP_TEMPBAN"->"Временный бан по IP";case "IP_BAN"->"Постоянный бан по IP";
   case "WARN"->"Предупреждение";case "TEMP_MUTE"->"Временный мут";case "MUTE"->"Мут";
   case "ACTIVE"->"Действует";case "EXPIRED"->"Срок истёк";case "REVOKED","LIFTED"->"Снято";default->m.group();});
 }
}
