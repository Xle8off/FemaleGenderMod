package dev.aeltorx.inflow.client;

import java.awt.*;
import java.util.Locale;

/** Code-native pixel icons: no font glyphs, network images or identical placeholder cubes. */
public final class WheelIcons {
    public enum Kind { PANEL,CROSSHAIR,REPORTS,MODERATORS,CARD,INSPECT,TELEPORT,CUSTOM_RULES,PUNISH,CHECK,PASS,HISTORY,PREVIEW,SETTINGS,PLAYER,RULE,CLOCK,WARN,MUTE,BAN,CONFIRM,NEXT }
    private WheelIcons(){}
    private record Bounds(int x,int y,int width,int height){}
    private static final java.util.Map<Kind,Bounds> BOUNDS=new java.util.concurrent.ConcurrentHashMap<>();
    /** Centre the visible artwork, not its sometimes asymmetric nominal canvas. */
    public static void drawCentered(Graphics2D g,Kind kind,int cx,int cy){
        var b=BOUNDS.computeIfAbsent(kind,k->{
            var image=new java.awt.image.BufferedImage(36,36,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var probe=image.createGraphics();try{draw(probe,k,3,3);}finally{probe.dispose();}
            int left=36,top=36,right=-1,bottom=-1;
            for(int y=0;y<36;y++)for(int x=0;x<36;x++)if((image.getRGB(x,y)>>>24)!=0){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
            return new Bounds(left-3,top-3,right-left+1,bottom-top+1);
        });
        var centered=(Graphics2D)g.create();try{centered.translate(cx-b.x()-b.width()/2.0,cy-b.y()-b.height()/2.0);draw(centered,kind,0,0);}finally{centered.dispose();}
    }
    public static Kind forLabel(String label){
        String s=label.toLowerCase(Locale.ROOT);
        if(s.contains("избран"))return Kind.SETTINGS;
        if(s.contains("подтверд"))return Kind.CONFIRM;
        if(s.contains("далее"))return Kind.NEXT;
        if(s.contains("срок")||s.contains("минут")||s.contains("час")||s.contains("день")||s.contains("дней")||s.contains("навсегда"))return Kind.CLOCK;
        if(s.contains("варн"))return Kind.WARN;if(s.contains("мут"))return Kind.MUTE;if(s.contains("бан"))return Kind.BAN;
        if(s.matches("^[0-9].*"))return Kind.RULE;
        return Kind.PLAYER;
    }
    private static void line(Graphics2D g,int x,int y,int xx,int yy){g.drawLine(x,y,xx,yy);}
    private static void tick(Graphics2D g){line(g,7,15,12,20);line(g,12,20,23,8);}
    private static void person(Graphics2D g,int x,int y){g.fillRect(x+3,y,8,8);g.fillRect(x,y+10,14,9);}
    public static void draw(Graphics2D source,Kind kind,int x,int y){
        var g=(Graphics2D)source.create();
        try{g.translate(x,y);g.setStroke(new BasicStroke(2.5f,BasicStroke.CAP_SQUARE,BasicStroke.JOIN_MITER));
            g.setColor(switch(kind){case BAN,PUNISH,MUTE->new Color(244,123,132);case PASS,CONFIRM->new Color(112,218,160);case WARN,REPORTS,CLOCK->new Color(255,204,102);case PLAYER,CARD,MODERATORS,CROSSHAIR->new Color(146,187,255);default->new Color(190,151,234);});
            switch(kind){
                case PANEL->{g.drawRect(2,3,24,22);line(g,2,9,26,9);g.fillRect(6,13,6,8);g.fillRect(16,13,6,3);g.fillRect(16,19,6,3);}
                case CROSSHAIR->{g.drawRect(6,6,16,16);line(g,14,1,14,9);line(g,14,19,14,27);line(g,1,14,9,14);line(g,19,14,27,14);g.fillRect(12,12,4,4);}
                case REPORTS->{g.drawRect(5,1,18,26);g.fillRect(13,5,3,10);g.fillRect(13,19,3,3);}
                case MODERATORS->{person(g,1,2);g.setColor(new Color(190,151,234));person(g,14,9);}
                case CARD->{g.drawRect(1,4,26,21);person(g,4,7);line(g,21,10,24,10);line(g,21,16,24,16);}
                case INSPECT->{g.drawOval(2,2,16,16);line(g,17,17,27,27);g.fillRect(8,8,4,4);}
                case TELEPORT->{g.drawRect(2,2,13,24);line(g,10,14,27,14);line(g,21,8,27,14);line(g,21,20,27,14);}
                case CUSTOM_RULES->{g.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.drawRoundRect(4,2,20,24,3,3);for(int yy:new int[]{8,14,20}){g.fillRect(8,yy-1,2,2);line(g,13,yy,20,yy);}}
                case PUNISH->{g.fillRect(6,2,14,7);g.fillRect(3,1,4,10);g.fillRect(20,1,4,10);g.fillRect(12,9,4,13);g.fillRect(5,24,20,3);}
                case CHECK->{g.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.drawRoundRect(4,2,20,24,3,3);g.drawOval(10,7,8,8);line(g,16,14,20,18);line(g,8,22,20,22);}
                case PASS->{g.setStroke(new BasicStroke(2.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.drawOval(2,2,24,24);line(g,8,14,12,18);line(g,12,18,20,10);}
                case HISTORY->{g.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.drawRoundRect(4,2,20,24,3,3);line(g,9,8,19,8);line(g,9,14,19,14);line(g,9,20,16,20);}
                case PREVIEW->{g.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.drawRoundRect(2,3,24,22,4,4);g.fillPolygon(new int[]{11,11,20},new int[]{8,20,14},3);}
                case SETTINGS->{g.drawOval(5,5,18,18);g.drawOval(11,11,6,6);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(g,14+(int)(10*Math.cos(a)),14+(int)(10*Math.sin(a)),14+(int)(14*Math.cos(a)),14+(int)(14*Math.sin(a)));}}
                case PLAYER->{person(g,7,3);g.setColor(ModerationTheme.CHROME);g.fillRect(11,6,2,2);g.fillRect(16,6,2,2);}
                case RULE->{g.drawRect(2,3,11,22);g.drawRect(14,3,11,22);line(g,5,9,10,9);line(g,17,9,22,9);line(g,5,15,10,15);line(g,17,15,22,15);}
                case CLOCK->{g.drawOval(2,2,24,24);line(g,14,6,14,14);line(g,14,14,21,18);}
                case WARN->{g.drawPolygon(new int[]{14,1,27},new int[]{2,26,26},3);line(g,14,10,14,17);g.fillRect(13,21,3,3);}
                case MUTE->{g.drawRect(2,7,23,15);line(g,8,21,6,27);line(g,2,1,27,27);}
                case BAN->{g.drawOval(2,2,24,24);line(g,6,6,23,23);}
                case CONFIRM->tick(g);
                case NEXT->{line(g,2,14,26,14);line(g,17,5,26,14);line(g,17,23,26,14);}
            }
        }finally{g.dispose();}
    }
}
