package com.wildfire.gui;
import java.nio.file.*;
import java.util.*;

public final class StickerPreferencesCheck {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        for(int height:new int[]{300,360,480,540,720,1080}){
            int emoji=ReactionPickerLayout.emojiRows(height),sticker=ReactionPickerLayout.stickerRows(height);
            check(ReactionPickerLayout.top(height)+49+emoji*24==height-132,"shared emoji frame");
            check(ReactionPickerLayout.top(height)+28+sticker*36<=height-132,"sticker rows fit shared frame");
            check(sticker>=1&&(height<480||sticker>2),"sticker rows use full available panel");
        }
        check(Arrays.equals(StickerResampler.dimensions(512,256,96),new int[]{96,48}),"contain aspect ratio");
        check(Arrays.equals(StickerResampler.dimensions(32,16,128),new int[]{32,16}),"never upscale");
        check(Arrays.equals(StickerResampler.resize(new int[]{0xff000000,0xffffffff,0xffffffff,0xff000000},2,2,1,1),new int[]{0xff808080}),"checkerboard area averaged");
        check(StickerResampler.resize(new int[]{0x00ff0000,0xff0000ff},2,1,1,1)[0]==0x800000ff,"transparent RGB cannot produce halos");
        check(StickerResampler.resize(new int[]{0x00ff0000,0x0000ff00},2,1,1,1)[0]==0,"transparent output stays zero");
        check(Arrays.equals(StickerResampler.resize(new int[]{0x12345678},1,1,1,1),new int[]{0x12345678}),"identity exact");
        try{StickerResampler.resize(new int[2],2,2,1,1);throw new AssertionError("bad input accepted");}catch(IllegalArgumentException expected){}
        Path directory=Files.createTempDirectory("springplus-reactions-test-");
        try{
            Path file=directory.resolve("preferences.json");var history=new ReactionHistory(file);
            check(!history.installed("samson_vk"),"packs are opt-in");
            history.togglePack("samson_vk");check(history.installed("samson_vk"),"pack installed locally");
            history.togglePack("test");history.togglePack("test");check(!history.installed("test"),"pack removal");
            history.togglePack("../../secret");check(!history.installed("../../secret"),"invalid pack rejected");
            history.toggle("emoji","smile");history.toggle("sticker","test/1");
            history.used("emoji","smile");history.used("emoji","heart");history.used("emoji","smile");
            check(history.ids("emoji",false).equals(List.of("smile","heart")),"MRU order and duplicate elimination");
            check(history.favorite("emoji","smile")&&history.favorite("sticker","test/1"),"favorites separate");
            history.toggle("emoji","smile");check(!history.favorite("emoji","smile"),"favorite removal");
            for(int i=0;i<80;i++)history.used("sticker","test/"+i);
            check(history.ids("sticker",false).size()==64&&history.ids("sticker",false).getFirst().equals("test/79"),"recent bounded");
            for(int i=0;i<300;i++)history.toggle("emoji","item_"+i);
            check(history.ids("emoji",true).size()==256,"favorites bounded");
            long revision=history.revision();history.used("sticker","../../secret");
            check(history.revision()==revision,"invalid identifier ignored");
            check(history.awaitSaved(5000),"background persistence finished");
            var loaded=new ReactionHistory(file);
            check(loaded.installed("samson_vk")&&!loaded.installed("test"),"installed packs survive restart");
            check(loaded.ids("emoji",true).equals(history.ids("emoji",true))&&loaded.ids("sticker",false).equals(history.ids("sticker",false)),"preferences survive restart");
            Files.writeString(file,"{broken");check(new ReactionHistory(file).ids("emoji",true).isEmpty(),"corrupt preferences safe");
        }finally{try(var paths=Files.walk(directory)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("Sticker smoothing and favorites checks passed");
    }
}

