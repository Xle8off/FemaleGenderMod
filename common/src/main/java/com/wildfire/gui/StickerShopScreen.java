package com.wildfire.gui;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A free catalog window. Installing changes local preferences only. */
public final class StickerShopScreen extends Screen {
    private final Screen parent;
    private String selected="";
    private int scrollOffset;
    private int left,top,w,h,columns;
    public StickerShopScreen(Screen parent){super(Component.literal("Магазин стикеров"));this.parent=parent;}
    public StickerShopScreen(Screen parent,WebStickers.Sticker sticker){this(parent);selected=sticker.packId();}
    private int gridTop(){return top+(selected.isEmpty()?42:112);}
    private int gridBottom(){return top+h-10;}
    private int gridLeft(){return left+(w-(columns*64-4))/2;}
    private int maxScroll(int count){return Math.max(0,((count+columns-1)/columns)*70-4-(gridBottom()-gridTop()));}
    private List<WebStickers.Sticker> entries(){
        var all=WebStickers.entries();
        if(!selected.isEmpty())return all.stream().filter(s->s.id().startsWith(selected+"/")).toList();
        var packs=new LinkedHashMap<String,WebStickers.Sticker>();
        all.forEach(s->packs.putIfAbsent(s.id().split("/")[0],s));return List.copyOf(packs.values());
    }
    @Override protected void init(){
        WebStickers.refresh();w=Math.min(width-16,Math.max(300,width/2));h=Math.min(height-16,Math.max(230,height*2/3));
        left=(width-w)/2;top=(height-h)/2;columns=Math.max(1,(w-24)/64);
        addRenderableWidget(FlatButtons.builder(Component.literal(selected.isEmpty()?"Закрыть":"← Паки"),b->{if(selected.isEmpty())onClose();else{selected="";scrollOffset=0;rebuildWidgets();}}).bounds(left+w-90,top+8,80,20).build());
        if(!selected.isEmpty())addRenderableWidget(FlatButtons.builder(Component.literal(ReactionHistory.get().installed(selected)?"Удалить":"Добавить"),b->{ReactionHistory.get().togglePack(selected);rebuildWidgets();}).bounds(left+w-110,top+62,100,24).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xA0000000);FlatButtons.plate(g,left,top,w,h,true,false,false,UiTheme.ACCENT);
        g.text(font,"Магазин стикеров",left+12,top+14,UiTheme.TEXT,false);
        var entries=entries();
        if(!selected.isEmpty()&&!entries.isEmpty()){
            WebStickers.draw(g,entries.getFirst(),left+12,top+39,64);
            g.text(font,font.plainSubstrByWidth(entries.getFirst().pack(),Math.max(1,w-200)),left+84,top+45,UiTheme.TEXT,false);
            String author=entries.getFirst().author();
            if(author.isBlank()||author.equalsIgnoreCase("System"))author="Сервер";
            g.text(font,font.plainSubstrByWidth(entries.size()+" стикеров · "+author,Math.max(1,w-200)),left+84,top+60,UiTheme.MUTED,false);
        }
        int grid=gridTop(),bottom=gridBottom();
        int max=maxScroll(entries.size());scrollOffset=Math.clamp(scrollOffset,0,max);
        g.enableScissor(left+8,grid,left+w-8,bottom);
        for(int i=(scrollOffset/70)*columns;i<entries.size();i++){
            int y=grid+i/columns*70-scrollOffset;if(y>=bottom)break;
            int x=gridLeft()+i%columns*64;var sticker=entries.get(i);
            FlatButtons.plate(g,x,y,60,66,true,mx>=x&&mx<x+60&&my>=y&&my<y+66,false,UiTheme.ACCENT);
            WebStickers.draw(g,sticker,x+4,y+2,52);
            if(selected.isEmpty())g.text(font,font.plainSubstrByWidth(sticker.pack(),56),x+2,y+55,UiTheme.TEXT,false);
        }
        g.disableScissor();
        if(max>0){
            int track=bottom-grid,thumb=Math.max(12,track*track/(track+max));
            int thumbY=grid+(track-thumb)*scrollOffset/max;
            g.fill(left+w-6,grid,left+w-4,bottom,UiTheme.CHROME);
            g.fill(left+w-6,thumbY,left+w-4,thumbY+thumb,UiTheme.ACCENT);
        }
        if(entries.isEmpty())g.text(font,selected.isEmpty()?WebStickers.status():"Пак удалён или недоступен",left+12,grid,UiTheme.MUTED,false);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick){
        if(event.button()==0&&selected.isEmpty()&&event.x()>=gridLeft()&&event.x()<gridLeft()+columns*64-4&&event.y()>=gridTop()&&event.y()<gridBottom()){
            int localY=(int)(event.y()-gridTop())+scrollOffset,localX=(int)(event.x()-gridLeft());
            int index=localY/70*columns+localX/64;
            var entries=entries();if(localX%64<60&&localY%70<66&&index<entries.size()){selected=entries.get(index).id().split("/")[0];scrollOffset=0;rebuildWidgets();return true;}
        }return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
        if(x>=left&&x<left+w&&y>=gridTop()&&y<gridBottom()){
            scrollOffset=Math.clamp(scrollOffset-(int)Math.round(sy*35),0,maxScroll(entries().size()));return true;
        }return super.mouseScrolled(x,y,sx,sy);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void tick(){if(!com.wildfire.client.music.SpringPlusSession.enabled())minecraft.gui.setScreen(null);else WebStickers.refresh();}
    @Override public boolean isPauseScreen(){return false;}
}
