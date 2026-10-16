package com.wildfire.gui;

import com.wildfire.client.towns.TownsClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Centred town-management window; all mutations are validated by Towns. */
public final class TownsScreen extends Screen {
    private final Screen parent;
    private int left,top,w,h,section,scroll,listTop,visibleRows;
    private String selected="";
    private boolean sentApplications;
    private TownsClient.Snapshot shown;
    private long refreshedAt;
    private List<TownRosterLayout.Row> roster=List.of();
    private int rosterX,rosterWidth,infoX,infoWidth;
    public TownsScreen(Screen parent){super(Component.literal("Города"));this.parent=parent;}
    private Button button(String label,int x,int y,int width,int height,Runnable action){
        return addRenderableWidget(FlatButtons.builder(Component.literal(label),b->action.run()).bounds(x,y,width,height).build());
    }
    private Button iconButton(String label,int icon,int x,int y,int width,int height,Runnable action){
        return addRenderableWidget(SettingsControls.category(label,icon,b->action.run()).bounds(x,y,width,height).build());
    }
    private void change(int next){section=next;scroll=0;selected="";rebuildWidgets();}
    private void mailButton(int x,int y){
        var mail=new Button(x,y,26,22,Component.literal("Сообщения города"),b->change(2),message->message.get()){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
                FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),true,isHoveredOrFocused()||!active,false,UiTheme.ACCENT);
                int px=getX()+5,py=getY()+5;
                // A paper envelope, not a font glyph: centred inside the compact button.
                g.fill(px,py,px+16,py+12,0xFF71829E);
                g.fill(px+1,py+1,px+15,py+11,0xFFF0E9DB);
                for(int i=0;i<7;i++){
                    g.fill(px+1+i,py+1+i/2,px+2+i,py+2+i/2,0xFF8C9BB4);
                    g.fill(px+14-i,py+1+i/2,px+15-i,py+2+i/2,0xFF8C9BB4);
                }
                g.fill(px+7,py+4,px+9,py+5,0xFF8C9BB4);
            }
        };
        mail.active=section!=2;addRenderableWidget(mail);
    }
    @Override protected void init(){
        w=Math.min(width-16,Math.max(420,width/2));h=Math.min(height-16,Math.max(240,height*2/3));
        left=(width-w)/2;top=(height-h)/2;shown=TownsClient.snapshot();
        addRenderableWidget(new Button(left+w-30,top+8,22,20,Component.literal("Закрыть"),b->onClose(),message->message.get()){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
                FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),true,isHoveredOrFocused(),false,UiTheme.ACCENT);
                int cx=getX()+(getWidth()-8)/2,cy=getY()+(getHeight()-8)/2;
                for(int i=0;i<8;i++){g.fill(cx+i,cy+i,cx+i+1,cy+i+1,UiTheme.TEXT);g.fill(cx+7-i,cy+i,cx+8-i,cy+i+1,UiTheme.TEXT);}
            }
        });
        int cell=(w-58)/2;
        String[] titles={"Мой город","Города"};
        for(int i=0;i<2;i++){final int next=i;var b=addRenderableWidget(SettingsControls.category(titles[i],4,ignored->change(next)).bounds(left+12+i*(cell+4),top+38,cell,22).build());b.active=section!=i;}
        mailButton(left+w-38,top+38);
        if(shown==null){button("Обновить",left+12,top+h-30,w-24,20,TownsClient::refresh);return;}
        if(section==0){rosterControls();return;}
        if(section==1||section==2){cardControls();return;}
        if(section==7){managementControls();return;}
        if(section==5){
            button("Отмена",left+12,top+h-34,(w-28)/2,24,()->change(0));
            button("Подтвердить",left+16+(w-28)/2,top+h-34,(w-28)/2,24,()->{
                TownsClient.action(shown.founder()?10:5,"",shown.founder()?"confirm":"");change(0);
            });return;
        }
        if(section==3){memberControls();return;}
        listTop=top+(section==0?138:70);
        visibleRows=Math.max(1,(top+h-42-listTop)/38);
        int count=count();scroll=Math.clamp(scroll,0,Math.max(0,count-visibleRows));
        for(int i=0;i<visibleRows&&scroll+i<count;i++){
            int index=scroll+i,y=listTop+i*38;
            if(section==0){
                var resident=shown.residents().get(index);
                var b=button(resident.name()+" · "+resident.role(),left+12,y,w-24,32,()->{selected=resident.id();section=3;rebuildWidgets();});
                boolean self=minecraft.player!=null&&resident.id().equals(minecraft.player.getUUID().toString());
                b.active=!resident.id().isEmpty()&&!self&&!resident.role().equals("Глава города")&&
                    (shown.canRole()||shown.canKick()&&!resident.role().equals("Заместитель главы"));
            }else if(section==1){
                var city=shown.cities().get(index);
                var b=button(city.name()+" · "+city.population()+" / "+city.capacity(),left+12,y,w-24,32,()->{});b.active=false;
            }else if(section==2){
                var city=shown.invitations().get(index);
                button(city.name(),left+12,y,w-142,32,()->{}).active=false;
                button("Принять",left+w-126,y,66,32,()->TownsClient.action(3,city.id()));
                button("×",left+w-56,y,44,32,()->TownsClient.action(4,city.id()));
            }else if(section==4){
                var candidate=shown.candidates().get(index);
                button("Пригласить · "+candidate.name(),left+12,y,w-24,32,()->TownsClient.action(6,candidate.id()));
            }else if(section==6){
                var candidate=shown.applications().get(index);
                button(candidate.name(),left+12,y,w-172,32,()->{}).active=false;
                button("Принять",left+w-154,y,82,32,()->TownsClient.action(12,candidate.id()));
                button("✕",left+w-66,y,54,32,()->TownsClient.action(13,candidate.id()));
            }
        }
        if(section==0&&shown.own()!=null){
            int cellWidth=(w-28)/2;
            if(shown.canInvite())button("Пригласить жителя",left+12,top+h-30,cellWidth,20,()->change(4));
            button(shown.founder()?"Распустить город":"Покинуть город",left+16+cellWidth,top+h-30,cellWidth,20,()->change(5));
        }else if(section==4||section==6)button("Назад к жителям",left+12,top+h-30,w-24,20,()->change(0));
        else button("Обновить",left+12,top+h-30,w-24,20,TownsClient::refresh);
    }
    private void rosterControls(){
        roster=TownRosterLayout.rows(shown.residents());
        rosterWidth=(w-36)*3/5;infoWidth=w-36-rosterWidth;infoX=left+12;rosterX=infoX+infoWidth+12;
        listTop=top+76;visibleRows=Math.max(1,(h-118)/30);
        scroll=Math.clamp(scroll,0,Math.max(0,roster.size()-visibleRows));
        for(int i=0;i<visibleRows&&scroll+i<roster.size();i++){
            var row=roster.get(scroll+i);var resident=row.resident();if(resident==null)continue;
            var b=button("",rosterX,listTop+i*30,rosterWidth-8,28,()->{selected=resident.id();section=3;rebuildWidgets();});
            boolean self=minecraft.player!=null&&resident.id().equals(minecraft.player.getUUID().toString());
            b.active=!resident.id().isEmpty()&&!self;
            var actions=addRenderableWidget(new Button(rosterX+rosterWidth-31,listTop+i*30+4,20,20,Component.literal("Действия с игроком"),ignored->{selected=resident.id();section=3;rebuildWidgets();},message->message.get()){
                @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
                    FlatButtons.plate(g,getX(),getY(),20,20,true,isHoveredOrFocused(),false,UiTheme.ACCENT);
                    for(int dot=0;dot<3;dot++)g.fill(getX()+9,getY()+4+dot*5,getX()+11,getY()+6+dot*5,UiTheme.TEXT);
                }
            });
            actions.active=b.active;
            actions.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Действия с игроком")));
        }
        if(shown.own()!=null){
            iconButton("Управление",3,infoX,top+h-38,infoWidth,22,()->change(7));
        }
        button("Обновить",rosterX,top+h-32,rosterWidth-8,20,TownsClient::refresh);
    }
    private void managementControls(){
        int y=top+74,cell=(w-28)/2;
        if(shown.canInvite())iconButton("Пригласить жителя",1,left+12,y,cell,24,()->change(4));
        if(shown.canInvite()&&shown.protocol()>=3)iconButton("Заявки · "+shown.applications().size(),2,left+16+cell,y,cell,24,()->change(6));
        if(shown.founder()&&shown.protocol()>=3){
            iconButton("Изменить аватарку",2,left+12,y+32,cell,24,()->artwork(14));
            iconButton("Изменить шапку",2,left+16+cell,y+32,cell,24,()->artwork(15));
            button("Убрать аватарку",left+12,y+64,cell,24,()->TownsClient.action(14,"",""));
            button("Убрать шапку",left+16+cell,y+64,cell,24,()->TownsClient.action(15,"",""));
        }
        button(shown.founder()?"Распустить город":"Покинуть город",left+12,top+h-62,w-24,24,()->change(5));
        button("Назад к жителям",left+12,top+h-30,w-24,20,()->change(0));
    }
    private void artwork(int action){
        String cityId=shown.own()==null?"":shown.own().id();
        ChatImages.choose(this,false,TownsClient::requestImageTicket,url->{
            var current=TownsClient.snapshot();String id=ChatImages.imageId(url);
            if(id!=null&&current!=null&&current.own()!=null&&current.own().id().equals(cityId)&&current.founder())TownsClient.action(action,"",id);
        });
    }
    private List<TownsClient.City> cardCities(){return section==2?(sentApplications?TownsClient.pendingCities(shown):shown.invitations()):shown.cities();}
    private int cardSize(){return Math.max(1,(w-40)/3);}
    private int cardBottom(){return top+h-48;}
    private int cardMaxScroll(){return Math.max(0,((count()+2)/3)*(cardSize()+8)-8-(cardBottom()-listTop));}
    private void cardControls(){
        listTop=top+70;visibleRows=3*Math.max(1,(cardBottom()-listTop)/(cardSize()+8));
        if(section==2){
            int tabWidth=(w-28)/2;
            var invites=button("Приглашения · "+shown.invitations().size(),left+12,top+68,tabWidth,22,
                ()->{sentApplications=false;scroll=0;rebuildWidgets();});
            invites.active=sentApplications;
            var sent=button("Мои заявки · "+shown.requestedTowns().size(),left+16+tabWidth,top+68,tabWidth,22,
                ()->{sentApplications=true;scroll=0;rebuildWidgets();});
            sent.active=!sentApplications;
            listTop=top+98;
        }
        scroll=Math.clamp(scroll,0,cardMaxScroll());
        var cities=cardCities();int size=cardSize();
        for(int i=0;i<cities.size();i++){
            var city=cities.get(i);int x=left+12+(i%3)*(size+8),y=listTop+(i/3)*(size+8)-scroll;
            int by=y+size-26;
            if(by<listTop||by+20>cardBottom())continue;
            if(section==2){
                if(sentApplications){
                    var cancel=button("Отменить заявку",x+6,by,size-12,20,()->cancelApplication(city));
                    cancel.active=shown.protocol()>=4;
                    if(!cancel.active)cancel.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Для отмены обновите плагин Towns")));
                }else{
                    button("Принять",x+6,by,size-40,20,()->TownsClient.action(3,city.id()));
                    button("✕",x+size-30,by,24,20,()->TownsClient.action(4,city.id()));
                }
            }else{
                boolean own=shown.own()!=null&&shown.own().id().equals(city.id()),pending=shown.requestedTowns().contains(city.id());
                String label=own?"Управление":pending?"Отправлена":"Подать заявку";
                var b=button(label,x+6,by,size-12,20,()->{if(own)change(0);else TownsClient.action(11,city.id());});
                b.active=own||shown.protocol()>=3&&shown.passport()&&shown.own()==null&&!pending&&city.population()<city.capacity();
            }
        }
        button("Обновить",left+12,top+h-30,w-24,20,TownsClient::refresh);
    }
    private void cancelApplication(TownsClient.City city){
        minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ok->{
            minecraft.gui.setScreen(this);
            if(ok)TownsClient.action(16,city.id());
        },Component.literal("Отменить заявку?"),Component.literal(city.name()),
          Component.literal("Отменить заявку"),Component.literal("Назад")));
    }
    private void renderCards(GuiGraphicsExtractor g){
        var cities=cardCities();int size=cardSize();
        g.enableScissor(left+12,listTop,left+w-10,cardBottom());
        for(int i=0;i<cities.size();i++){
            var city=cities.get(i);int x=left+12+(i%3)*(size+8),y=listTop+(i/3)*(size+8)-scroll;
            if(y+size<=listTop||y>=cardBottom())continue;
            FlatButtons.plate(g,x,y,size,size,true,false,false,UiTheme.ACCENT);
            int cover=Math.max(18,size/4),avatar=Math.min(24,cover);
            g.fill(x+2,y+2,x+size-2,y+cover,0xFF292E43);
            if(!city.cover().isEmpty())ChatImages.drawCover(g,city.cover(),x+2,y+2,size-4,cover-2);
            FlatButtons.plate(g,x+6,y+cover-avatar/2,avatar,avatar,true,false,false,UiTheme.ACCENT);
            TownsPanel.icon(g,x+8,y+cover-avatar/2+2);
            if(!city.avatar().isEmpty())ChatImages.drawCover(g,city.avatar(),x+8,y+cover-avatar/2+2,avatar-4,avatar-4);
            int ty=y+cover+avatar/2+4;
            text(g,city.name(),x+6,ty,size-12,UiTheme.TEXT);
            text(g,city.type()+(city.capital()?" · Столица":""),x+6,ty+11,size-12,UiTheme.ACCENT);
            text(g,section==2&&sentApplications?"Ожидает рассмотрения":"Жители: "+city.population()+"/"+city.capacity(),x+6,ty+22,size-12,UiTheme.MUTED);
            if(city.cityChunks()>=0){
                text(g,"Чанки: "+city.cityChunks()+"/"+city.cityLimit(),x+6,ty+33,size-12,UiTheme.MUTED);
                if(ty+53<y+size-28)text(g,"Фермы: "+city.farmChunks()+"/"+city.farmLimit(),x+6,ty+44,size-12,UiTheme.MUTED);
            }
        }
        g.disableScissor();
    }
    private void renderRoster(GuiGraphicsExtractor g){
        for(int i=0;i<visibleRows&&scroll+i<roster.size();i++){
            var row=roster.get(scroll+i);int y=listTop+i*30;
            if(row.resident()==null){
                int color=row.role().equals("Глава города")?0xFFFFBF45:UiTheme.ACCENT;
                text(g,row.role()+" · "+row.members(),rosterX+4,y+6,rosterWidth-16,color);
                g.fill(rosterX+4,y+22,rosterX+rosterWidth-12,y+23,UiTheme.BORDER);continue;
            }
            var resident=row.resident();var face=ChatAvatars.texture(resident.name());
            if(face!=null)ChatAvatars.drawTexture(g,face,rosterX+5,y+4,20);
            else FlatButtons.plate(g,rosterX+5,y+4,20,20,true,false,false,UiTheme.BORDER);
            text(g,resident.name(),rosterX+31,y+5,rosterWidth-66,UiTheme.TEXT);
            text(g,resident.online()?"В сети":LastSeenLabel.format(resident.lastSeen(),System.currentTimeMillis(),java.time.ZoneId.systemDefault(),resident.name()),rosterX+31,y+16,rosterWidth-66,resident.online()?0xFF59CF8B:UiTheme.MUTED);
            g.fill(rosterX+21,y+20,rosterX+26,y+25,resident.online()?0xFF59CF8B:0xFF777F8D);
        }
    }
    private void memberControls(){
        var resident=shown.residents().stream().filter(r->r.id().equals(selected)).findFirst().orElse(null);
        if(resident==null){change(0);return;}
        int socialWidth=(w-28)/2;
        iconButton("Написать сообщение",2,left+12,top+110,socialWidth,24,()->minecraft.gui.setScreen(new com.wildfire.gui.screen.NotificationCenterScreen(parent,resident.name())));
        boolean mutable=!resident.role().equals("Глава города");
        iconButton("Добавить в друзья",1,left+16+socialWidth,top+110,socialWidth,24,()->{
            com.wildfire.main.networking.appearance.SocialClient.action("add",resident.name());
        });
        if(shown.canRole()&&mutable){
            String[] roles={"citizen","advisor","minister","deputy"};
            int rank=switch(resident.role()){case "Гражданин"->0;case "Советник"->1;case "Министр"->2;case "Заместитель главы"->3;default->-1;};
            int cell=(w-28)/2;
            var up=button("Повысить",left+12,top+142,cell,24,()->TownsClient.action(8,selected,roles[Math.min(3,rank+1)]));
            var down=button("Понизить",left+16+cell,top+142,cell,24,()->TownsClient.action(8,selected,roles[Math.max(0,rank-1)]));
            up.active=rank>=0&&rank<3;down.active=rank>0;
        }
        if(shown.canKick()&&mutable&&(shown.founder()||!resident.role().equals("Заместитель главы")))button(selected.equals(confirmTarget)&&System.currentTimeMillis()<confirmUntil?"Подтвердить исключение":"Исключить жителя",left+12,top+h-62,w-24,24,()->{
            if(selected.equals(confirmTarget)&&System.currentTimeMillis()<confirmUntil){TownsClient.action(7,selected);confirmTarget="";change(0);}
            else{confirmTarget=selected;confirmUntil=System.currentTimeMillis()+5000;rebuildWidgets();}
        });
        button("Назад к жителям",left+12,top+h-30,w-24,20,()->change(0));
    }
    private String confirmTarget="";private long confirmUntil;
    private int count(){return shown==null?0:switch(section){case 0->roster.size();case 1->shown.cities().size();case 2->cardCities().size();case 4->shown.candidates().size();case 6->shown.applications().size();default->0;};}
    private void text(GuiGraphicsExtractor g,String value,int x,int y,int width,int color){g.text(font,font.plainSubstrByWidth(value,Math.max(0,width)),x,y,color,false);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xA0000000);FlatButtons.plate(g,left,top,w,h,true,false,false,UiTheme.ACCENT);
        int titleX=left+(w-font.width("Города")-28)/2;
        TownsPanel.icon(g,titleX,top+9);g.text(font,"Города",titleX+28,top+15,UiTheme.TEXT,false);
        g.fill(left+8,top+32,left+w-8,top+33,UiTheme.BORDER);
        if(shown==null){int y=top+76;for(var line:font.split(Component.literal(TownsClient.status()),w-24)){g.text(font,line,left+12,y,UiTheme.MUTED,false);y+=12;}}
        else if(section==0){
            FlatButtons.plate(g,infoX,top+68,infoWidth,120,true,false,false,UiTheme.ACCENT);
            g.fill(rosterX-7,top+68,rosterX-6,top+h-12,UiTheme.BORDER);
            if(shown.own()!=null){
                var city=shown.own();
                if(!city.cover().isEmpty())ChatImages.drawCover(g,city.cover(),infoX+2,top+70,infoWidth-4,25);
                FlatButtons.plate(g,infoX+8,top+80,24,24,true,false,false,UiTheme.ACCENT);TownsPanel.icon(g,infoX+10,top+82);
                if(!city.avatar().isEmpty())ChatImages.drawCover(g,city.avatar(),infoX+10,top+82,20,20);
                text(g,city.name(),infoX+36,top+98,infoWidth-44,UiTheme.TEXT);
                text(g,city.type(),infoX+8,top+114,infoWidth-16,UiTheme.MUTED);
                text(g,"Жители: "+city.population()+" / "+city.capacity(),infoX+8,top+128,infoWidth-16,UiTheme.MUTED);
                if(city.cityChunks()>=0){
                    text(g,"Город: "+city.cityChunks()+" / "+city.cityLimit(),infoX+8,top+142,infoWidth-16,UiTheme.MUTED);
                    text(g,"Фермы: "+city.farmChunks()+" / "+city.farmLimit(),infoX+8,top+156,infoWidth-16,UiTheme.MUTED);
                }
                text(g,shown.role(),infoX+8,top+172,infoWidth-16,UiTheme.ACCENT);
            }else{
                int y=top+78;
                for(var line:font.split(Component.literal(shown.passport()?"Вы пока не состоите в городе. Вступить можно по приглашению.":"Сначала оформите паспорт через /passport"),infoWidth-16)){g.text(font,line,infoX+8,y,UiTheme.MUTED,false);y+=12;}
            }
        }else if(section==3){
            var member=shown.residents().stream().filter(r->r.id().equals(selected)).findFirst().orElse(null);
            if(member!=null){text(g,member.name()+" · "+member.role(),left+12,top+76,w-24,UiTheme.TEXT);text(g,member.online()?"В сети":"Не в сети",left+12,top+94,w-24,UiTheme.MUTED);}
        }else if(section==5){
            text(g,shown.founder()?"Распустить город навсегда?":"Покинуть город?",left+12,top+82,w-24,UiTheme.TEXT);
            text(g,shown.founder()?"Все жители будут исключены. Отменить нельзя.":"Для возвращения понадобится приглашение.",left+12,top+104,w-24,UiTheme.MUTED);
        }
        if(shown!=null&&section!=3&&section!=5&&count()==0&&listTop>0)text(g,section==2?(sentApplications?"Отправленных заявок нет":"Приглашений пока нет"):section==4?"Нет доступных игроков":section==1?"Городов пока нет":"",left+12,listTop+8,w-24,UiTheme.MUTED);
        if(shown!=null&&!shown.feedback().isEmpty())text(g,shown.feedback(),left+12,top+h-44,w-24,0xFFFFBF45);
        if((section==1||section==2)&&shown!=null){
            renderCards(g);
            int max=cardMaxScroll(),track=cardBottom()-listTop;
            if(max>0){int thumb=Math.max(10,track*track/(track+max)),y=listTop+(track-thumb)*scroll/max;g.fill(left+w-7,y,left+w-5,y+thumb,UiTheme.ACCENT);}
        }else if(count()>visibleRows&&section!=3&&section!=5){int track=section==0?visibleRows*30:top+h-42-listTop,thumb=Math.max(10,track*visibleRows/count());int y=listTop+(track-thumb)*scroll/Math.max(1,count()-visibleRows),x=section==0?rosterX+rosterWidth-2:left+w-7;g.fill(x,y,x+2,y+thumb,UiTheme.ACCENT);}
        super.extractRenderState(g,mx,my,delta);
        if(shown!=null&&section==0)renderRoster(g);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
        if(shown!=null&&(section==1||section==2)&&x>=left&&x<left+w&&y>=listTop&&y<cardBottom()){
            scroll=Math.clamp(scroll-(int)(sy*28),0,cardMaxScroll());rebuildWidgets();return true;
        }
        if(x>=(section==0?rosterX:left)&&x<left+w&&y>=listTop&&y<top+h-42&&section!=3&&section!=5){scroll=Math.clamp(scroll-(int)Math.signum(sy),0,Math.max(0,count()-visibleRows));rebuildWidgets();return true;}
        return super.mouseScrolled(x,y,sx,sy);
    }
    @Override public void tick(){
        if(minecraft.player==null){minecraft.gui.setScreen(null);return;}
        if(System.currentTimeMillis()-refreshedAt>1000){refreshedAt=System.currentTimeMillis();TownsClient.refresh();}
        if(TownsClient.snapshot()!=shown)rebuildWidgets();
        if(!confirmTarget.isEmpty()&&System.currentTimeMillis()>=confirmUntil){confirmTarget="";rebuildWidgets();}
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
