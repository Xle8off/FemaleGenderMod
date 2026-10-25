package com.wildfire.gui;

import java.util.List;

/** Pure, bounded animation math shared by open chat and the HUD. No render tasks or textures. */
public final class ChatMotion {
    public enum Appearance {
        OFF("Без анимации"), FADE("Затухание"), LEFT("Слева"), UP("Снизу"),
        RIGHT("Справа"), DOWN("Сверху"), LOWER_LEFT("Слева снизу"), UPPER_RIGHT("Справа сверху");
        private final String label;
        Appearance(String label){this.label=label;}
        public String label(){return label;}
    }
    public enum Curve {
        EASE_OUT("Замедление"), LINEAR("Равномерно"), SMOOTH("Мягко");
        private final String label;
        Curve(String label){this.label=label;}
        public String label(){return label;}
    }
    public record Entry(float opacity,int x,int y){}
    public record Layout(int target,float scroll,float insertion){}
    private ChatMotion(){}
    public static Entry entry(Appearance style,long ageNanos,long durationNanos){
        return entry(style,ageNanos,durationNanos,Curve.EASE_OUT);
    }
    public static Entry entry(Appearance style,long ageNanos,long durationNanos,Curve curve){
        if(style==Appearance.OFF)return new Entry(1,0,0);
        double t=Math.clamp(ageNanos/(double)Math.max(1,durationNanos),0,1);
        double eased=switch(curve){case LINEAR->t;case SMOOTH->t*t*(3-2*t);case EASE_OUT->1-Math.pow(1-t,3);};
        return offset(style,(float)eased,(float)(1-eased));
    }
    private static Entry offset(Appearance style,float opacity,float distance){
        int x=switch(style){case LEFT,LOWER_LEFT->-24;case RIGHT,UPPER_RIGHT->24;default->0;};
        int y=switch(style){case UP,LOWER_LEFT->12;case DOWN,UPPER_RIGHT->-12;default->0;};
        return new Entry(opacity,Math.round(x*distance),Math.round(y*distance));
    }
    public static Entry exit(Appearance style,float visibility){
        if(style==Appearance.OFF)return new Entry(1,0,0);
        float visible=Math.clamp(visibility,0,1);
        return offset(style,visible,1-visible);
    }
    public static int color(int color,float opacity){
        // Font treats an almost-zero legacy alpha as unspecified; keep text safely translucent.
        int alpha=Math.max(4,Math.round((color>>>24)*Math.clamp(opacity,0,1)));
        return color&0xFFFFFF|alpha<<24;
    }
    /** Last part of the HUD lifetime fades out; open history never expires. */
    public static float lifetime(long ageMillis,long lifetimeMillis,long durationNanos){
        double remaining=(lifetimeMillis-ageMillis)/(Math.max(1,durationNanos)/1_000_000.0);
        double t=Math.clamp(remaining,0,1);
        return (float)(t*t*(3-2*t));
    }
    /** Fade only partially clipped rows, not fully visible messages at either edge. */
    public static float edge(int rowTop,int rowHeight,int top,int bottom){
        int height=Math.max(1,rowHeight);
        double visible=Math.clamp((Math.min(rowTop+height,bottom)-Math.max(rowTop,top))/(double)height,0,1);
        return (float)(visible*visible*(3-2*visible));
    }
    public static final class Viewport {
        private Object last;
        private long frame;
        private double visual,push;
        private boolean initialized;
        public void reset(){initialized=false;last=null;frame=0;visual=push=0;}
        public Layout update(List<?> rows,int[] heights,int target,int maximum,long now,long duration,
                             boolean shift,boolean smoothScroll,boolean direct){
            if(rows.size()!=heights.length)throw new IllegalArgumentException("row/height mismatch");
            if(!initialized){initialized=true;visual=target;}
            else if(last!=null&&!rows.isEmpty()){
                int at=-1;for(int i=rows.size()-1;i>=0;i--)if(rows.get(i)==last){at=i;break;}
                if(at>=0&&at<rows.size()-1){
                    int added=0;for(int i=at+1;i<heights.length;i++)added+=Math.max(0,heights[i]);
                    if(target>0){target+=added;visual+=added;}
                    else if(shift)push=Math.min(120,push+added);
                }
            }
            last=rows.isEmpty()?null:rows.getLast();
            double dt=frame==0?0:Math.clamp((now-frame)/1e9,0,.1);frame=now;
            target=Math.clamp(target,0,Math.max(0,maximum));
            double factor=1-Math.exp(-dt*6/(Math.max(1,duration)/1e9));
            visual=!smoothScroll||direct?target:visual+(target-visual)*factor;
            visual=Math.clamp(visual,0,Math.max(0,maximum));
            if(Math.abs(visual-target)<.1)visual=target;
            push=shift&&!direct?push*(1-factor):0;
            if(push<.1)push=0;
            return new Layout(target,(float)visual,(float)push);
        }
    }
}
