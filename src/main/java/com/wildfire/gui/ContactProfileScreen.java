package com.wildfire.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A contact card; actions use the existing server-authorized social/moderation APIs. */
public final class ContactProfileScreen extends Screen {
    public interface Portrait { void draw(GuiGraphicsExtractor graphics, int x, int y); }
    private final Screen parent;
    private final String name;
    private final Supplier<String> presence, friendLabel;
    private final BooleanSupplier friendEnabled;
    private final Runnable friend;
    private final java.util.function.Consumer<Screen> moderate;
    private final Portrait portrait;
    private net.minecraft.client.gui.components.Button friendButton;
    public ContactProfileScreen(Screen parent, String name, Supplier<String> presence,
            Supplier<String> friendLabel, BooleanSupplier friendEnabled, Runnable friend,
            java.util.function.Consumer<Screen> moderate, Portrait portrait) {
        super(Component.literal("Профиль игрока"));
        this.parent=parent;this.name=name;this.presence=presence;this.friendLabel=friendLabel;
        this.friendEnabled=friendEnabled;this.friend=friend;this.moderate=moderate;this.portrait=portrait;
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override protected void init(){
        int w=Math.min(260,width-24),x=(width-w)/2,y=(height-166)/2;
        addRenderableWidget(FlatButtons.builder(Component.literal("← Назад"),b->onClose()).bounds(x+12,y+132,w-24,22).build());
        friendButton=addRenderableWidget(FlatButtons.builder(Component.literal(friendLabel.get()),b->{friend.run();b.active=false;}).bounds(x+12,y+76,w-24,22).build());
        friendButton.active=friendEnabled.getAsBoolean();
        if(moderate!=null)addRenderableWidget(FlatButtons.builder(Component.literal("Модерация игрока →"),b->moderate.accept(this)).bounds(x+12,y+104,w-24,22).build());
    }
    @Override public void tick(){
        if(minecraft.getConnection()==null){minecraft.gui.setScreen(null);return;}
        if(friendButton!=null){friendButton.setMessage(Component.literal(friendLabel.get()));friendButton.active=friendEnabled.getAsBoolean();}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        int w=Math.min(260,width-24),x=(width-w)/2,y=(height-166)/2;
        FlatButtons.backdrop(g,x,y,x+w,y+166,UiTheme.BACKGROUND,6);
        FlatButtons.frame(g,x,y,x+w,y+166,UiTheme.ACCENT,6);
        portrait.draw(g,x+14,y+20);
        g.text(font,name,x+48,y+22,UiTheme.TEXT,false);
        g.text(font,font.plainSubstrByWidth(presence.get(),Math.max(1,w-60)),x+48,y+39,UiTheme.MUTED,false);
        super.extractRenderState(g,mx,my,delta);
    }
}
