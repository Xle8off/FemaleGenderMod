package com.wildfire.gui;

import com.wildfire.client.towns.TownsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;

/** City tab content. Hit targets exist only inside the rendered panel. */
public final class TownsPanel {
    private record Hit(int x,int y,int w,int h,Runnable action) {
        boolean contains(double px,double py){return px>=x&&py>=y&&px<x+w&&py<y+h;}
    }
    private final List<Hit> hits=new ArrayList<>();
    private int section,scroll,left,top,width,bottom,maxScroll;
    private long refreshedAt,confirmUntil;
    public void open(){scroll=0;hits.clear();TownsClient.refresh();}
    public void tick(){if(System.currentTimeMillis()-refreshedAt>10000){refreshedAt=System.currentTimeMillis();TownsClient.refresh();}}
    public static void icon(GuiGraphicsExtractor g,int x,int y){
        // Outline house: peaked roof, chimney, centred window and door.
        double[][] lines={
            {1,9,10,1},{10,1,19,9},
            {3,8,3,19},{3,19,17,19},{17,19,17,8},
            {14,4.5,14,2},{14,2,17,2},{17,2,17,7},
            {8,8,12,8},{12,8,12,11},{12,11,8,11},{8,11,8,8},
            {7,19,7,14},{7,14,13,14},{13,14,13,19}
        };
        for(int row=0;row<20;row++)for(int column=0;column<20;column++){
            double px=column+.5,py=row+.5;
            for(double[] line:lines){
                double dx=line[2]-line[0],dy=line[3]-line[1];
                double t=Math.clamp(((px-line[0])*dx+(py-line[1])*dy)/(dx*dx+dy*dy),0,1);
                double distance=Math.hypot(px-line[0]-t*dx,py-line[1]-t*dy);
                if(distance<=.85){g.fill(x+column,y+row,x+column+1,y+row+1,0xFFCBD6E5);break;}
            }
        }
        g.fill(x+11,y+16,x+12,y+17,0xFFCBD6E5);
    }
    public void render(GuiGraphicsExtractor g,int x,int y,int w,int end,int mx,int my){
        hits.clear();left=x;top=y;width=w;bottom=end;
        var font=Minecraft.getInstance().font;
        String[] labels={"Мой город","Города","Приглашения"};
        int cell=(w-4)/3;
        for(int i=0;i<3;i++){final int index=i;button(g,x+i*(cell+2),y,cell,20,labels[i],section==i,mx,my,()->{section=index;scroll=0;confirmUntil=0;});}
        var data=TownsClient.snapshot();
        if(data==null){
            int row=y+34;for(var line:font.split(net.minecraft.network.chat.Component.literal(TownsClient.status()),w-8)){g.text(font,line,x+4,row,UiTheme.MUTED,false);row+=11;}
            button(g,x,end-22,w,20,"Обновить",false,mx,my,TownsClient::refresh);return;
        }
        int listTop=y+28;
        if(section==0){
            FlatButtons.plate(g,x,listTop,w,72,true,false,false,UiTheme.ACCENT);
            if(data.own()!=null){
                icon(g,x+8,listTop+8);
                text(g,data.own().name(),x+36,listTop+8,w-42,UiTheme.TEXT);
                text(g,(data.own().capital()?"Столица · ":"")+data.own().type(),x+36,listTop+23,w-42,0xFFFFBF45);
                text(g,"Жители: "+data.own().population()+" / "+data.own().capacity(),x+8,listTop+41,w-16,UiTheme.MUTED);
                text(g,data.role(),x+8,listTop+55,w-16,UiTheme.TEXT);
            }else{
                text(g,"Вы пока не состоите в городе",x+8,listTop+10,w-16,UiTheme.TEXT);
                text(g,data.passport()?"Вступить можно по приглашению":"Сначала оформите паспорт",x+8,listTop+29,w-16,UiTheme.MUTED);
                text(g,"Создание города — через администрацию",x+8,listTop+48,w-16,UiTheme.MUTED);
            }
            listTop+=82;
            text(g,"Жители",x,listTop,w,UiTheme.MUTED);listTop+=15;
        }
        int rows=section==0?data.residents().size():section==1?data.cities().size():data.invitations().size();
        int rowHeight=section==2?62:section==1?43:35;
        int listBottom=end-52;
        maxScroll=Math.max(0,rows*rowHeight-Math.max(1,listBottom-listTop));scroll=Math.clamp(scroll,0,maxScroll);
        if(listBottom>listTop){
            g.enableScissor(x,listTop,x+w,listBottom);
            for(int i=scroll/rowHeight;i<rows;i++){
                int row=listTop+i*rowHeight-scroll;if(row>=listBottom)break;
                FlatButtons.plate(g,x,row,w,rowHeight-4,true,false,false,UiTheme.ACCENT);
                if(section==0){
                    var member=data.residents().get(i);
                    g.fill(x+7,row+8,x+11,row+12,member.online()?0xFF36C985:0xFF778394);
                    text(g,member.name(),x+17,row+5,w-23,UiTheme.TEXT);
                    text(g,member.role(),x+17,row+18,w-23,UiTheme.MUTED);
                }else{
                    var city=section==1?data.cities().get(i):data.invitations().get(i);
                    text(g,city.name()+(city.capital()?" · Столица":""),x+8,row+6,w-16,UiTheme.TEXT);
                    text(g,city.type()+" · "+city.population()+" / "+city.capacity(),x+8,row+20,w-16,UiTheme.MUTED);
                    if(section==2&&row>=listTop&&row+58<=listBottom){
                        int bw=(w-20)/2;
                        button(g,x+8,row+34,bw,20,"Принять",false,mx,my,()->TownsClient.action(3,city.id()));
                        button(g,x+12+bw,row+34,bw,20,"Отклонить",false,mx,my,()->TownsClient.action(4,city.id()));
                    }
                }
            }
            g.disableScissor();
            if(rows==0)text(g,section==2?"Приглашений пока нет":section==1?"Городов пока нет":"Нет жителей",x+4,listTop+8,w-8,UiTheme.MUTED);
            if(maxScroll>0){int track=listBottom-listTop,thumb=Math.max(12,track*track/(track+maxScroll));int ty=listTop+(int)((long)scroll*(track-thumb)/maxScroll);g.fill(x+w-2,ty,x+w,ty+thumb,UiTheme.ACCENT);}
        }
        int half=(w-4)/2;
        button(g,x,end-46,half,20,"Меню города",false,mx,my,()->TownsClient.action(1,""));
        button(g,x+half+4,end-46,half,20,"Паспорт",false,mx,my,()->TownsClient.action(2,""));
        if(section==0&&data.own()!=null&&!data.role().equals("Глава города")){
            button(g,x,end-22,w,20,System.currentTimeMillis()<confirmUntil?"Подтвердить выход из города":"Покинуть город",false,mx,my,()->{
                if(System.currentTimeMillis()<confirmUntil){TownsClient.action(5,"");confirmUntil=0;}else confirmUntil=System.currentTimeMillis()+5000;
            });
        }else button(g,x,end-22,w,20,"Обновить",false,mx,my,TownsClient::refresh);
    }
    private static void text(GuiGraphicsExtractor g,String text,int x,int y,int w,int color){var font=Minecraft.getInstance().font;g.text(font,font.plainSubstrByWidth(text,Math.max(0,w)),x,y,color,false);}
    private void button(GuiGraphicsExtractor g,int x,int y,int w,int h,String label,boolean selected,int mx,int my,Runnable action){
        var hit=new Hit(x,y,w,h,action);hits.add(hit);
        FlatButtons.plate(g,x,y,w,h,true,selected||hit.contains(mx,my),false,UiTheme.ACCENT);
        var font=Minecraft.getInstance().font;String shown=font.plainSubstrByWidth(label,Math.max(0,w-8));
        g.text(font,shown,x+(w-font.width(shown))/2,y+(h-font.lineHeight)/2,UiTheme.TEXT,false);
        if(hit.contains(mx,my))g.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
    }
    public boolean click(double x,double y,int button){if(button!=0)return false;for(var hit:hits)if(hit.contains(x,y)){hit.action.run();return true;}return false;}
    public boolean scroll(double x,double y,double amount){if(x<left||x>=left+width||y<top+28||y>=bottom-52)return false;scroll=Math.clamp(scroll-(int)Math.round(amount*35),0,maxScroll);return true;}
}
