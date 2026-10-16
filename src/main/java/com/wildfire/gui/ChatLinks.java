package com.wildfire.gui;

import java.net.URI;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;

/** Links are display-only components; wire messages remain plain Unicode. */
public final class ChatLinks {
    private static final Pattern URL=Pattern.compile("(?i)(?<![\\p{L}\\p{N}_@])(?:https?://|www\\.)[^\\s<>\\\"\\p{Cntrl}]+");
    private ChatLinks() {}
    public static Component text(String body) {
        var result=Component.empty();var matcher=URL.matcher(body);int last=0;
        while(matcher.find()){
            int end=matcher.end();
            while(end>matcher.start()&&".,;:!?)]}".indexOf(body.charAt(end-1))>=0)end--;
            String label=body.substring(matcher.start(),end);
            URI uri=valid(label);
            if(uri==null)continue;
            result.append(ChatReactions.text(body.substring(last,matcher.start())));
            result.append(Component.literal(label).withStyle(style->style.withColor(0x77BFFF).withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(uri))));
            last=end;
        }
        return result.append(ChatReactions.text(body.substring(last)));
    }
    public static URI valid(String value) {
        try {
            URI uri=URI.create(value.regionMatches(true,0,"www.",0,4)?"https://"+value:value);
            if(!("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))
                ||uri.getHost()==null||uri.getUserInfo()!=null)return null;
            return uri;
        }catch(IllegalArgumentException invalid){return null;}
    }
}
