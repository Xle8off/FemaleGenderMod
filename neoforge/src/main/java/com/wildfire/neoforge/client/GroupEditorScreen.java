package com.wildfire.neoforge.client;
import com.wildfire.client.music.GroupInbox;
import com.wildfire.client.music.SpringPlusSession;

import com.wildfire.gui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Shared create/manage UI; roster operations are checked again by the authenticated proxy. */
public final class GroupEditorScreen extends Screen {
    private final Screen parent; private final String groupId; private final List<String> friends; private final Consumer<String> opened;
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    private EditBox name,search; private Button save,leave; private final List<Button> choices=new ArrayList<>();
    private Button rolesButton;
    private int left,panelWidth,offset; private String pending="",error="";private long sent;private boolean confirmLeave,leaving,rosterReady;
    public GroupEditorScreen(Screen parent,String groupId,List<String> friends,Consumer<String> opened) {
        super(Component.literal(groupId.isEmpty()?"Создать группу":"Участники группы"));this.parent=parent;this.groupId=groupId;this.friends=List.copyOf(friends);this.opened=opened;
        rosterReady=groupId.isEmpty(); loadRoster();
        if(!groupId.isEmpty())GroupInbox.history(groupId);
    }
    private String self(){var player=net.minecraft.client.Minecraft.getInstance().player;return player==null?"":player.getUUID().toString();}
    private boolean owner(){var group=GroupInbox.group(groupId);return groupId.isEmpty()||group!=null&&group.owner().equals(self());}
    private void loadRoster(){var group=GroupInbox.group(groupId);if(!rosterReady&&group!=null&&GroupInbox.members(groupId).size()==group.count()){for(var m:GroupInbox.members(groupId))if(!m.uuid().equals(self()))selected.add(m.name());rosterReady=true;}}
    @Override protected void init() {
        panelWidth=Math.min(360,width-24);left=(width-panelWidth)/2;choices.clear();
        String draft=name==null?(GroupInbox.group(groupId)==null?"":GroupInbox.group(groupId).title()):name.getValue();
        rolesButton=com.wildfire.gui.FlatButtons.builder(Component.literal("Роли"),b->minecraft.gui.setScreen(new GroupRolesScreen(this,groupId))).bounds(left+panelWidth-70,28,58,18).build();addRenderableWidget(rolesButton);
        name=new EditBox(font,left+12,48,panelWidth-24,20,Component.literal("Название группы"));name.setMaxLength(32);name.setHint(Component.literal("Название группы"));name.setValue(draft);name.setEditable(owner()||GroupInbox.allowed(groupId,4));addRenderableWidget(name);
        search=new EditBox(font,left+12,76,panelWidth-24,18,Component.literal("Поиск участников"));search.setMaxLength(16);search.setHint(Component.literal("Поиск по нику"));search.setResponder(v->offset=0);addRenderableWidget(search);
        for(int i=0;i<Math.max(1,(height-202)/22);i++){int slot=i;var button=com.wildfire.gui.FlatButtons.builder(Component.empty(),b->{var rows=rows();int index=offset+slot;if(index>=rows.size())return;String target=rows.get(index);if(!selected.remove(target)){if(selected.size()>=19){error="Максимум 20 участников вместе с вами";return;}selected.add(target);}}).bounds(left+12,102+i*22,panelWidth-24,20).build();choices.add(button);addRenderableWidget(button);}
        save=com.wildfire.gui.FlatButtons.builder(Component.literal(groupId.isEmpty()?"Создать группу":"Сохранить"),b->{
            if(name.getValue().isBlank()){error="Введите название группы";return;}
            if(groupId.isEmpty()&&selected.isEmpty()){error="Выберите хотя бы одного друга";return;}
            var fields=new LinkedHashMap<String,Object>();fields.put("title",name.getValue());fields.put("members",List.copyOf(selected));if(!groupId.isEmpty())fields.put("group",groupId);
            send(groupId.isEmpty()?"create":"edit",fields,false);
        }).bounds(left+12,height-70,panelWidth-24,20).build();addRenderableWidget(save);
        leave=com.wildfire.gui.FlatButtons.builder(Component.literal("Выйти из группы"),b->{if(!confirmLeave){confirmLeave=true;leave.setMessage(Component.literal("Точно выйти?"));return;}send("leave",Map.of("group",groupId),true);}).bounds(left+12,height-44,(panelWidth-28)/2,20).build();addRenderableWidget(leave);
        addRenderableWidget(com.wildfire.gui.FlatButtons.builder(Component.literal("Назад"),b->onClose()).bounds(left+panelWidth/2+2,height-44,(panelWidth-28)/2,20).build());
        setFocused(name);
    }
    private void send(String op,Map<String,?> fields,boolean leaving){pending=GroupInbox.request(op,fields);sent=System.currentTimeMillis();this.leaving=leaving;error=pending.isEmpty()?"Группы недоступны":"";}
    private List<String> rows(){var all=new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);all.addAll(friends);for(var m:GroupInbox.members(groupId))if(!m.uuid().equals(self()))all.add(m.name());all.addAll(selected);String q=search==null?"":search.getValue().toLowerCase(Locale.ROOT);return all.stream().filter(n->n.toLowerCase(Locale.ROOT).contains(q)).toList();}
    @Override public void tick(){
        if(!SpringPlusSession.enabled()){onClose();return;}
        loadRoster(); var rows=rows();offset=Math.clamp(offset,0,Math.max(0,rows.size()-choices.size()));
        rolesButton.visible=!groupId.isEmpty()&&owner();rolesButton.active=pending.isEmpty();
        name.setEditable(owner()||GroupInbox.allowed(groupId,4));
        for(int i=0;i<choices.size();i++){var b=choices.get(i);int index=offset+i;b.visible=index<rows.size();
            String target=b.visible?rows.get(index):"";var member=GroupInbox.members(groupId).stream().filter(m->m.name().equalsIgnoreCase(target)).findFirst();
            var role=member.map(m->GroupInbox.role(groupId,m.uuid()).role()).orElse("member");
            boolean canEdit=owner()||(selected.contains(target)?GroupInbox.allowed(groupId,2)&&role.equals("member"):GroupInbox.allowed(groupId,1));
            b.active=rosterReady&&canEdit&&pending.isEmpty();if(b.visible)b.setMessage(Component.literal((selected.contains(target)?"✓ ":"+ ")+target+(role.equals("admin")?" · Админ":role.equals("owner")?" · Владелец":"")));
        }
        save.visible=owner()||GroupInbox.allowed(groupId,1)||GroupInbox.allowed(groupId,2)||GroupInbox.allowed(groupId,4);save.active=rosterReady&&pending.isEmpty()&&GroupInbox.supported();leave.visible=!groupId.isEmpty();leave.active=pending.isEmpty();
        if(!pending.isEmpty()) {
            var result=GroupInbox.result(pending);
            if(result!=null){pending="";if(result.ok()){minecraft.gui.setScreen(parent);opened.accept(leaving?"":GroupInbox.key(result.group()));}else error=result.error();}
            else if(System.currentTimeMillis()-sent>10000){pending="";error="Нет ответа от прокси. Проверьте поддержку групп";}
        }
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){RoundedPanels.fill(g,left,24,left+panelWidth,height-16,UiTheme.BACKGROUND,6);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.text(font,getTitle(),left+12,32,UiTheme.TEXT,false);
        String subtitle=groupId.isEmpty()?"Выберите друзей · "+(selected.size()+1)+" / 20":owner()?"Владелец · состав группы и роли":"Участники · доступны действия по вашим правам";
        g.text(font,font.plainSubstrByWidth(subtitle,panelWidth-24),left+12,height-99,UiTheme.MUTED,false);
        String status=!pending.isEmpty()?"Сохраняем…":!error.isEmpty()?error:!GroupInbox.supported()?GroupInbox.status():"";
        g.text(font,font.plainSubstrByWidth(status,panelWidth-24),left+12,height-85,error.isEmpty()?UiTheme.MUTED:0xFFFF5264,false);
        super.extractRenderState(g,mx,my,delta);
        var visibleRows=rows();
        for(int i=0;i<choices.size()&&offset+i<visibleRows.size();i++)
            com.wildfire.gui.ModPresence.draw(g,visibleRows.get(offset+i),left+panelWidth-24,107+i*22,mx,my);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){if(x>=left&&x<left+panelWidth&&y>=102&&y<height-102){offset=Math.clamp(offset-(int)Math.signum(sy)*3,0,Math.max(0,rows().size()-choices.size()));return true;}return super.mouseScrolled(x,y,sx,sy);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
