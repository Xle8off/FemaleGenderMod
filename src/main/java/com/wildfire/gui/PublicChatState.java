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
        if(next!=connection){connection=next;history.clear();staff=false;spy=false;sending="global";selectedTab="all";}
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
        history.add(new Row(text.copy(),channel,System.currentTimeMillis()));if(history.size()>200)history.removeFirst();return false;
    }
    public static void clear(){history.clear();}
    public static int chatWidth(int guiWidth){
        int available=Math.max(1,guiWidth-16);
        int configured=net.minecraft.client.gui.components.ChatComponent.getWidth(Minecraft.getInstance().options.chatWidth().get());
        return Math.min(available,Math.max(Math.min(160,available),configured));
    }
    public static int rowHeight(Row row,net.minecraft.client.gui.Font font,int width){
        String body=row.text().getString();int marker=body.indexOf("[Стикер:");
        if(marker>=0&&WebStickers.isMessage(body.substring(marker).trim()))return 74;
        if(java.util.regex.Pattern.compile("https://skin\\.springplus\\.net/api/chat-images/[a-f0-9]{64}\\.png").matcher(body).find())return 76;
        return Math.max(1,font.split(formatted(row.text()),width).size())*12+2;
    }
    public static Component formatted(Component source){
        var result=Component.empty();
        source.visit((style,value)->{result.append(ChatReactions.text(value).copy().withStyle(style));return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
        return result;
    }
    public static Component prefix(Component source,int length){
        var result=Component.empty();int[] remaining={Math.max(0,length)};
        source.visit((style,value)->{
            int count=Math.min(remaining[0],value.length());
            if(count>0)result.append(Component.literal(value.substring(0,count)).withStyle(style));
            remaining[0]-=count;
            return remaining[0]==0?Optional.of(true):Optional.empty();
        },net.minecraft.network.chat.Style.EMPTY);
        return formatted(result);
    }
    public static List<Row> rows(String channel){checkConnection();return history.stream().filter(r->!r.channel.equals("staff")||staff).filter(r->!r.channel.equals("spy")||spy).filter(r->channel.equals("all")?filters().visible(r.channel):r.channel.equals(channel)).toList();}
}
