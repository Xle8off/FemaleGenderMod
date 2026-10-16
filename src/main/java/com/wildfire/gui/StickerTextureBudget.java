package com.wildfire.gui;

import java.util.*;

/** Enough slots for all visible picker tiles; animation memory stays bounded. */
final class StickerTextureBudget {
    private final LinkedHashMap<String,Long> sizes=new LinkedHashMap<>(128,.75f,true);
    private long bytes;
    void touch(String key){sizes.get(key);}
    List<String> put(String key,long size){
        Long previous=sizes.put(key,size);bytes+=size-(previous==null?0:previous);
        var evicted=new ArrayList<String>();
        while(sizes.size()>128||bytes>96L*1024*1024){
            var iterator=sizes.entrySet().iterator();var oldest=iterator.next();
            bytes-=oldest.getValue();evicted.add(oldest.getKey());iterator.remove();
        }
        return evicted;
    }
}
