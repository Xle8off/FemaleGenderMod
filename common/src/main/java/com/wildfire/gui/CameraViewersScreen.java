package com.wildfire.gui;

import com.wildfire.client.overhead.CameraClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Owner-only live diagnostics. Opening/closing this screen never stops the camera. */
public final class CameraViewersScreen extends Screen {
    private final Screen parent;
    private int left,top,w,h,offset;
    public CameraViewersScreen(Screen parent){super(Component.literal("Зрители камеры"));this.parent=parent;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(CameraClient.capture()==null)parent.onClose();else minecraft.gui.setScreen(parent);}
    @Override protected void init(){
        w=Math.min(620,width-16);h=Math.min(340,height-16);left=(width-w)/2;top=(height-h)/2;
        addRenderableWidget(FlatButtons.builder(Component.literal("Назад"),b->onClose()).bounds(left+16,top+h-34,w-32,22).build());
    }
    private int slots(){return Math.max(1,(h-154)/29);}
    @Override public void tick(){if(minecraft.level==null)minecraft.gui.setScreen(null);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xA0000000);FlatButtons.plate(g,left,top,w,h,true,false,false,UiTheme.ACCENT);
        String heading="Зрители камеры";g.text(font,heading,left+(w-font.width(heading))/2,top+12,UiTheme.TEXT,false);
        long now=System.currentTimeMillis(),since=CameraClient.viewerUpdated()==0?Long.MAX_VALUE:Math.max(0,now-CameraClient.viewerUpdated());
        var rows=CameraClient.viewers();long receiving=rows.stream().filter(v->v.status().equals("RECEIVING")&&v.ageMillis()>=0&&v.ageMillis()+since<=8000).count();
        String connection=CameraClient.capture()==null?(CameraClient.lastFailure().isBlank()?"Камера выключена":CameraClient.lastFailure()):CameraClient.publicationConnected()?"Связь с SFU установлена":"Подключение к SFU…";
        g.text(font,connection,left+16,top+40,UiTheme.TEXT,false);
        g.text(font,"Подписок: "+CameraClient.subscriberCount()+" · Получают кадры: "+receiving,left+16,top+56,UiTheme.MUTED,false);
        g.text(font,"Статусы подтверждают клиенты, не локальный предпросмотр",left+16,top+72,UiTheme.MUTED,false);
        g.fill(left+16,top+88,left+w-16,top+89,UiTheme.BORDER);
        offset=Math.clamp(offset,0,Math.max(0,rows.size()-slots()));
        g.enableScissor(left+14,top+94,left+w-14,top+h-61);
        if(!CameraClient.viewerDiagnostics()){
            String message=CameraClient.viewerUpdated()==0?"Ожидаем ответ координатора…":"Нужно обновить координатор на прокси";
            g.text(font,message,left+16,top+101,UiTheme.MUTED,false);
        }else if(rows.isEmpty())g.text(font,CameraClient.capture()==null?"Сначала включи камеру":"Подписок и попыток подключения пока нет",left+16,top+101,UiTheme.MUTED,false);
        for(int i=offset;i<Math.min(rows.size(),offset+slots());i++){
            var row=rows.get(i);int y=top+96+(i-offset)*29;
            long age=row.ageMillis()<0?Long.MAX_VALUE:row.ageMillis()+since;
            boolean live=row.status().equals("RECEIVING")&&age<=8000;
            int color=row.status().equals("ERROR")?0xFFFF8B91:live?0xFF6DDB9B:UiTheme.MUTED;
            g.text(font,font.plainSubstrByWidth(row.name(),Math.max(1,w/3-20)),left+16,y,UiTheme.TEXT,false);
            g.text(font,font.plainSubstrByWidth(CameraViewerLabels.status(row.status(),age),Math.max(1,w*2/3-28)),left+w/3,y,color,false);
            String detail=CameraViewerLabels.error(row.code());
            if(detail.isEmpty())detail="Кадров получено: "+row.frames()+(row.ageMillis()<0?"":" · Отчёт: "+Math.max(0,age/1000)+" с назад");
            g.text(font,font.plainSubstrByWidth(detail,w-34),left+16,y+12,UiTheme.MUTED,false);
        }
        g.disableScissor();
        String footer=since>10000?"Нет свежего ответа координатора":"Обновление автоматически · листай список колёсиком";
        if(CameraClient.viewerTotal()>rows.size())footer="Показаны первые "+rows.size()+" из "+CameraClient.viewerTotal();
        g.text(font,font.plainSubstrByWidth(footer,w-32),left+16,top+h-51,UiTheme.MUTED,false);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(x>=left&&x<left+w&&y>=top+88&&y<top+h-58){offset=Math.clamp(offset-(int)Math.signum(dy),0,Math.max(0,CameraClient.viewers().size()-slots()));return true;}
        return super.mouseScrolled(x,y,dx,dy);
    }
}
