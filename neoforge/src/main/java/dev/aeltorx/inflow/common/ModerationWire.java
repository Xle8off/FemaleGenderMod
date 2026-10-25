package dev.aeltorx.inflow.common;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;

/** A bounded Minecraft UTF string; Bukkit plugin messaging and Fabric share these bytes. */
public final class ModerationWire {
    public static final int LIMIT=30000;
    private ModerationWire(){}
    public static byte[] encode(String text){
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>LIMIT)throw new IllegalArgumentException("Packet too large");
        var out=new ByteArrayOutputStream(bytes.length+3);int n=bytes.length;
        do{int b=n&127;n>>>=7;out.write(n==0?b:b|128);}while(n!=0);
        out.writeBytes(bytes);return out.toByteArray();
    }
    public static String decode(byte[] packet){
        if(packet.length>LIMIT+3)throw new IllegalArgumentException("Packet too large");
        int n=0,pos=0,shift=0;
        while(true){if(pos>=packet.length||shift>21)throw new IllegalArgumentException("Invalid VarInt");
            int b=packet[pos++]&255;n|=(b&127)<<shift;if((b&128)==0)break;shift+=7;}
        if(n<0||n>LIMIT||n!=packet.length-pos)throw new IllegalArgumentException("Invalid length");
        return new String(packet,pos,n,StandardCharsets.UTF_8);
    }
    public static boolean target(String value){return value!=null&&(value.matches("[A-Za-z0-9_]{1,16}")||value.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"));}
    public static boolean rule(String value){return value!=null&&value.matches("[0-9]{1,3}\\.[0-9]{1,3}");}
    public static void checkJsonDepth(String text){int depth=0;boolean quoted=false,escaped=false;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}else if(c=='"')quoted=true;else if(c=='{'||c=='['){if(++depth>12)throw new IllegalArgumentException("Nested packet");}else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("Invalid packet");}}if(depth!=0||quoted)throw new IllegalArgumentException("Incomplete packet");}
}
