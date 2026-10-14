package com.wildfire.gui;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import java.net.http.HttpResponse;
public final class WebStickersCheck {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejected(String json){try{WebStickers.parse(json);throw new AssertionError("Invalid catalog accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args)throws Exception{
        var uploadsField=WebStickers.class.getDeclaredField("pendingUploads");uploadsField.setAccessible(true);
        @SuppressWarnings("unchecked") var uploads=(java.util.Queue<Runnable>)uploadsField.get(null);
        int[] uploaded={0};
        uploads.add(()->uploaded[0]++);uploads.add(()->uploaded[0]++);
        WebStickers.tick();
        check(uploaded[0]==1&&uploads.size()==1,"one GPU upload maximum per client tick");
        WebStickers.tick();WebStickers.tick();
        check(uploaded[0]==2&&uploads.isEmpty(),"queued uploads drain once, empty tick is safe");
        var slotsField=WebStickers.class.getDeclaredField("uploadSlots");slotsField.setAccessible(true);
        check(((java.util.concurrent.Semaphore)slotsField.get(null)).availablePermits()==4,"decoded images have a bounded backlog");
        check(WebStickers.isMessage("[Стикер:u_4d4b0ca5bb74_0df8521f2361/17]"),"incoming sticker recognized before catalog download");
        for(String invalid:new String[]{"[Стикер:]","[Стикер:../secret]","[Стикер:https://evil.invalid/a]","ordinary text"})
            check(!WebStickers.isMessage(invalid),"reject invalid incoming token");
        var budget=new StickerTextureBudget();
        for(int i=0;i<80;i++)check(budget.put("visible"+i,96L*96*32*4).isEmpty(),"visible animated tiles must not evict each other");
        budget.touch("visible0");
        var evictions=budget.put("large",16L*1024*1024);
        check(!evictions.isEmpty()&&!evictions.contains("visible0"),"memory cap evicts least recently viewed textures");
        byte[] png=Files.readAllBytes(Path.of(args[0],"assets/appearance_sync/textures/gui/emoji/smile.png"));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));
        String row="{\"id\":\"samson_vk/3710957\",\"pack\":\"Самсон\",\"label\":\"Самсон #01\",\"sha256\":\""+hash+"\",\"width\":72,\"height\":72,\"bytes\":"+png.length+"}";
        String json="{\"version\":1,\"stickers\":["+row+"]}";
        var entry=WebStickers.parse(json).getFirst();WebStickers.validate(png,entry);
        var animated=WebStickers.parse(json.replace("\"bytes\":","\"durations\":[80,120],\"bytes\":")).getFirst();
        check(animated.frame(0)==0&&animated.frame(79)==0&&animated.frame(80)==1&&animated.frame(199)==1&&animated.frame(200)==0,"animation loops with per-frame timing");
        rejected(json.replace("\"bytes\":","\"durations\":[0,120],\"bytes\":"));
        rejected(json.replace("\"bytes\":","\"durations\":[100],\"bytes\":"));
        check(entry.message().equals("[Стикер:samson_vk/3710957]"),"stable text token fits existing history");
        for(String bad:new String[]{json.replace("samson_vk/3710957","../secret"),json.replace("samson_vk/3710957","https://evil.invalid/a"),json.replace("\"width\":72","\"width\":65536"),json.replace(hash,"not-a-hash"),"{\"version\":1,\"stickers\":["+row+","+row+"]}"})rejected(bad);
        png[30]^=1;try{WebStickers.validate(png,entry);throw new AssertionError("Corrupt PNG accepted");}catch(IllegalArgumentException expected){}
        var clazz=Class.forName("com.wildfire.gui.WebStickers$LimitedBody");
        var constructor=clazz.getDeclaredConstructor(int.class);constructor.setAccessible(true);
        @SuppressWarnings("unchecked") var subscriber=(HttpResponse.BodySubscriber<byte[]>)constructor.newInstance(16);
        boolean[] cancelled={false};
        subscriber.onSubscribe(new Flow.Subscription(){public void request(long n){}public void cancel(){cancelled[0]=true;}});
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[17])));
        check(cancelled[0]&&subscriber.getBody().toCompletableFuture().isCompletedExceptionally(),"oversized HTTP body cancelled before buffering");
        System.out.println("Web sticker checks passed: catalog bounds, IDs, hashes, PNG dimensions, capped HTTP subscriber");
    }
}
