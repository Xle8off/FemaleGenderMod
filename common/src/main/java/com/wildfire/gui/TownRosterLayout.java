package com.wildfire.gui;

import com.wildfire.client.towns.TownsClient;
import java.util.*;

/** Header and resident rows share one scrollable list; each role is shown once. */
public final class TownRosterLayout {
    public record Row(String role,TownsClient.Resident resident,int members){}
    private TownRosterLayout(){}
    public static List<Row> rows(List<TownsClient.Resident> residents){
        var groups=new LinkedHashMap<String,List<TownsClient.Resident>>();
        residents.stream().sorted(Comparator.comparingInt((TownsClient.Resident r)->rank(r.role()))
            .thenComparing(TownsClient.Resident::role).thenComparing(r->r.name().toLowerCase(Locale.ROOT)))
            .forEach(r->groups.computeIfAbsent(r.role(),key->new ArrayList<>()).add(r));
        var rows=new ArrayList<Row>();
        groups.forEach((role,group)->{rows.add(new Row(role,null,group.size()));group.forEach(r->rows.add(new Row(role,r,group.size())));});
        return List.copyOf(rows);
    }
    private static int rank(String role){return switch(role){
        case "Глава города"->0;case "Заместитель главы"->1;case "Министр"->2;case "Советник"->3;case "Гражданин"->4;default->5;
    };}
}
