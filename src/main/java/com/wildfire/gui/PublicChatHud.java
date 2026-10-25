package com.wildfire.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The same component history and media as the open chat, without interactive targets. */
public final class PublicChatHud {
    private static final ChatMotion.Viewport motion=new ChatMotion.Viewport();
    private static Object connection;
    private static int motionWidth;
    private PublicChatHud(){}
    public static void render(GuiGraphicsExtractor g){
        var client=Minecraft.getInstance();
        HudReveal.observe();
        if(HudReveal.menuVisible())return;
        if(client.options.chatVisibility().get().name().equals("HIDDEN"))return;
        var font=client.font;
        int width=PublicChatState.chatWidth(g.guiWidth());
        int left=8,bottom=PublicChatControls.historyBottom(g.guiHeight()),top=Math.max(8,bottom-g.guiHeight()/3),y=bottom+2;
        long now=System.currentTimeMillis();
        var rows=PublicChatState.rows("all").stream().filter(row->now-row.receivedAt()<=10000).toList();
        var preferences=PublicChatState.filters();
        if(connection!=client.getConnection()||motionWidth!=width){connection=client.getConnection();motionWidth=width;motion.reset();}
        var layout=motion.update(rows,rows.stream().mapToInt(row->PublicChatState.rowHeight(row,font,width-16)).toArray(),0,0,
            System.nanoTime(),preferences.durationNanos(),preferences.smoothShift(),false,false);
        y+=Math.round(layout.insertion());
        g.pose().pushMatrix();
        g.pose().translate((float)(-(width+8)*HudReveal.remaining()),0);
        g.enableScissor(left,top,left+width,bottom);
        int backgroundTop=y;
        for(int i=rows.size()-1;i>=0&&backgroundTop>top;i--){
            var row=rows.get(i);if(now-row.receivedAt()>10000)break;
            backgroundTop-=PublicChatState.rowHeight(row,font,width-16);
        }
        // Line boxes include trailing spacing; give their glyphs equal outer padding.
        if(backgroundTop<y){
            float backdropOpacity=rows.stream().map(row->preferences.disappearance()==ChatMotion.Appearance.OFF?1f:
                ChatMotion.lifetime(now-row.receivedAt(),10000,preferences.durationNanos())).max(Float::compare).orElse(0f);
            if(PublicChatState.filters().vanillaChat())FlatButtons.backdrop(g,left,Math.max(top,backgroundTop-3),left+width,bottom,ChatMotion.color(0x78000000,backdropOpacity),6);
            else FlatButtons.backdrop(g,left,Math.max(top,backgroundTop-3),left+width,bottom,ChatMotion.color(0x780D1118,backdropOpacity),3);
        }
        for(int i=rows.size()-1;i>=0;i--){
            var row=rows.get(i);long age=now-row.receivedAt();if(age>10000)break;
            var animation=ChatMotion.entry(preferences.appearance(),System.nanoTime()-row.animatedAt(),preferences.durationNanos(),preferences.curve());
            int rowX=left+8+animation.x(),offsetY=animation.y();y+=offsetY;
            int rowHeight=PublicChatState.rowHeight(row,font,width-16);
            var leaving=ChatMotion.exit(preferences.disappearance(),
                ChatMotion.lifetime(age,10000,preferences.durationNanos())*ChatMotion.edge(y-rowHeight,rowHeight,top,bottom));
            rowX+=leaving.x();y+=leaving.y();offsetY+=leaving.y();
            float opacity=animation.opacity()*leaving.opacity();
            String body=row.text().getString();int marker=body.indexOf("[Стикер:");
            var sticker=marker>=0?WebStickers.message(body.substring(marker).trim()):null;
            var image=PublicChatState.IMAGE_PATTERN.matcher(body);
            if(sticker!=null||marker>=0&&WebStickers.isMessage(body.substring(marker).trim())){
                y-=74;if(y+74<top)break;
g.text(font,PublicChatState.prefix(row.text(),marker).getVisualOrderText(),rowX,y,ChatMotion.color(UiTheme.TEXT,opacity),false);
if(sticker!=null)WebStickers.draw(g,sticker,rowX,y+12,60,opacity);
else g.text(font,WebStickers.missingLabel(),rowX,y+24,ChatMotion.color(UiTheme.MUTED,opacity),false);
            }else if(image.find()){
                y-=76;if(y+76<top)break;
g.text(font,PublicChatState.prefix(row.text(),image.start()).getVisualOrderText(),rowX,y,ChatMotion.color(UiTheme.TEXT,opacity),false);
ChatImages.draw(g,image.group(1),rowX,y+12,60,opacity);
            }else{
                var lines=PublicChatState.lines(row,font,width-16);
                int height=Math.max(1,lines.size())*12;y-=height+2;if(y+height<top)break;
for(int line=0;line<lines.size();line++)g.text(font,lines.get(line),rowX,y+line*12,ChatMotion.color(UiTheme.TEXT,opacity),false);
            }
            y-=offsetY;
        }
        g.disableScissor();
        g.pose().popMatrix();
    }
}
