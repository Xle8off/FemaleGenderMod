package com.wildfire.gui;

import java.util.LinkedHashMap;

/** Animate only a read transition observed while this conversation is visible. */
public final class MessageReceiptReveal {
    private static final long DURATION=220_000_000L;
    private static final class Entry {
        boolean read;
        long changed;
        Entry(boolean read,long now){this.read=read;changed=read?now-DURATION:now;}
    }
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(128,.75f,true);
    public double update(String key,boolean read,long now){
        Entry entry=entries.get(key);
        if(entry==null){
            entry=new Entry(read,now);entries.put(key,entry);
            while(entries.size()>256)entries.remove(entries.keySet().iterator().next());
        }else if(read&&!entry.read){entry.read=true;entry.changed=now;}
        if(!entry.read)return 0;
        double t=Math.clamp((now-entry.changed)/(double)DURATION,0,1);
        return t*t*(3-2*t);
    }
    public void clear(){entries.clear();}
}
