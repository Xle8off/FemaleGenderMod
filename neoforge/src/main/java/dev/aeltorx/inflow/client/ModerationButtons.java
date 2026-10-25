package dev.aeltorx.inflow.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Compact block-shaped controls shared by all moderation forms. */
public final class ModerationButtons {
    private ModerationButtons(){}
    private static long frameElapsed=Long.MAX_VALUE;
    static long frame(long elapsed){long old=frameElapsed;frameElapsed=elapsed;return old;}
    public static float appearance(long elapsed,int rowY){double t=Math.clamp((elapsed-Math.clamp(rowY/25,0,12)*6_000_000L)/160_000_000.0,0,1);return (float)(1-Math.pow(1-t,3));}
    public static void selected(Button button,boolean value){if(button instanceof Tile tile)tile.selected=value;}
    public static void person(Button button,String name){if(button instanceof Tile tile)tile.person=name;}
    public static Builder builder(Component text,Button.OnPress action){return new Builder(text,action);}
    public static final class Builder {
        private final Component text;private final Button.OnPress action;private int x,y,w=150,h=20;
        private Builder(Component text,Button.OnPress action){this.text=text;this.action=action;}
        public Builder bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
        public Button build(){return new Tile(x,y,w,h,text,action);}
    }
    private static final class Tile extends Button {
        private boolean selected;
        private String person="";
        Tile(int x,int y,int w,int h,Component text,OnPress action){super(x,y,w,h,text,action,DEFAULT_NARRATION);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            // Mouse coordinates have already been converted into the logical canvas.
            // Vanilla's scissor hover test uses screen coordinates at scaled GUI sizes.
            boolean hot=active&&(isMouseOver(mx,my)||isFocused());int x=getX(),y=getY(),w=getWidth(),h=getHeight();
            float opacity=appearance(frameElapsed,y);if(opacity<=0)return;
            ModerationSurfaces.control(g,x,y,w,h,active,hot||selected,false,opacity);
            var font=net.minecraft.client.Minecraft.getInstance().font;
            String symbol=getMessage().getString();
            if(person.isBlank()&&(symbol.equals("←")||symbol.equals("—"))){
                int color=ModerationSurfaces.fade((active?ModerationTheme.TEXT:ModerationTheme.MUTED).getRGB(),opacity);
                int cx=x+w/2,cy=y+h/2;
                if(symbol.equals("—"))g.fill(cx-5,cy-1,cx+5,cy+1,color);
                else {g.fill(cx-5,cy-1,cx+5,cy+1,color);for(int step=0;step<4;step++){g.fill(cx-5+step,cy-1-step,cx-3+step,cy+1-step,color);g.fill(cx-5+step,cy-1+step,cx-3+step,cy+1+step,color);}}
                return;
            }
            int inset=person.isBlank()?4:25;
            var label=person.isBlank()?getMessage():ModerationPeople.label(person,getMessage());
            var text=net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(label,Math.max(1,w-inset-4)));
            if(!person.isBlank())ModerationPeople.head(g,person,x+3,y+2,16);
            g.text(font,text,person.isBlank()?x+(w-font.width(text))/2:x+inset,y+(h-font.lineHeight)/2,ModerationSurfaces.fade((active?ModerationTheme.TEXT:ModerationTheme.MUTED).getRGB(),opacity),false);
        }
    }
}
