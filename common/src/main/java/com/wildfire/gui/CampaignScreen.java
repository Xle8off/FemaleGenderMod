package com.wildfire.gui;

import com.wildfire.client.music.CampaignClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Admin controls and ballots share a compact modal, not a chat-command interface. */
public final class CampaignScreen extends Screen {
    private final Screen parent;
    private int left,top,w,h,mode,index,scroll,listTop,listBottom,maxScroll;
    private boolean kindVote=true,multiple,keep=true;
    private String titleDraft="",textDraft="",durationDraft="60",nickDraft="";
    private EditBox title,duration,nick;
    private MultiLineEditBox text;
    private final List<String> optionDrafts=new ArrayList<>(List.of("",""));
    private final List<EditBox> optionFields=new ArrayList<>();
    private final Set<Integer> selected=new LinkedHashSet<>();
    private String rendered="",localError="",selectionFor="";
    private final List<AbstractWidget> formWidgets=new ArrayList<>();
    public CampaignScreen(Screen parent,boolean admin){super(Component.literal(admin?"Администрирование":"Голосования"));this.parent=parent;mode=admin?1:0;}
    private Button button(String label,int x,int y,int width,int height,Runnable action){
        return addRenderableWidget(FlatButtons.builder(Component.literal(label),b->action.run()).bounds(x,y,width,height).build());
    }
    private void capture(){
        if(title!=null)titleDraft=title.getValue();if(text!=null)textDraft=text.getValue();
        if(!optionFields.isEmpty()){
            optionDrafts.clear();
            optionFields.forEach(field->optionDrafts.add(field.getValue()));
        }
        if(duration!=null)durationDraft=duration.getValue();if(nick!=null)nickDraft=nick.getValue();
    }
    private void change(int next){capture();mode=next;index=scroll=0;selected.clear();localError="";rebuildWidgets();}
    private List<CampaignClient.Poll> rows(){return mode==2?CampaignClient.history():CampaignClient.active();}
    private CampaignClient.Poll current(){var list=rows();return list.isEmpty()?null:list.get(Math.clamp(index,0,list.size()-1));}
    private String signature(){
        return CampaignClient.admin()+":"+CampaignClient.pending()+":"+mode+":"+index+":"+rows().stream()
            .map(p->p.id()+"/"+p.submitted()+"/"+p.closed()+(mode==2?"/"+p.counts()+"/"+p.totalVoters():"")).toList();
    }
    @Override protected void init(){
        w=Math.min(620,width-16);h=Math.min(340,height-16);left=(width-w)/2;top=(height-h)/2;
        formWidgets.clear();optionFields.clear();title=null;text=null;duration=null;nick=null;
        if(mode!=0&&!CampaignClient.admin())mode=0;
        button("✕",left+w-29,top+7,21,20,this::onClose);
        int count=CampaignClient.admin()?3:1,cell=(w-24)/count;
        String[] labels={"Голосования","Создать","Результаты"};
        for(int i=0;i<count;i++){final int next=i;button(labels[i],left+12+i*cell,top+32,cell-3,20,()->change(next)).active=mode!=i;}
        if(mode==1)form();else ballot();
        rendered=signature();
    }
    private EditBox field(String placeholder,String value,int y,int limit,int width){
        var box=new EditBox(font,left+16,y,width,18,Component.literal(placeholder));
        box.setMaxLength(limit);box.setValue(value);addRenderableWidget(box);formWidgets.add(box);return box;
    }
    private void addOption(){
        capture();
        if(optionDrafts.size()<8)optionDrafts.add("");
        rebuildWidgets();
    }
    private void removeOption(int option){
        capture();
        if(optionDrafts.size()>2&&option>=0&&option<optionDrafts.size())optionDrafts.remove(option);
        rebuildWidgets();
    }
    private void form(){
        listTop=top+64;listBottom=top+h-58;
        int contentHeight=kindVote?194+optionDrafts.size()*22:200;
        maxScroll=Math.max(0,contentHeight-(listBottom-listTop));scroll=Math.clamp(scroll,0,maxScroll);
        int y=listTop-scroll;
        formWidgets.add(button(kindVote?"Тип: голосование":"Тип: текст",left+16,y,w-32,20,()->{capture();kindVote=!kindVote;scroll=0;rebuildWidgets();}));
        title=field("Заголовок",titleDraft,y+42,80,w-32);
        text=MultiLineEditBox.builder().setX(left+16).setY(y+80).setPlaceholder(Component.literal("Текст уведомления"))
            .build(font,w-32,36,Component.literal("Текст"));
        text.setCharacterLimit(512);text.setValue(textDraft);addRenderableWidget(text);formWidgets.add(text);
        if(kindVote){
            for(int i=0;i<optionDrafts.size();i++){
                final int option=i;int optionY=y+136+i*22;
                optionFields.add(field("Вариант "+(i+1),optionDrafts.get(i),optionY,60,w-58));
                var remove=button("−",left+w-38,optionY,22,18,()->removeOption(option));remove.active=optionDrafts.size()>2;formWidgets.add(remove);
            }
            int addY=y+136+optionDrafts.size()*22;
            var add=button(optionDrafts.size()<8?"+ Добавить вариант":"Максимум 8 вариантов",left+16,addY,w-32,18,this::addOption);add.active=optionDrafts.size()<8;formWidgets.add(add);
            int controlsY=addY+40;
            duration=field("Минуты",durationDraft,controlsY,5,(w-40)/2);
            formWidgets.add(button(multiple?"Несколько ответов":"Один ответ",left+24+(w-40)/2,controlsY,(w-40)/2,18,
                ()->{capture();multiple=!multiple;rebuildWidgets();}));
        }else{
            nick=field("Ник получателя",nickDraft,y+136,16,w-32);
            formWidgets.add(button(keep?"Отложенная доставка: да":"Отложенная доставка: нет",left+16,y+168,w-32,20,
                ()->{capture();keep=!keep;rebuildWidgets();}));
        }
        for(var widget:formWidgets)widget.visible=widget.getY()>=listTop&&widget.getY()+widget.getHeight()<=listBottom;
        button("Отправить",left+16,top+h-29,w-32,21,this::submit).active=!CampaignClient.pending();
    }
    private void submit(){
        capture();localError="";
        if(titleDraft.isBlank()||(!kindVote&&textDraft.isBlank())){localError="Заполните заголовок и текст";return;}
        var input=new LinkedHashMap<String,Object>();input.put("type",kindVote?"vote":"text");input.put("title",titleDraft);
        input.put("text",textDraft.replace('\n',' ').replace('\r',' '));
        if(kindVote){
            var labels=optionDrafts.stream().map(String::trim).toList();
            if(labels.size()<2||labels.size()>8||labels.stream().anyMatch(v->v.isBlank()||v.length()>60)
                ||labels.stream().map(v->v.toLowerCase(Locale.ROOT)).distinct().count()!=labels.size()){localError="Заполните 2–8 разных вариантов";return;}
            long minutes;try{minutes=Long.parseLong(durationDraft);}catch(NumberFormatException bad){localError="Укажите срок в минутах";return;}
            if(minutes<1||minutes>43200){localError="Срок: от 1 минуты до 30 дней";return;}
            input.put("options",labels);input.put("multiple",multiple);input.put("durationSeconds",minutes*60);
        }else{
            if(!nickDraft.matches("[A-Za-z0-9_]{1,16}")){localError="Укажите ник получателя";return;}
            input.put("nick",nickDraft);input.put("keep",keep);
        }
        CampaignClient.create(input);
    }
    private void ballot(){
        var poll=current();listTop=top+119;listBottom=top+h-58;
        if(poll==null){maxScroll=scroll=0;selected.clear();selectionFor="";return;}
        if(!poll.id().equals(selectionFor)){selected.clear();selectionFor=poll.id();}
        if(poll.submitted()){selected.clear();selected.addAll(poll.choices());}
        maxScroll=Math.max(0,poll.options().size()*25-(listBottom-listTop));scroll=Math.clamp(scroll,0,maxScroll);
        for(int i=0;i<poll.options().size();i++){
            final int choice=i;int y=listTop+i*25-scroll;
            var option=addRenderableWidget(new VoteOption(poll,choice,left+16,y,w-32,22));
            option.visible=y>=listTop&&y+22<=listBottom;option.active=mode!=2&&!poll.submitted()&&!CampaignClient.pending();
        }
        button("<",left+16,top+h-29,30,21,()->navigate(-1)).active=index>0;
        button(">",left+w-46,top+h-29,30,21,()->navigate(1)).active=index+1<rows().size();
        if(mode==2){
            button(poll.closed()?"Завершено":"Завершить досрочно",left+52,top+h-29,w-104,21,()->minecraft.gui.setScreen(new ConfirmScreen(ok->{
                minecraft.gui.setScreen(this);if(ok)CampaignClient.close(poll);
            },Component.literal("Завершить голосование?"),Component.literal(poll.title())))).active=!poll.closed()&&!CampaignClient.pending();
        }else button(poll.submitted()?"Голос сохранён":"Проголосовать",left+52,top+h-29,w-104,21,()->{
            if(selected.isEmpty()){localError="Выберите вариант ответа";return;}localError="";CampaignClient.vote(poll,selected);
        }).active=!poll.submitted()&&!CampaignClient.pending();
    }
    private void navigate(int step){index=Math.clamp(index+step,0,Math.max(0,rows().size()-1));scroll=0;selected.clear();localError="";rebuildWidgets();}
    @Override public void tick(){
        if(mode!=0&&!CampaignClient.admin()){mode=0;scroll=index=0;selected.clear();rebuildWidgets();return;}
        if(!signature().equals(rendered)){capture();rebuildWidgets();}
        for(var child:children())if(child instanceof Button button&&button.getMessage().getString().equals("Отправить"))button.active=!CampaignClient.pending();
    }
    private void label(GuiGraphicsExtractor g,String value,int x,int y,int width,int color){g.text(font,font.plainSubstrByWidth(value,Math.max(1,width)),x,y,color,false);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xA0000000);FlatButtons.plate(g,left,top,w,h,true,false,false,UiTheme.ACCENT);
        String heading=mode==1||mode==2?"Администрирование":"Голосования";
        g.text(font,heading,left+(w-font.width(heading))/2,top+12,UiTheme.TEXT,false);
        if(mode==1){
            int y=listTop-scroll;g.enableScissor(left+12,listTop,left+w-12,listBottom);
            label(g,"Заголовок",left+16,y+31,w-32,UiTheme.MUTED);label(g,"Текст",left+16,y+69,w-32,UiTheme.MUTED);
            label(g,kindVote?"Варианты ответов (2–8)":"Получатель",left+16,y+125,w-32,UiTheme.MUTED);
            if(kindVote)label(g,"Длительность, минут",left+16,y+165+optionDrafts.size()*22,(w-40)/2,UiTheme.MUTED);
            g.disableScissor();
        }else{
            var poll=current();
            if(poll==null)label(g,mode==2?"Голосований ещё нет":"Нет активных голосований",left+16,top+72,w-32,UiTheme.MUTED);
            else{
                label(g,poll.title(),left+16,top+64,w-32,UiTheme.TEXT);
                var lines=font.split(Component.literal(poll.text()),w-32);for(int i=0;i<Math.min(2,lines.size());i++)g.text(font,lines.get(i),left+16,top+78+i*10,UiTheme.MUTED,false);
                long seconds=Math.max(0,(poll.closesAt()-CampaignClient.serverNow())/1000);
                String status=mode==2?"Участников: "+poll.totalVoters()+(poll.closed()?" · Завершено":" · Идёт голосование")
                    :(poll.multiple()?"Несколько вариантов":"Один вариант")+" · "+seconds/60+"м "+seconds%60+"с";
                label(g,(index+1)+"/"+rows().size()+" · "+status,left+16,top+104,w-32,UiTheme.ACCENT);
            }
        }
        if(maxScroll>0){int length=listBottom-listTop,thumb=Math.max(10,length*length/(length+maxScroll));int y=listTop+(length-thumb)*scroll/maxScroll;g.fill(left+w-8,y,left+w-6,y+thumb,UiTheme.ACCENT);}
        label(g,localError.isEmpty()?CampaignClient.feedback():localError,left+16,top+h-45,w-32,
            !localError.isEmpty()||CampaignClient.failed()?0xFFFF9B9B:UiTheme.MUTED);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(x>=left&&x<left+w&&y>=listTop&&y<listBottom&&maxScroll>0){capture();scroll=Math.clamp(scroll-(int)Math.signum(dy)*25,0,maxScroll);rebuildWidgets();return true;}
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private final class VoteOption extends Button {
        private final CampaignClient.Poll poll;private final int choice;
        VoteOption(CampaignClient.Poll poll,int choice,int x,int y,int width,int height){
            super(x,y,width,height,Component.literal(poll.options().get(choice)),b->{},DEFAULT_NARRATION);this.poll=poll;this.choice=choice;
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers input){
            if(!active||CampaignClient.pending())return;
            if(selected.contains(choice))selected.remove(choice);else{if(!poll.multiple())selected.clear();selected.add(choice);}
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),true,isHoveredOrFocused(),false,UiTheme.ACCENT);
            int y=getY()+7;g.fill(getX()+7,y,getX()+15,y+8,UiTheme.MUTED);g.fill(getX()+8,y+1,getX()+14,y+7,UiTheme.BACKGROUND);
            if(selected.contains(choice))g.fill(getX()+9,y+2,getX()+13,y+6,UiTheme.ACCENT);
            String suffix=mode==2&&!poll.counts().isEmpty()?" · "+poll.counts().get(choice):"";
            label(g,poll.options().get(choice)+suffix,getX()+22,getY()+7,getWidth()-29,UiTheme.TEXT);
        }
    }
}
