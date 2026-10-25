package dev.aeltorx.inflow.client;
import java.util.*;
/** Local-only matching; source is tab-list names and already received search results. */
public final class NicknameMatches {
 private NicknameMatches(){}
 private static final java.util.regex.Pattern NAME=java.util.regex.Pattern.compile("[A-Za-z0-9_]{1,16}"),QUERY=java.util.regex.Pattern.compile("[a-z0-9_]{0,16}");
 public static List<String> find(Collection<String> source,String query,int limit){
  String needle=query.trim().toLowerCase(Locale.ROOT);if(limit<=0||!QUERY.matcher(needle).matches())return List.of();
  var unique=new LinkedHashMap<String,String>();for(String name:source)if(name!=null&&NAME.matcher(name).matches())unique.putIfAbsent(name.toLowerCase(Locale.ROOT),name);
  return unique.values().stream().filter(name->name.toLowerCase(Locale.ROOT).contains(needle)).sorted(Comparator.<String>comparingInt(name->name.toLowerCase(Locale.ROOT).startsWith(needle)?0:1).thenComparing(String.CASE_INSENSITIVE_ORDER)).limit(limit).toList();
 }
}
