package com.wildfire.client.music;
import com.google.gson.JsonParser;
import java.util.UUID;
import java.util.function.Consumer;
/** Only refreshed while the account panel is open, on the client thread. */
public final class CardBalanceClient {
    private static Consumer<String> sender=ignored->{};
    private static String nonce="",amount="—";
    private static long requestedAt;
    private CardBalanceClient() {}
    public static void configure(Consumer<String> value){reset();sender=value;}
    public static void reset(){nonce="";amount="—";requestedAt=0;}
    public static String display(){return amount;}
    public static void refresh(){
        if(!SpringPlusSession.enabled()){reset();return;}
        long now=System.currentTimeMillis();
        if(now-requestedAt<15000)return;
        requestedAt=now;nonce=UUID.randomUUID().toString();
        try{sender.accept(nonce);}catch(RuntimeException unavailable){amount="—";}
    }
    public static void accept(String body){
        if(!SpringPlusSession.enabled()||body.length()>512)return;
        try{var root=JsonParser.parseString(body).getAsJsonObject();
            if(!root.get("request").getAsString().equals(nonce))return;
            if(!root.get("state").getAsString().equals("ok")){amount="—";return;}
            String value=root.get("amount").getAsString();
            if(value.matches("-?\\d{1,18}(?:\\.\\d{1,8})?"))amount=value;
        }catch(RuntimeException ignored){}
    }
}
