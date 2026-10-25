package com.wildfire.client.overhead;

import java.util.List;
import java.util.Map;

/** Snapshot before filtering: access-order map lookups can reorder the live map. */
final class CameraSubscriptions {
    private CameraSubscriptions(){}
    static <K,V> List<Map.Entry<K,V>> snapshot(Map<K,V> requested){
        return requested.entrySet().stream().map(e->Map.entry(e.getKey(),e.getValue())).toList();
    }
}
