package com.wildfire.gui;

import com.wildfire.client.overhead.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Same centred modal dimensions/theme as CampaignScreen. Preview never publishes itself. */
public final class CameraScreen extends Screen {
    private final Screen parent;
    private final NativeCameraPreview preview;
    private int left,top,w,h,fieldPitch,device=-1,dropdown=-1,dropScroll,dropSelected,dropX,dropY,dropW;
    private CameraOptions.Mode mode;
    private List<String> options=List.of();
    private List<NativeCameraPreview.Device> shown=List.of();
    private final List<Button> fields=new ArrayList<>();
    private Button enable,close;
    private DynamicTexture texture;
    private final Identifier textureId=Identifier.fromNamespaceAndPath("appearance_sync","camera_preview");
    private int imageWidth,imageHeight;
    private long frameAt;
    private boolean restored;
    private static final class PreviewTexture extends DynamicTexture {
        PreviewTexture(NativeImage image){
            super(()->"SpringPlus local camera preview",image);
            sampler=com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
        }
    }
    public CameraScreen(Screen parent){
        super(Component.literal("Камера"));this.parent=parent;
        preview=CameraClient.capture()!=null?CameraClient.capture():new NativeCameraPreview();
        device=preview.selectedDevice();mode=preview.selectedMode();
    }
    private Button button(String label,int x,int y,int width,Runnable action){
        return addRenderableWidget(FlatButtons.builder(Component.literal(label),b->action.run()).bounds(x,y,width,22).build());
    }
    @Override protected void init(){
        w=Math.min(620,width-16);h=Math.min(340,height-16);left=(width-w)/2;top=(height-h)/2;
        fields.clear();dropdown=-1;int fieldWidth=(w-44)/2;
        fieldPitch=Math.clamp((h-110)/3,30,43);
        addRenderableWidget(new Button(left+w-29,top+7,21,20,Component.literal("Закрыть"),b->onClose(),message->message.get()){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
                FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),true,isHoveredOrFocused(),false,UiTheme.ACCENT);
                int x=getX()+(getWidth()-7)/2,y=getY()+(getHeight()-7)/2;
                for(int i=0;i<7;i++){g.fill(x+i,y+i,x+i+1,y+i+1,UiTheme.TEXT);g.fill(x+6-i,y+i,x+7-i,y+i+1,UiTheme.TEXT);}
            }
        });
        for(int i=0;i<3;i++){
            final int index=i;
            fields.add(addRenderableWidget(new Button(left+16,top+53+i*fieldPitch,fieldWidth,22,Component.empty(),b->open(index),message->message.get()){
                @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
                    FlatButtons.plate(g,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused()||dropdown==index,false,UiTheme.ACCENT);
                    g.text(font,font.plainSubstrByWidth(getMessage().getString(),Math.max(1,getWidth()-22)),getX()+7,getY()+7,active?UiTheme.TEXT:UiTheme.MUTED,false);
                    int x=getX()+getWidth()-12,y=getY()+9;g.fill(x,y,x+7,y+1,UiTheme.MUTED);g.fill(x+1,y+1,x+6,y+2,UiTheme.MUTED);g.fill(x+2,y+2,x+5,y+3,UiTheme.MUTED);
                }
            }));
        }
        close=button("Отмена",left+16,top+h-34,(w-36)/2,this::onClose);
        enable=button("Включить камеру",left+20+(w-36)/2,top+h-34,(w-36)/2,()->{
            if(CameraClient.capture()==preview){CameraClient.stop();onClose();}
            else CameraClient.publish(preview);
        });
        update();
    }
    private List<CameraOptions.Mode> modes(){return device<0||device>=preview.devices().size()?List.of():preview.devices().get(device).modes();}
    private List<String> resolutions(){return modes().stream().map(CameraOptions.Mode::resolution).distinct().toList();}
    private List<Integer> rates(){return mode==null?List.of():modes().stream().filter(m->m.width()==mode.width()&&m.height()==mode.height()).map(CameraOptions.Mode::fps).distinct().sorted(Comparator.reverseOrder()).toList();}
    private void update(){
        boolean running=CameraClient.capture()==preview;
        fields.get(0).setMessage(Component.literal(device<0?"Выбрать камеру":preview.devices().get(device).name()));
        fields.get(1).setMessage(Component.literal(mode==null?"Нет доступных режимов":mode.resolution()));
        fields.get(2).setMessage(Component.literal(mode==null?"—":mode.fps()+" FPS"));
        fields.get(0).active=!running&&!preview.devices().isEmpty();fields.get(1).active=fields.get(2).active=!running&&mode!=null;
        close.active=!running;
        enable.setMessage(Component.literal(running?(CameraClient.pending()?"Отменить подключение":"Выключить камеру"):CameraClient.pending()?"Подключение…":"Включить камеру"));
        enable.active=running||!CameraClient.pending()&&mode!=null&&preview.ready()&&CameraClient.available();
    }
    private void open(int index){
        if(dropdown==index){dropdown=-1;return;}
        dropdown=index;dropScroll=dropSelected=0;var field=fields.get(index);dropX=field.getX();dropY=field.getY()+24;dropW=field.getWidth();
        options=switch(index){case 0->preview.devices().stream().map(NativeCameraPreview.Device::name).toList();case 1->resolutions();default->rates().stream().map(rate->rate+" FPS").toList();};
        dropY=Math.min(dropY,top+h-40-Math.min(6,options.size())*20);
    }
    private void choose(int index){
        if(dropdown==0){device=index;mode=modes().isEmpty()?null:modes().getFirst();}
        else if(dropdown==1){String resolution=options.get(index);mode=modes().stream().filter(m->m.resolution().equals(resolution)).findFirst().orElse(null);}
        else if(mode!=null){int fps=rates().get(index);mode=new CameraOptions.Mode(mode.width(),mode.height(),fps);}
        dropdown=-1;releaseTexture();frameAt=0;
        if(mode!=null)preview.select(device,mode);
        else preview.disable("У камеры нет поддерживаемых режимов до 480p / 30 FPS");
        if(mode!=null)com.wildfire.client.config.CosmeticPreferences.setCamera(preview.devices().get(device).name(),mode);
        update();
    }
    @Override public void tick(){
        if(!restored&&preview.scanned()){
            restored=true;
            if(device<0){
                var saved=com.wildfire.client.config.CosmeticPreferences.cameraDevice();
                for(int i=0;i<preview.devices().size();i++)if(preview.devices().get(i).name().equals(saved)){
                    device=i;
                    var wanted=com.wildfire.client.config.CosmeticPreferences.cameraMode();
                    mode=modes().contains(wanted)?wanted:modes().stream().findFirst().orElse(null);
                    if(mode!=null)preview.select(device,mode);
                    break;
                }
            }
        }
        if(!shown.equals(preview.devices())){shown=preview.devices();update();}
        update();
        if(minecraft.level==null)onClose();
    }
    private void upload(){
        var frame=preview.poll();if(frame==null||frame.time()==frameAt)return;
        int rotation=Math.floorMod(frame.rotation(),360),iw=rotation%180==0?frame.width():frame.height(),ih=rotation%180==0?frame.height():frame.width();
        if(texture!=null&&(iw!=imageWidth||ih!=imageHeight))releaseTexture();
        NativeImage image=texture==null?new NativeImage(iw,ih,false):texture.getPixels();
        for(int y=0;y<frame.height();y++)for(int x=0;x<frame.width();x++){
            int at=(y*frame.width()+x)*4;var rgba=frame.rgba();int color=(rgba[at+3]&255)<<24|(rgba[at]&255)<<16|(rgba[at+1]&255)<<8|(rgba[at+2]&255);
            int dx=x,dy=y;
            if(rotation==90){dx=frame.height()-1-y;dy=x;}else if(rotation==180){dx=frame.width()-1-x;dy=frame.height()-1-y;}else if(rotation==270){dx=y;dy=frame.width()-1-x;}
            image.setPixel(dx,dy,color);
        }
        if(texture==null){texture=new PreviewTexture(image);minecraft.getTextureManager().register(textureId,texture);imageWidth=iw;imageHeight=ih;}
        else texture.upload();
        frameAt=frame.time();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        upload();g.fill(0,0,width,height,0xA0000000);FlatButtons.plate(g,left,top,w,h,true,false,false,UiTheme.ACCENT);
        g.text(font,"Камера",left+(w-font.width("Камера"))/2,top+12,UiTheme.TEXT,false);
        g.fill(left+1,top+32,left+w-1,top+33,0xFF454151);
        String[] labels={"Устройство","Разрешение","Частота кадров"};for(int i=0;i<3;i++)g.text(font,labels[i],left+17,top+41+i*fieldPitch,UiTheme.MUTED,false);
        int px=left+w/2+6,py=top+42,pw=w/2-22,ph=Math.max(30,h-111);
        RoundedPanels.fill(g,px,py,px+pw,py+ph,0xFF10151E);
        if(texture!=null&&System.nanoTime()-frameAt<2_000_000_000L){
            double scale=Math.min(pw/(double)imageWidth,ph/(double)imageHeight);int dw=Math.max(1,(int)(imageWidth*scale)),dh=Math.max(1,(int)(imageHeight*scale));
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,textureId,px+(pw-dw)/2,py+(ph-dh)/2,0,0,dw,dh,imageWidth,imageHeight,imageWidth,imageHeight);
        }else g.text(font,"Предпросмотр",px+(pw-font.width("Предпросмотр"))/2,py+ph/2-4,UiTheme.MUTED,false);
        String status=CameraClient.capture()==preview?CameraClient.status():
            !CameraClient.lastFailure().isEmpty()?CameraClient.lastFailure():preview.status();
        int infoY=top+Math.min(187,h-66);
        for(var line:font.split(Component.literal(status),Math.max(20,(w-44)/2))) {if(infoY>top+h-61)break;g.text(font,line,left+17,infoY,UiTheme.MUTED,false);infoY+=11;}
        String hint=CameraClient.available()?"До 480p · 30 FPS · 800 Кбит/с":"Координатор камер недоступен";
        g.text(font,font.plainSubstrByWidth(hint,w-32),left+16,top+h-51,UiTheme.MUTED,false);
        super.extractRenderState(g,mx,my,delta);
        if(dropdown>=0){
            int rows=Math.min(6,options.size()-dropScroll);
            FlatButtons.plate(g,dropX,dropY,dropW,rows*20+2,true,false,false,UiTheme.ACCENT);
            for(int i=0;i<rows;i++){
                int y=dropY+1+i*20;if(dropSelected==dropScroll+i||mx>=dropX&&mx<dropX+dropW&&my>=y&&my<y+20)g.fill(dropX+1,y,dropX+dropW-1,y+20,0xFF3B354D);
                g.text(font,font.plainSubstrByWidth(options.get(dropScroll+i),dropW-14),dropX+7,y+6,UiTheme.TEXT,false);
            }
        }
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick){
        if(dropdown>=0){
            int row=(int)((event.y()-dropY-1)/20);
            if(event.button()==0&&event.x()>=dropX&&event.x()<dropX+dropW&&event.y()>=dropY+1&&row>=0&&row<Math.min(6,options.size()-dropScroll))choose(dropScroll+row);
            else dropdown=-1;
            return true;
        }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(dropdown>=0){dropScroll=Math.clamp(dropScroll-(int)Math.signum(dy),0,Math.max(0,options.size()-6));return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
        if(dropdown>=0){
            if(event.key()==256||event.key()==258){dropdown=-1;return true;}
            if((event.key()==257||event.key()==335||event.key()==32)&&!options.isEmpty()){choose(dropSelected);return true;}
            if((event.key()==264||event.key()==265)&&!options.isEmpty()){
                dropSelected=Math.clamp(dropSelected+(event.key()==264?1:-1),0,options.size()-1);
                if(dropSelected<dropScroll)dropScroll=dropSelected;if(dropSelected>=dropScroll+6)dropScroll=dropSelected-5;
            }
            return true;
        }
        return super.keyPressed(event);
    }
    private void releaseTexture(){if(texture!=null){minecraft.getTextureManager().release(textureId);texture=null;}}
    @Override public void removed(){releaseTexture();if(CameraClient.capture()!=preview)preview.close();}
    // Closing the settings window is not revocation of the explicit publish action.
    // removed() keeps the preview alive while CameraClient owns it, including negotiation.
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
