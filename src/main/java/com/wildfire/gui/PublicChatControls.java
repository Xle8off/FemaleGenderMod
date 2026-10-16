package com.wildfire.gui;

import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

/** An optional enhanced surface; the vanilla input still owns commands, history and focus. */
public final class PublicChatControls {
    private static PublicChatControls current;
    private static java.util.function.BiConsumer<net.minecraft.client.gui.screens.Screen,Consumer<String>> attachment;
    public static void setAttachment(java.util.function.BiConsumer<net.minecraft.client.gui.screens.Screen,Consumer<String>> value){attachment=value;}
    public static boolean active(){return current!=null&&Minecraft.getInstance().gui.screen()==current.screen&&com.wildfire.client.music.SpringPlusSession.enabled();}
    public static int historyBottom(int guiHeight){
        return current!=null&&current.screen.height==guiHeight?current.rowTop():guiHeight-23;
    }
    public int rowRight(){return inputLeft+w;}
    public int rowLeft(){return inputLeft;}
    public int composerWidth(){return composer.getWidth();}
    public int rowWidth(){return w;}
    public boolean composerFocused(){return commandMode?input.isFocused():composer.isFocused();}
    public int rowTop(){return screen.height-2-(int)Math.round(animatedHeight);}
    public boolean vanilla(){return PublicChatState.filters().vanillaChat();}
    public static int suggestionBottom(int fallback){return active()?current.rowTop():fallback;}
    public void focusComposer(){
        if(Minecraft.getInstance().gui.screen()!=screen)return;
        if(picker&&!stickers&&pickerSearch.isFocused())return;
        pickerSearch.setFocused(false);
        syncComposer();
        commandMode=input.getValue().startsWith("/");
        input.visible=commandMode;composer.visible=!commandMode;
        var target=commandMode?input:composer;
        // No Chat Reports accepts only EditBox in its ChatScreen focus override.
        // Keep normal routing first; recover the parent listener only when rejected.
        ChatFocusRecovery.ensure(
            (net.minecraft.client.gui.components.events.GuiEventListener)target,
            screen::getFocused,screen::setFocused,
            ((ChatScreenFocusAccess)screen)::springplus$setFocusedListener,
            previous->previous.setFocused(false));
        if(!target.isFocused())target.setFocused(true);
    }
    public boolean composerKey(net.minecraft.client.input.KeyEvent event){
        if(input.getValue().startsWith("/")||event.key()==258||event.isConfirmation()||event.key()==256||event.key()==264||event.key()==265)return false;
        return composer.keyPressed(event);
    }
    private final MultiLineEditBox composer;
    private final ModSearchBox pickerSearch;
    public boolean searchKey(net.minecraft.client.input.KeyEvent event){
        if(!picker||stickers||!pickerSearch.isFocused())return false;
        if(event.key()==256||event.isConfirmation()){pickerSearch.setFocused(false);focusComposer();}
        else pickerSearch.keyPressed(event);
        return true;
    }
    public boolean searchClick(net.minecraft.client.input.MouseButtonEvent event,boolean doubled){
        if(!picker||stickers||pickerProgress<1||!pickerSearch.isMouseOver(event.x(),event.y()))return false;
        screen.setFocused(pickerSearch);pickerSearch.setFocused(true);
        pickerSearch.mouseClicked(event,doubled);return true;
    }
    private double animatedHeight=21;
    private long animationTime;
    private long headerOpened=System.nanoTime();
    private float headerOffset;
    private boolean commandMode;
    private boolean syncingComposer;
    public static void syncMentionCursor(EditBox input){
        if(active()&&current.input==input&&!input.getValue().startsWith("/")){
            var field=((ChatComposerAccess)current.composer).springplus$textField();
            input.setCursorPosition(field.cursor());
            // Only selection presence matters to mention suggestions; the composer owns its range.
            input.setHighlightPos(field.hasSelection()?(field.cursor()==0?field.getSelectedText().length():0):field.cursor());
        }
    }
    private void syncComposer(){
        if(!composer.getValue().equals(input.getValue())){
            syncingComposer=true;
            try{composer.setValue(input.getValue());}finally{syncingComposer=false;}
            ((ChatComposerAccess)composer).springplus$textField().seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE,input.getCursorPosition());
        }
    }
    private final ChatScreen screen;
    private final EditBox input;
    private final List<Button> buttons=new ArrayList<>();
    private final List<Hit> hits=new ArrayList<>();
    private final List<TextHit> textHits=new ArrayList<>();
    private record TextHit(int x,int y,net.minecraft.util.FormattedCharSequence text){}
    private final Map<Hit,Runnable> favorites=new HashMap<>();
    private record Hit(int x,int y,int w,int h,Runnable action){boolean contains(double px,double py){return px>=x&&px<x+w&&py>=y&&py<y+h;}}
    private String channel="all",pack="",collection="all";
    private boolean picker,stickers;
    private final ToastControlsReveal pickerReveal=new ToastControlsReveal();
    private double pickerProgress;
    private boolean settings;
    private int emojiCategory,packOffset,packStripTop;
    private static final String[] MODES={"all","local","global","discord","staff","system","spy"};
    private int scroll,pickerScroll,left=8,inputLeft,top,restTop,bottom,w,columns;
    private int headerHeight=22;
    private int maxHistoryScroll;
    private final ChatMotion.Viewport historyMotion=new ChatMotion.Viewport();
    private int motionWidth;
    private float renderedHistoryScroll;
    private int settingsScroll;
    private int settingsHeight(){return 212+(4+(PublicChatState.staff?1:0)+(PublicChatState.spy?1:0))*24;}
    private boolean historyDragging;
    private double historyGrab;
    private boolean historyScrollbar(){return maxHistoryScroll>0&&bottom-historyTop()>16&&!picker&&pickerProgress<=0&&!settings;}
    public boolean historyDragging(){return historyDragging;}
    private int scrollTrackTop(){return historyTop()+2;}
    private int scrollTrackHeight(){return Math.max(0,bottom-historyTop()-4);}
    private void renderHistoryScrollbar(GuiGraphicsExtractor g,int mx,int my){
        if(!historyScrollbar()){historyDragging=false;return;}
        int x=left+w-5,y=scrollTrackTop(),height=scrollTrackHeight();
        var thumb=ChatScrollBounds.thumb(height,maxHistoryScroll,Math.round(renderedHistoryScroll));
        g.fill(x,y,x+3,y+height,0x403D354B);
        boolean hover=mx>=x-3&&mx<left+w&&my>=y&&my<y+height;
        g.fill(x,y+thumb.top(),x+3,y+thumb.top()+thumb.height(),historyDragging||hover?0xFFE1B4F5:0xBFAE86C7);
    }
    public boolean dragHistory(double y,int button){
        if(!historyDragging||button!=0)return false;
        if(!historyScrollbar()){historyDragging=false;return true;}
        scroll=ChatScrollBounds.scrollAt(y-scrollTrackTop()-historyGrab,scrollTrackHeight(),maxHistoryScroll);
        return true;
    }
    public boolean releaseHistory(int button){
        if(button!=0||!historyDragging)return false;historyDragging=false;return true;
    }
    private int historyTop(){return top+headerHeight;}
    private final Consumer<net.minecraft.network.chat.ClickEvent> clickEvent;
    private net.minecraft.network.chat.Style hovered;
    public PublicChatControls(ChatScreen screen,EditBox input,Consumer<AbstractWidget> add,Consumer<net.minecraft.network.chat.ClickEvent> clickEvent){
        pickerSearch=new ModSearchBox(Minecraft.getInstance().font,0,0,100,18,Component.literal("Поиск эмодзи"));
        pickerSearch.setMaxLength(80);pickerSearch.setHint(Component.literal("Поиск: emoji, heart, флаги…"));
        pickerSearch.setResponder(value->pickerScroll=0);
        this.clickEvent=clickEvent;
        this.screen=screen;this.input=input;
        input.setCanLoseFocus(true);
        if(current!=null&&current.screen==screen){headerOpened=current.headerOpened;input.setValue(current.input.getValue());}
        PublicChatState.checkConnection();
        channel=PublicChatState.selectedTab;
        if(current!=null&&current.screen.height==screen.height)animatedHeight=current.animatedHeight;
        current=this;
        var client=Minecraft.getInstance();
        w=PublicChatState.chatWidth(screen.width);
        inputLeft=left;
        bottom=input.getY()-6;top=Math.max(8,bottom-Math.max(100,screen.height*2/3));
        restTop=top;
        input.setX(inputLeft+4);input.setWidth(Math.max(40,w-26));
        composer=MultiLineEditBox.builder().setX(inputLeft).setY(screen.height-22)
            .setPlaceholder(Component.literal("Написать сообщение")).setTextColor(UiTheme.TEXT)
            .setShowDecorations(false).setShowBackground(false)
            .build(client.font,Math.max(40,w-22),20,Component.literal("Сообщение"));
        composer.setCharacterLimit(256);FormattedChatInput.register(composer);
        composer.setValue(input.getValue());composer.setValueListener(value->{if(!syncingComposer)input.setValue(value);});add.accept(composer);
        commandMode=input.getValue().startsWith("/");input.visible=commandMode;composer.visible=!commandMode;
        String[] labels={"Все","Локальный","Глобальный","Discord","Staff","Система","SPY"};
        for(int i=0;i<MODES.length;i++){
            Button b=channelButton(MODES[i],labels[i]);
            b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(switch(MODES[i]){
                case "all"->"Все каналы";case "local"->"Локальный чат";case "global"->"Глобальный чат";
                case "discord"->"Сообщения Discord";case "staff"->"Чат персонала";
                case "system"->"Системные уведомления";default->"Наблюдение за чатами (SPY)";
            })));
            b.setTooltipDelay(java.time.Duration.ofMillis(350));
            add.accept(b);buttons.add(b);
        }
        Button reactions=iconButton("Эмодзи и стикеры",1,inputLeft+w-18,()->{picker=!picker;pickerReveal.update(picker,System.nanoTime());pickerScroll=0;WebStickers.refresh();});add.accept(reactions);buttons.add(reactions);
        Button gear=new Button(left+w-20,top+24,20,20,Component.literal("Фильтры «Все»"),b->{settings=!settings;picker=false;},message->message.get()){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
                beginHeader(g);
                try{
                if(isHoveredOrFocused()||settings)FlatButtons.backdrop(g,getX(),getY(),getX()+getWidth(),getY()+getHeight(),UiTheme.SURFACE,2);
                float iconScale=vanilla()?.6f:.7f;
                float inset=(getWidth()-20*iconScale)/2f;
                g.pose().pushMatrix();g.pose().translate(getX()+inset,getY()+inset);g.pose().scale(iconScale,iconScale);ColorMenuTitleIcons.draw(g,3,0,0);g.pose().popMatrix();
                }finally{endHeader(g);}
            }
        };
        gear.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Настройки чата")));
        gear.setTooltipDelay(java.time.Duration.ofMillis(350));
        add.accept(gear);buttons.add(gear);
    }
    private Button channelButton(String mode,String label){
        return new Button(left,top,26,24,Component.literal(label),ignored->{
            channel=mode;PublicChatState.selectedTab=mode;scroll=0;historyMotion.reset();picker=false;settings=false;
            if(Set.of("global","local","staff","discord").contains(mode))command("chat "+(mode.equals("discord")?"global":mode));
        },message->message.get()){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
                beginHeader(g);
                try{
                boolean selected=channel.equals(mode);
                if(vanilla()){
                    if(selected||isHoveredOrFocused())FlatButtons.backdrop(g,getX(),getY(),getX()+18,getY()+18,0xA0252D38,2);
                    float iconSize=16*.7f;
                    g.pose().pushMatrix();g.pose().translate(getX()+(getWidth()-iconSize)/2f,getY()+(getHeight()-iconSize)/2f);g.pose().scale(.7f,.7f);
                    ChatChannelIcons.draw(g,mode,0,0);g.pose().popMatrix();
                    if(selected)g.fill(getX()+3,getY()+16,getX()+15,getY()+17,UiTheme.ACCENT);
                    return;
                }
                if(selected||isHoveredOrFocused()){
                    FlatButtons.backdrop(g,getX(),getY(),getX()+26,getY()+24,selected?UiTheme.SURFACE:0xFF252D38,3);
                    g.fill(getX(),getY()+21,getX()+26,getY()+24,selected?UiTheme.SURFACE:0xFF252D38);
                }
                ChatChannelIcons.draw(g,mode,getX()+(getWidth()-16)/2,getY()+(getHeight()-16)/2);
                if(selected)g.fill(getX()+4,getY()+23,getX()+22,getY()+24,UiTheme.ACCENT);
                }finally{endHeader(g);}
            }
        };
    }
    private Button iconButton(String label,int kind,int x,Runnable action){return new Button(x,screen.height-18,16,16,Component.literal(label),b->action.run(),message->message.get()){
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            int px=getX()+2,py=getY()+2;
            if(kind==1)ChatActionButton.drawIcon(g,1,px,py,isHoveredOrFocused()?UiTheme.ACCENT:UiTheme.TEXT);
            else if(kind==2){for(int i=0;i<7;i++)g.fill(px+i,py+i,px+i+1,py+14-i,UiTheme.TEXT);}
            else {g.fill(px,py+2,px+14,py+12,UiTheme.TEXT);g.fill(px+1,py+3,px+13,py+11,UiTheme.SURFACE);g.fill(px+3,py+4,px+5,py+6,0xFFFFBF45);for(int i=0;i<6;i++)g.fill(px+2+i,py+10-i/2,px+3+i,py+11,0xFF99BAFF);}
        }
    };}
    private void command(String value){var c=Minecraft.getInstance().getConnection();if(c!=null)c.sendCommand(value);}
    private Hit tile(GuiGraphicsExtractor g,int x,int y,int width,int height,String label,int mx,int my,Runnable action){
        int hitY=settings?Math.max(y,historyTop()):y,hitEnd=settings?Math.min(y+height,bottom):y+height;
        Hit hit=new Hit(x,hitY,width,Math.max(0,hitEnd-hitY),action);if(hitEnd>hitY)hits.add(hit);
        FlatButtons.plate(g,x,y,width,height,true,hit.contains(mx,my),false,UiTheme.ACCENT);
        if(!label.isEmpty()){var f=Minecraft.getInstance().font;String shown=f.plainSubstrByWidth(label,Math.max(1,width-10));g.text(f,shown,x+(width-f.width(shown))/2,y+(height-f.lineHeight)/2,UiTheme.TEXT,false);}
        return hit;
    }
    public void render(GuiGraphicsExtractor g,int mx,int my){
        PublicChatState.checkConnection();hits.clear();textHits.clear();favorites.clear();hovered=null;
        syncComposer();
        boolean commands=input.getValue().startsWith("/");
        if(commands!=commandMode)focusComposer();
        input.visible=commands;composer.visible=!commands;
        int composerLines=Math.max(1,Minecraft.getInstance().font.split(ChatReactions.input(composer.getValue()),Math.max(1,composer.getWidth()-16)).size());
        int target=commands?21:Math.min(Math.max(21,composerLines*9+12),Math.max(21,Math.min(66,screen.height/4)));
        long now=System.nanoTime();double elapsed=animationTime==0?0:Math.min(.05,(now-animationTime)/1e9);animationTime=now;
        animatedHeight+=(target-animatedHeight)*(1-Math.exp(-18*elapsed));
        if(Math.abs(target-animatedHeight)<.15)animatedHeight=target;
        int shown=(int)Math.round(animatedHeight),textHeight=Math.min(shown,composerLines*9+8);
        composer.setHeight(textHeight);composer.setY(rowTop()+(shown-textHeight)/2);bottom=rowTop();
        input.setY(rowTop()+(shown-Minecraft.getInstance().font.lineHeight)/2);
        buttons.get(7).setY(rowTop()+(shown-16)/2);
        boolean showTabs=PublicChatState.filters().showTabs();
        for(int i=0;i<MODES.length;i++)buttons.get(i).visible=showTabs;
        buttons.get(4).visible=showTabs&&PublicChatState.staff;
        buttons.get(6).visible=showTabs&&PublicChatState.spy;
        if(!showTabs)channel="all";
        int tabPitch=vanilla()?20:28,tabHeight=vanilla()?18:24;
        int headerColumns=Math.max(1,(w-32)/tabPitch),slot=0;
        int visibleTabs=0;
        for(int i=0;i<MODES.length;i++)if(buttons.get(i).visible)visibleTabs++;
        headerHeight=showTabs?(Math.max(0,visibleTabs-1)/headerColumns+1)*(tabHeight+2)+4:vanilla()?22:24;
        // Unroll from the composer upwards. Text keeps its final coordinates;
        // the moving top edge reveals it rather than dragging the messages.
        int travel=Math.max(0,bottom-headerHeight-restTop);
        headerOffset=ChatHeaderReveal.offset(System.nanoTime()-headerOpened,travel);
        top=restTop+Math.round(headerOffset);
        for(int i=0;i<MODES.length;i++)if(buttons.get(i).visible){
            buttons.get(i).setWidth(vanilla()?18:26);buttons.get(i).setHeight(tabHeight);
            buttons.get(i).setX(left+4+(slot%headerColumns)*tabPitch);
            buttons.get(i).setY(top+2+(slot/headerColumns)*(tabHeight+2));slot++;
        }
        int gearSize=vanilla()?18:20;
        buttons.get(8).setWidth(gearSize);buttons.get(8).setHeight(gearSize);
        buttons.get(8).setX(left+w-gearSize-4);buttons.get(8).setY(top+2);
        for(int i=0;i<MODES.length;i++)buttons.get(i).active=headerOffset<.1f;
        buttons.get(7).active=headerOffset<.1f;
        buttons.get(8).active=headerOffset<.1f;
        beginHeader(g);
        if(vanilla())FlatButtons.joinedBackdrop(g,showTabs?left:left+w-26,top,left+w,top+headerHeight,0x78000000,6,true,false);
        else FlatButtons.backdrop(g,showTabs?left:left+w-28,top,left+w,top+headerHeight,UiTheme.CHROME,4);
        // Internal joint is square: only the outside top corners are rounded.
        if(!vanilla())g.fill(showTabs?left:left+w-28,top+headerHeight-4,left+w,top+headerHeight,UiTheme.CHROME);
        // One quiet seam inside the joined panel, moving with its opening edge.
        g.fill(showTabs?left:left+w-(vanilla()?26:28),top+headerHeight-1,
            left+w,top+headerHeight,vanilla()?0xA0444B59:UiTheme.BORDER);
        endHeader(g);
        if(channel.equals("staff")&&!PublicChatState.staff)channel="all";
        if(channel.equals("spy")&&!PublicChatState.spy)channel="all";
        input.setHint(Component.literal("Сообщение · "+switch(PublicChatState.sending){case "local"->"Локальный";case "staff"->"Staff";default->"Глобальный";}));
        // The history has no opaque backdrop. Vanilla command suggestions draw above it.
        var f=Minecraft.getInstance().font;var rows=PublicChatState.rows(channel);
        int[] heights=rows.stream().mapToInt(row->PublicChatState.rowHeight(row,f,w-16)).toArray();
        maxHistoryScroll=ChatScrollBounds.maximum(heights,bottom-historyTop());
        if(motionWidth!=w){motionWidth=w;historyMotion.reset();}
        var preferences=PublicChatState.filters();
        var layout=historyMotion.update(rows,heights,scroll,maxHistoryScroll,now,preferences.durationNanos(),
            preferences.smoothShift(),preferences.smoothScroll(),historyDragging);
        scroll=layout.target();renderedHistoryScroll=layout.scroll();
        // Match the closed HUD's baseline; opening only reveals more history.
        scroll=Math.clamp(scroll,0,maxHistoryScroll);
        int y=bottom+2+Math.round(layout.scroll()+layout.insertion());
        g.enableScissor(left,historyTop(),left+w,bottom);
        // One continuous panel between the tab bar and composer; only the outer frame rounds.
        if(!vanilla())g.fill(left+1,historyTop(),left+w-1,bottom,0x780D1118);
        else if(!showTabs)FlatButtons.topLeftBackdrop(g,left,historyTop(),left+w,bottom,0x78000000,6);
        else g.fill(left,historyTop(),left+w,bottom,0x78000000);
        for(int i=rows.size()-1;i>=0;i--){
            var row=rows.get(i);
            var animation=ChatMotion.entry(preferences.appearance(),now-row.animatedAt(),preferences.durationNanos(),preferences.curve());
            int rowX=left+8+animation.x(),offsetY=animation.y();
            y+=offsetY;
            var leaving=ChatMotion.exit(preferences.disappearance(),ChatMotion.edge(y-heights[i],heights[i],historyTop(),bottom));
            rowX+=leaving.x();y+=leaving.y();offsetY+=leaving.y();
            float opacity=animation.opacity()*leaving.opacity();
            String body=row.text().getString();int marker=body.indexOf("[Стикер:");
            var sticker=marker>=0?WebStickers.message(body.substring(marker).trim()):null;
            var imageMatch=java.util.regex.Pattern.compile("https://skin\\.springplus\\.net/api/chat-images/([a-f0-9]{64})\\.png").matcher(body);
            if(sticker!=null||marker>=0&&WebStickers.isMessage(body.substring(marker).trim())){
                y-=74;if(y+74<historyTop())break;
g.text(f,PublicChatState.prefix(row.text(),marker).getVisualOrderText(),rowX,y,ChatMotion.color(UiTheme.TEXT,opacity),false);
                if(sticker!=null){
WebStickers.draw(g,sticker,rowX,y+12,60,opacity);
                    int imageY=y+12,hitTop=Math.max(imageY,historyTop()),hitBottom=Math.min(imageY+60,bottom);
                    if(hitBottom>hitTop)hits.add(new Hit(rowX,hitTop,60,hitBottom-hitTop,()->Minecraft.getInstance().gui.setScreen(new StickerShopScreen(screen,sticker))));
                }
else g.text(f,WebStickers.missingLabel(),rowX,y+24,ChatMotion.color(UiTheme.MUTED,opacity),false);
            }else if(imageMatch.find()){
                String id=imageMatch.group(1);y-=76;if(y+76<historyTop())break;
g.text(f,PublicChatState.prefix(row.text(),imageMatch.start()).getVisualOrderText(),rowX,y,ChatMotion.color(UiTheme.TEXT,opacity),false);
ChatImages.draw(g,id,rowX,y+12,60,opacity);int imageY=y+12;
                if(imageY>=historyTop()&&imageY+60<=bottom)hits.add(new Hit(rowX,imageY,60,60,()->ChatImages.view(screen,id)));
            }else{
                var lines=f.split(PublicChatState.formatted(row.text()),w-16);int height=Math.max(1,lines.size())*12;y-=height+2;if(y+height<historyTop())break;
                var text=g.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR,style->hovered=style);
                for(var line:lines){
                    textHits.add(new TextHit(rowX,y,line));
                    var parameters=new net.minecraft.client.gui.ActiveTextCollector.Parameters(new org.joml.Matrix3x2f(g.pose()))
.withOpacity(opacity).withScissor(left,left+w,historyTop(),bottom);
                    text.accept(net.minecraft.client.gui.TextAlignment.LEFT,rowX,y,parameters,line);y+=12;
                }y-=height;
            }
            y-=offsetY;
        }
        g.disableScissor();
        renderHistoryScrollbar(g,mx,my);
        pickerProgress=pickerReveal.update(picker,System.nanoTime());
        if(picker||pickerProgress>0){
            hits.clear();textHits.clear();hovered=null;
            int revealTop=historyTop()+(int)Math.round((1-pickerProgress)*Math.max(0,bottom-historyTop()));
            g.enableScissor(left,revealTop,left+w,bottom);
            renderPicker(g,mx,my);
            g.disableScissor();
            if(!picker||pickerProgress<1){hits.clear();favorites.clear();}
        }
        if(settings){hits.clear();textHits.clear();hovered=null;renderSettings(g,mx,my);}
    }
    private void beginHeader(GuiGraphicsExtractor g){
        g.enableScissor(left,top,left+w,top+headerHeight);
        g.pose().pushMatrix();
    }
    private void endHeader(GuiGraphicsExtractor g){
        g.pose().popMatrix();g.disableScissor();
    }
    public void renderFrame(GuiGraphicsExtractor g){
        g.fill(left,bottom,left+w,bottom+1,vanilla()?0xA0444B59:UiTheme.ACCENT);
        if(vanilla())return;
        boolean showTabs=PublicChatState.filters().showTabs();
        if(showTabs)FlatButtons.frame(g,left,top,left+w,screen.height-2,UiTheme.ACCENT,4);
        else FlatButtons.joinedFrame(g,left,historyTop(),left+w,screen.height-2,left+w-28,top,UiTheme.ACCENT,4);
    }
    private void renderSettings(GuiGraphicsExtractor g,int mx,int my){
        int visible=Math.max(1,bottom-historyTop());
        settingsScroll=Math.clamp(settingsScroll,0,Math.max(0,settingsHeight()-visible));
        int py=historyTop()-settingsScroll;
        var preferences=PublicChatState.filters();
        g.enableScissor(left,historyTop(),left+w,bottom);
        FlatButtons.plate(g,left,historyTop(),w,visible,true,false,false,UiTheme.ACCENT);
        g.text(Minecraft.getInstance().font,"Настройки чата",left+8,py+8,UiTheme.TEXT,false);
        tile(g,left+6,py+24,w-12,20,(preferences.showTabs()?"✓ ":"— ")+"Кнопки режимов сверху",mx,my,()->{preferences.toggleTabs();scroll=0;historyMotion.reset();});
        tile(g,left+6,py+48,w-12,20,"Появление: "+preferences.appearance().label(),mx,my,preferences::cycleAppearance);
        tile(g,left+6,py+72,w-12,20,"Скорость: "+preferences.speedLabel(),mx,my,preferences::cycleSpeed);
        tile(g,left+6,py+96,w-12,20,(preferences.smoothShift()?"✓ ":"— ")+"Плавный сдвиг сообщений",mx,my,preferences::toggleSmoothShift);
        tile(g,left+6,py+120,w-12,20,(preferences.smoothScroll()?"✓ ":"— ")+"Плавная прокрутка",mx,my,preferences::toggleSmoothScroll);
        tile(g,left+6,py+144,w-12,20,"Исчезновение: "+preferences.disappearanceLabel(),mx,my,preferences::cycleDisappearance);
        tile(g,left+6,py+168,w-12,20,"Движение: "+preferences.curve().label(),mx,my,preferences::cycleCurve);
        g.text(Minecraft.getInstance().font,"Показывать во «Все»",left+8,py+196,UiTheme.TEXT,false);
        String[] labels={"Глобальный","Локальный","Discord","Staff","Система","SPY"};
        int slot=0;
        for(int i=0;i<ChatAllFilters.CHANNELS.size();i++){
            String mode=ChatAllFilters.CHANNELS.get(i);if(mode.equals("staff")&&!PublicChatState.staff||mode.equals("spy")&&!PublicChatState.spy)continue;
            int y=py+212+slot++*24;
            tile(g,left+6,y,w-12,20,(preferences.visible(mode)?"✓ ":"— ")+labels[i],mx,my,()->{preferences.toggle(mode);scroll=0;historyMotion.reset();});
        }
        g.disableScissor();
        int maximum=Math.max(0,settingsHeight()-visible);
        if(maximum>0){
            var thumb=ChatScrollBounds.thumb(Math.max(1,visible-4),maximum,maximum-settingsScroll);
            g.fill(left+w-4,historyTop()+2+thumb.top(),left+w-2,historyTop()+2+thumb.top()+thumb.height(),0xBFAE86C7);
        }
    }
    private List<WebStickers.Sticker> selectedStickers(){
        var all=selectedStickersForPacks();
        String key=collection+":"+pack;
        if(selectionSource==all&&key.equals(selectionKey))return selectionCache;
        selectionSource=all;selectionKey=key;
        return selectionCache=filterStickers(all);
    }
    private List<WebStickers.Sticker> selectionSource=List.of(),selectionCache=List.of();
    private String selectionKey="";
    private List<WebStickers.Sticker> filterStickers(List<WebStickers.Sticker> all){
        if(!collection.equals("all")){var ids=ReactionHistory.get().ids("sticker",collection.equals("favorites"));return ids.stream().map(id->all.stream().filter(s->s.id().equals(id)).findFirst().orElse(null)).filter(Objects::nonNull).toList();}
        return all.stream().filter(s->pack.isEmpty()||s.id().startsWith(pack+"/")).toList();
    }
    private List<ChatReactions.Reaction> emojiSelection=List.of(),categoryIcons;
    private String emojiSelectionKey="";
    private long emojiSelectionRevision=-1;
    private List<ChatReactions.Reaction> selectedEmoji(){
        long revision=ReactionHistory.get().revision();String key=collection+":"+emojiCategory+":"+pickerSearch.getValue();
        if(key.equals(emojiSelectionKey)&&revision==emojiSelectionRevision)return emojiSelection;
        emojiSelectionKey=key;emojiSelectionRevision=revision;
        var matches=EmojiCatalog.select(emojiCategory,pickerSearch.getValue());
        if(collection.equals("all"))return emojiSelection=matches;
        var byId=new HashMap<String,ChatReactions.Reaction>();ChatReactions.ALL.forEach(r->byId.put(r.id(),r));
        var allowed=new HashSet<>(EmojiCatalog.select(0,pickerSearch.getValue()));
        return emojiSelection=ReactionHistory.get().ids("emoji",collection.equals("favorites")).stream().map(byId::get).filter(Objects::nonNull).filter(allowed::contains).toList();
    }
    private Hit reactionTile(GuiGraphicsExtractor g,int x,int y,int size,int mx,int my,Runnable action){
        Hit hit=new Hit(x,y,size,size,action);hits.add(hit);
        if(hit.contains(mx,my))FlatButtons.plate(g,x,y,size,size,true,true,false,UiTheme.ACCENT);
        return hit;
    }
    private void renderPicker(GuiGraphicsExtractor g,int mx,int my){
        int py=historyTop(),ph=bottom-py;
        FlatButtons.plate(g,left,py,w,ph,true,false,false,UiTheme.ACCENT);
        tile(g,left+6,py+5,(w-18)/2,20,"Эмодзи",mx,my,()->{stickers=false;pickerScroll=0;});
        tile(g,left+12+(w-18)/2,py+5,(w-18)/2,20,"Стикеры",mx,my,()->{stickers=true;pickerScroll=0;});
        int selectedTabLeft=stickers?left+12+(w-18)/2:left+6;
        g.fill(selectedTabLeft+8,py+23,selectedTabLeft+(w-18)/2-8,py+25,UiTheme.ACCENT);
        int content=py+(stickers?30:49),size=stickers?40:24;columns=Math.max(1,(w-16)/size);
        if(!stickers){
            pickerSearch.setX(left+6);pickerSearch.setY(py+27);pickerSearch.setWidth(w-12);
            pickerSearch.extractWidgetRenderState(g,mx,my,0);
        }
        int toolbar=bottom-32;
        packStripTop=toolbar;
        if(stickers){
            var packs=stickerPacks();
            int visible=Math.max(1,(w-16)/30);packOffset=Math.clamp(packOffset,0,Math.max(0,packs.size()+3-visible));
            var entries=new ArrayList<>(packs.entrySet());
            for(int i=packOffset;i<Math.min(entries.size()+3,packOffset+visible);i++){
                int px=left+8+(i-packOffset)*30;
                if(i<2){
                    boolean favorite=i==1;
                    tile(g,px,toolbar,26,26,"",mx,my,()->{collection=favorite?"favorites":"recent";pickerScroll=0;});
                    ReactionIcons.draw(g,favorite,px,toolbar,26,26,UiTheme.TEXT);continue;
                }
                if(i==entries.size()+2){tile(g,px,toolbar,26,26,"+",mx,my,()->Minecraft.getInstance().gui.setScreen(new StickerShopScreen(screen)));continue;}
                var entry=entries.get(i-2);String id=entry.getKey();
                tile(g,px,toolbar,26,26,"",mx,my,()->{pack=id;collection="all";pickerScroll=0;});WebStickers.draw(g,entry.getValue(),px+2,toolbar+2,22);
            }
        }else{
            if(categoryIcons==null){
                String[] icons={"🙂","😀","👋","🐻","🍎","🚗","⚽","💡","🔣","🏳️"};
                categoryIcons=Arrays.stream(icons).map(icon->ChatReactions.ALL.stream().filter(r->r.unicode().equals(icon)).findFirst().orElse(ChatReactions.LEGACY.getFirst())).toList();
            }
            int pitch=Math.min(24,Math.max(1,(w-12)/(categoryIcons.size()+2))),button=Math.max(1,pitch-2);
            int start=left+(w-(pitch*(categoryIcons.size()+2)-2))/2;
            tile(g,start,toolbar,button,22,"",mx,my,()->{collection="recent";pickerScroll=0;});
            ReactionIcons.draw(g,false,start,toolbar,button,22,UiTheme.TEXT);
            tile(g,start+pitch,toolbar,button,22,"",mx,my,()->{collection="favorites";pickerScroll=0;});
            ReactionIcons.draw(g,true,start+pitch,toolbar,button,22,UiTheme.TEXT);
            for(int i=0;i<categoryIcons.size();i++){
                int category=i,x=start+(i+2)*pitch;
                tile(g,x,toolbar,button,22,"",mx,my,()->{emojiCategory=category;collection="all";pickerScroll=0;});
                ChatReactions.draw(g,categoryIcons.get(i),x+(button-14)/2,toolbar+4,14);
            }
        }
        var list=stickers?selectedStickers():selectedEmoji();int rows=Math.max(0,(toolbar-content-4)/size);
        pickerScroll=Math.clamp(pickerScroll,0,Math.max(0,(list.size()+columns-1)/columns-rows));
        for(int i=pickerScroll*columns;i<list.size()&&i<(pickerScroll+rows)*columns;i++){
            int x=left+(w-columns*size+2)/2+(i%columns)*size,y=content+(i/columns-pickerScroll)*size;
            Object value=list.get(i);
            if(value instanceof WebStickers.Sticker s){var hit=tile(g,x,y,size-2,size-2,"",mx,my,()->{screen.handleChatInput("[Стикер:"+s.id()+"]",true);ReactionHistory.get().used("sticker",s.id());picker=false;});favorites.put(hit,()->ReactionHistory.get().toggle("sticker",s.id()));WebStickers.draw(g,s,x+2,y+2,size-6);}
            else if(value instanceof ChatReactions.Reaction r){var hit=reactionTile(g,x,y,size-2,mx,my,()->{input.insertText(r.unicode());ReactionHistory.get().used("emoji",r.id());});favorites.put(hit,()->ReactionHistory.get().toggle("emoji",r.id()));ChatReactions.draw(g,r,x+3,y+3,size-8);}
        }
    }
    private List<WebStickers.Sticker> installedCatalog=List.of(),installedStickers=List.of();
    private List<WebStickers.Sticker> packSource=List.of();
    private Map<String,WebStickers.Sticker> packCache=Map.of();
    private Map<String,WebStickers.Sticker> stickerPacks(){
        var installed=selectedStickersForPacks();
        if(packSource!=installed){
            packSource=installed;var packs=new LinkedHashMap<String,WebStickers.Sticker>();
            installed.forEach(s->packs.putIfAbsent(s.id().substring(0,s.id().indexOf('/')),s));
            packCache=packs;
        }
        return packCache;
    }
    private long installedRevision=-1;
    private List<WebStickers.Sticker> selectedStickersForPacks(){
        var entries=WebStickers.entries();var history=ReactionHistory.get();long revision=history.revision();
        if(entries!=installedCatalog||revision!=installedRevision){
            installedCatalog=entries;installedRevision=revision;
            installedStickers=entries.stream().filter(s->history.installed(s.id().substring(0,s.id().indexOf('/')))).toList();
        }
        return installedStickers;
    }
    public boolean click(double x,double y,int button){
        if(button==0&&historyScrollbar()&&x>=left+w-8&&x<left+w&&y>=scrollTrackTop()&&y<bottom-2){
            var thumb=ChatScrollBounds.thumb(scrollTrackHeight(),maxHistoryScroll,Math.round(renderedHistoryScroll));
            double relative=y-scrollTrackTop();
            historyGrab=relative>=thumb.top()&&relative<thumb.top()+thumb.height()?relative-thumb.top():thumb.height()/2.0;
            historyDragging=true;dragHistory(y,button);return true;
        }
        pickerSearch.setFocused(false);
        if(button==1)for(var entry:favorites.entrySet())if(entry.getKey().contains(x,y)){entry.getValue().run();focusComposer();return true;}
        if(button==0){
            for(var hit:hits)if(hit.contains(x,y)){hit.action.run();focusComposer();return true;}
            if(!picker&&!settings&&x>=left&&x<left+w&&y>=historyTop()&&y<bottom){
                for(var hit:textHits)if(y>=hit.y&&y<hit.y+Minecraft.getInstance().font.lineHeight){
                    var style=styleAt(hit.text,(float)(x-hit.x));
                    if(style!=null&&style.getClickEvent()!=null){
                        clickEvent.accept(style.getClickEvent());
                        if(Minecraft.getInstance().gui.screen()==screen)focusComposer();
                        return true;
                    }
                }
            }
        }
        return x>=left&&x<left+w&&y>=historyTop()&&y<bottom;
    }
    private static net.minecraft.network.chat.Style styleAt(net.minecraft.util.FormattedCharSequence line,float x){
        if(x<0)return null;
        float[] cursor={0};
        net.minecraft.network.chat.Style[] result={null};
        var font=Minecraft.getInstance().font;
        line.accept((index,style,codepoint)->{
            float width=font.width(Component.literal(new String(Character.toChars(codepoint))).withStyle(style));
            if(x>=cursor[0]&&x<cursor[0]+width){result[0]=style;return false;}
            cursor[0]+=width;return true;
        });
        return result[0];
    }
    public boolean scroll(double x,double y,double amount){
        boolean outside=x<left||x>=left+w||y<historyTop()-4||y>=bottom;
        if(outside)return false;
        else if(settings){settingsScroll=Math.clamp(settingsScroll-(int)Math.round(amount*24),0,Math.max(0,settingsHeight()-(bottom-historyTop())));}
        else if(picker&&stickers&&y>=packStripTop){packOffset=Math.max(0,packOffset-(int)Math.signum(amount));}
        else if(picker){pickerScroll=Math.max(0,pickerScroll-(int)Math.signum(amount));}
        else{scroll=ChatScrollBounds.wheel(scroll,maxHistoryScroll,amount);}
        return !outside;
    }
}
