package com.wildfire.gui;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Only server-supplied metadata identifies channels. Never guesses from prefixes. */
public final class PublicChatState {
    public record Row(Component text,String channel,long receivedAt,long animatedAt){
        public Row(Component text,String channel,long receivedAt){this(text,channel,receivedAt,System.nanoTime());}
    }
    private static final List<Row> history=new ArrayList<>();
    public static final java.util.regex.Pattern IMAGE_PATTERN=java.util.regex.Pattern.compile("https://skin\\.springplus\\.net/api/chat-images/([a-f0-9]{64})\\.png");
    // Only history-owned components are retained: at most 200 entries, cleared on disconnect.
    private static final Map<Component,CachedText> textCache=new IdentityHashMap<>();
    private static net.minecraft.server.packs.resources.ResourceManager reloadManager;
    private static volatile long resourceEpoch;
    private static long cacheEpoch;
    private static final class CachedText {
        Component formatted;
        net.minecraft.client.gui.Font font;
        Object splitter;
        int width=-1;
        List<net.minecraft.util.FormattedCharSequence> lines;
        Integer mediaHeight;
        int prefixLength=-1;
        Component prefix;
    }
    private static CachedText cached(Component source){
        if(cacheEpoch!=resourceEpoch){textCache.clear();cacheEpoch=resourceEpoch;}
        var entry=textCache.get(source);
        if(entry==null){
            // Non-history callers must not grow the cache (e.g. temporary prefixes).
            entry=new CachedText();
            if(history.stream().anyMatch(row->row.text()==source))textCache.put(source,entry);
        }
        return entry;
    }
    public static List<net.minecraft.util.FormattedCharSequence> lines(Row row,net.minecraft.client.gui.Font font,int width){
        var entry=cached(row.text());
        width=Math.max(1,width);
        if(entry.lines==null||entry.font!=font||entry.splitter!=font.getSplitter()||entry.width!=width){
            entry.font=font;entry.splitter=font.getSplitter();entry.width=width;
            entry.lines=List.copyOf(font.split(formatted(row.text()),width));
        }
        return entry.lines;
    }
    private static Object connection;
    public static boolean staff;
    public static boolean spy;
    private static ChatAllFilters filters;
    public static ChatAllFilters filters(){if(filters==null)filters=new ChatAllFilters(Minecraft.getInstance().gameDirectory.toPath().resolve("config/springplus-chat-filters.txt"));return filters;}
    public static String sending="global";
    /** Read filter survives screen recreation; sending remains server-controlled. */
    public static String selectedTab="all";
    private PublicChatState(){}
    public static void acceptControl(String value){
        checkConnection();
        var state=ChatChannelMetadata.capability(value);
        if(state!=null){sending=state.sending();staff=state.staff();spy=state.spy();}
    }
    public static void checkConnection(){
        Object next=Minecraft.getInstance().getConnection();
        if(next!=connection){connection=next;clear();staff=false;spy=false;sending="global";selectedTab="all";}
        var resources=Minecraft.getInstance().getResourceManager();
        if(reloadManager!=resources&&resources instanceof net.minecraft.server.packs.resources.ReloadableResourceManager reloadable){
            reloadManager=resources;
            // The listener only signals invalidation; the render thread owns the cache.
            reloadable.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->resourceEpoch++);
            resourceEpoch++;
        }
    }
    public static boolean accept(Component text){
        checkConnection();String control=ChatChannelMetadata.controlInsertion(text);
        String insertion=control!=null?control:text.getStyle().getInsertion();
        if(insertion!=null&&insertion.startsWith("springplus:chat-state:")){
            var state=ChatChannelMetadata.capability(insertion);
            if(state!=null){sending=state.sending();staff=state.staff();spy=state.spy();}
            return true;
        }
        String channel=ChatChannelMetadata.channel(insertion);
        history.add(new Row(text.copy(),channel,System.currentTimeMillis()));if(history.size()>200)textCache.remove(history.removeFirst().text());return false;
    }
    public static void clear(){history.clear();textCache.clear();}
    public static int chatWidth(int guiWidth){
        int available=Math.max(1,guiWidth-16);
        int configured=net.minecraft.client.gui.components.ChatComponent.getWidth(Minecraft.getInstance().options.chatWidth().get());
        return Math.min(available,Math.max(Math.min(160,available),configured));
    }
    public static int rowHeight(Row row,net.minecraft.client.gui.Font font,int width){
        var entry=cached(row.text());
        if(entry.mediaHeight==null){
            String body=row.text().getString();int marker=body.indexOf("[Стикер:");
            entry.mediaHeight=marker>=0&&WebStickers.isMessage(body.substring(marker).trim())?74:IMAGE_PATTERN.matcher(body).find()?76:0;
        }
        if(entry.mediaHeight!=0)return entry.mediaHeight;
        return Math.max(1,lines(row,font,width).size())*12+2;
    }
    public static Component formatted(Component source){
        var entry=cached(source);
        if(entry.formatted!=null)return entry.formatted;
        var result=Component.empty();
        source.visit((style,value)->{result.append(ChatReactions.text(value).copy().withStyle(style));return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
        entry.formatted=result;return result;
    }
    public static Component prefix(Component source,int length){
        var entry=cached(source);
        if(entry.prefix!=null&&entry.prefixLength==length)return entry.prefix;
        var result=Component.empty();int[] remaining={Math.max(0,length)};
        source.visit((style,value)->{
            int count=Math.min(remaining[0],value.length());
            if(count>0)result.append(Component.literal(value.substring(0,count)).withStyle(style));
            remaining[0]-=count;
            return remaining[0]==0?Optional.of(true):Optional.empty();
        },net.minecraft.network.chat.Style.EMPTY);
        entry.prefixLength=length;entry.prefix=formatted(result);return entry.prefix;
    }
    public static List<Row> rows(String channel){checkConnection();return history.stream().filter(r->!r.channel.equals("staff")||staff).filter(r->!r.channel.equals("spy")||spy).filter(r->channel.equals("all")?filters().visible(r.channel):r.channel.equals(channel)).toList();}
}
