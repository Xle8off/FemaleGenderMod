package com.wildfire.client.music;


import com.wildfire.gui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Owner-only per-member admin permissions. Server authorization is authoritative. */
public final class GroupRolesScreen extends Screen {
    private final Screen parent;private final String group;
    private Button targetButton,roleButton,save;private final List<Button> rightsButtons=new ArrayList<>();
    private int index,rights,left,panelWidth;private boolean admin;private String selected="",pending="",error="";private long sent;
    private static final String[] LABELS={"Добавлять участников","Исключать участников","Менять название","Удалять сообщения"};
    public GroupRolesScreen(Screen parent,String group){super(Component.literal("Роли и права"));this.parent=parent;this.group=group;GroupInbox.history(group);}
    private List<GroupInbox.Member> targets(){var g=GroupInbox.group(group);return GroupInbox.members(group).stream().filter(m->g!=null&&!m.uuid().equals(g.owner())).toList();}
    private void select(){var targets=targets();if(targets.isEmpty()){selected="";return;}index=Math.floorMod(index,targets.size());selected=targets.get(index).uuid();var role=GroupInbox.role(group,selected);admin=role.role().equals("admin");rights=role.permissions();}
    @Override protected void init(){
        panelWidth=Math.min(340,width-24);left=(width-panelWidth)/2;rightsButtons.clear();
        int step=Math.clamp((height-95)/7,16,24),buttonHeight=Math.min(20,step-2);
        targetButton=com.wildfire.gui.FlatButtons.builder(Component.empty(),b->{index++;select();}).bounds(left+12,48,panelWidth-24,buttonHeight).build();addRenderableWidget(targetButton);
        roleButton=com.wildfire.gui.FlatButtons.builder(Component.empty(),b->{admin=!admin;if(!admin)rights=0;}).bounds(left+12,48+step,panelWidth-24,buttonHeight).build();addRenderableWidget(roleButton);
        for(int i=0;i<4;i++){int bit=1<<i;var b=com.wildfire.gui.FlatButtons.builder(Component.empty(),button->rights^=bit).bounds(left+12,48+(i+2)*step,panelWidth-24,buttonHeight).build();rightsButtons.add(b);addRenderableWidget(b);}
        save=com.wildfire.gui.FlatButtons.builder(Component.literal("Сохранить права"),b->{pending=GroupInbox.request("admin",Map.of("group",group,"target",selected,"enabled",admin,"permissions",rights));sent=System.currentTimeMillis();if(pending.isEmpty())error="Не удалось отправить запрос";}).bounds(left+12,48+6*step,panelWidth-24,buttonHeight).build();addRenderableWidget(save);
        addRenderableWidget(com.wildfire.gui.FlatButtons.builder(Component.literal("Назад"),b->onClose()).bounds(left+12,height-40,panelWidth-24,20).build());
        if(selected.isEmpty())select();tick();
    }
    @Override public void tick(){
        var g=GroupInbox.group(group);var p=net.minecraft.client.Minecraft.getInstance().player;
        if(!SpringPlusSession.enabled()||g==null||p==null||!g.owner().equals(p.getUUID().toString())){onClose();return;}
        var targets=targets();if(!targets.isEmpty()&&targets.stream().noneMatch(m->m.uuid().equals(selected)))select();
        boolean ready=pending.isEmpty()&&!selected.isEmpty();
        String name=targets.stream().filter(m->m.uuid().equals(selected)).map(GroupInbox.Member::name).findFirst().orElse("Загружаем участников…");
        targetButton.setMessage(Component.literal(name+"  ›"));targetButton.active=ready;
        roleButton.setMessage(Component.literal("Роль: "+(admin?"Админ":"Участник")));roleButton.active=ready;
        for(int i=0;i<4;i++){var b=rightsButtons.get(i);b.setMessage(Component.literal(((rights&(1<<i))!=0?"✓ ":"— ")+LABELS[i]));b.active=ready&&admin;}
        save.active=ready;
        if(!pending.isEmpty()){var result=GroupInbox.result(pending);if(result!=null){pending="";error=result.ok()?"Права сохранены":result.error();if(result.ok())GroupInbox.history(group);}else if(System.currentTimeMillis()-sent>10000){pending="";error="Нет ответа от прокси";}}
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){RoundedPanels.fill(g,left,24,left+panelWidth,height-14,UiTheme.BACKGROUND,6);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.text(font,getTitle(),left+12,33,UiTheme.TEXT,false);
        g.text(font,font.plainSubstrByWidth(pending.isEmpty()?error:"Сохраняем…",panelWidth-24),left+12,height-54,UiTheme.MUTED,false);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
