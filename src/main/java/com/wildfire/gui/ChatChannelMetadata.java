package com.wildfire.gui;
import java.util.Set;
/** Small, strictly bounded metadata protocol; no channel is inferred from display text. */
public final class ChatChannelMetadata {
    public record Capability(String sending,boolean staff,boolean spy){}
    private ChatChannelMetadata(){}
    /** Proxies may wrap the styled payload in an otherwise unstyled component. */
    public static String controlInsertion(net.minecraft.network.chat.Component text){
        String root=text.getStyle().getInsertion();
        if(root!=null&&root.startsWith("springplus:chat-state:"))return root;
        return text.visit((style,value)->{
            String insertion=style.getInsertion();
            return insertion!=null&&insertion.startsWith("springplus:chat-state:")
                ?java.util.Optional.of(insertion):java.util.Optional.<String>empty();
        },net.minecraft.network.chat.Style.EMPTY).orElse(null);
    }
    public static String channel(String insertion){
        if(insertion==null||!insertion.startsWith("springplus:chat:"))return "system";
        String value=insertion.substring(16);
        return Set.of("global","local","staff","discord","system","spy").contains(value)?value:"system";
    }
    public static Capability capability(String insertion){
        if(insertion==null||!insertion.startsWith("springplus:chat-state:"))return null;
        String[] values=insertion.substring(22).split(":",-1);
        if((values.length!=2&&values.length!=3)||!Set.of("global","local","staff").contains(values[0])||!Set.of("true","false").contains(values[1]))return null;
        if(values.length==3&&!Set.of("true","false").contains(values[2]))return null;
        return new Capability(values[0],values[1].equals("true"),values.length==3&&values[2].equals("true"));
    }
}
