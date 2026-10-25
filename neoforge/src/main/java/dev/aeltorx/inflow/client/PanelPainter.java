package dev.aeltorx.inflow.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/** The game and visual regression tests use this same supersampled window renderer. */
public final class PanelPainter {
    public static final int RES=3;
    public record Control(int x,int y,int width,int height,String text,boolean field,boolean active,boolean hovered,boolean focused){}
    public record Line(int x,int y,String text,int color,int width){public Line(int x,int y,String text,int color){this(x,y,text,color,0);}}
    public record Profile(String name,String uuid,String state,String ping,String session,String active){}
    public record Account(String name,BufferedImage avatar,boolean hovered){}
    /** Native runtime draws all text/widgets itself; this cache contains only surfaces and avatar. */
    public static BufferedImage surface(int width,int height,Account account){
        var image=new BufferedImage(width*RES,height*RES,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try{g.scale(RES,RES);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(ModerationTheme.BACKGROUND);g.fillRoundRect(0,0,width-4,height-5,12,12);
            var clip=g.getClip();g.clip(new java.awt.geom.RoundRectangle2D.Double(0,0,width-4,height-5,12,12));g.setColor(ModerationTheme.CHROME);g.fillRect(0,0,width-4,33);g.setClip(clip);
            g.setColor(ModerationTheme.ACCENT);g.drawRoundRect(0,0,width-5,height-6,12,12);g.setColor(ModerationTheme.BORDER);g.drawLine(1,32,width-6,32);g.drawLine(128,42,128,height-59);
            g.setColor(ModerationTheme.CHROME);g.fillRoundRect(140,42,ModerationPanelLayout.contentWidth(width),46,8,8);g.setColor(ModerationTheme.ACCENT);g.fillRect(140,48,2,34);
            int y=height-40;g.setColor(account.hovered?ModerationTheme.HOVER:ModerationTheme.CHROME);g.fillRoundRect(8,y,width-20,30,10,10);g.setColor(ModerationTheme.BORDER);g.drawLine(8,y-5,width-12,y-5);InFlowBrandPainter.avatar(g,account.avatar,account.name,14,y+4,22);
        }finally{g.dispose();}return image;
    }
    public static BufferedImage paint(int width,int height,String player,List<Control> controls,List<Line> lines){
        return paint(width,height,player,controls,lines,null);
    }
    public static BufferedImage paint(int width,int height,String player,List<Control> controls,List<Line> lines,Profile profile){
        return paint(width,height,player,controls,lines,profile,null);
    }
    public static BufferedImage paint(int width,int height,String player,List<Control> controls,List<Line> lines,Profile profile,Account account){
        return paint(width,height,player,controls,lines,profile,account,true);
    }
    /** Full-width dialogs share the frame, not the workspace's sidebar decorations. */
    public static BufferedImage dialog(int width,int height,List<Control> controls,List<Line> lines){
        return paint(width,height,"",controls,lines,null,null,false);
    }
    private static BufferedImage paint(int width,int height,String player,List<Control> controls,List<Line> lines,Profile profile,Account account,boolean sidebar){
        var image=new BufferedImage(width*RES,height*RES,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();
        try{
            g.scale(RES,RES);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0,0,0,45));g.fillRoundRect(2,3,width-4,height-4,18,18);
            g.setColor(ModerationTheme.BACKGROUND);g.fillRoundRect(0,0,width-4,height-5,12,12);
            var chromeClip=g.getClip();g.clip(new java.awt.geom.RoundRectangle2D.Float(0,0,width-4,height-5,12,12));
            g.setColor(ModerationTheme.CHROME);g.fillRect(0,0,width-4,33);g.setClip(chromeClip);
            g.setColor(ModerationTheme.ACCENT);g.drawRoundRect(0,0,width-5,height-6,12,12);
            g.setColor(ModerationTheme.BORDER);g.drawLine(1,32,width-6,32);
            if(sidebar){g.drawLine(128,42,128,height-59);
            g.setColor(ModerationTheme.MUTED);g.setFont(new Font("Segoe UI",Font.PLAIN,8));g.drawString("РАЗДЕЛЫ",14,43);g.drawString("F9 · колесо",14,height-105);g.drawString("F10 · панель",14,height-91);}
            g.setFont(new Font("Segoe UI",Font.BOLD,10));g.setColor(ModerationTheme.TEXT);
            String heading="Модерация";g.drawString(heading,(width-4-g.getFontMetrics().stringWidth(heading))/2,21);
            var bounds=new ModerationWindowBounds(0,0,width,height);
            for(var raw:controls){var r=bounds.fit(raw.x,raw.y,raw.width,raw.height);var c=new Control(r.x(),r.y(),r.width(),r.height(),raw.text,raw.field,raw.active,raw.hovered,raw.focused);
                g.setColor(c.field?ModerationTheme.CHROME:(c.hovered||c.focused||c.text.startsWith("> "))&&c.active?ModerationTheme.HOVER:ModerationTheme.SURFACE);g.fillRoundRect(c.x,c.y,c.width,c.height,8,8);
                g.setColor((c.focused||c.hovered||c.text.startsWith("> "))&&c.active?ModerationTheme.ACCENT:ModerationTheme.BORDER);g.drawRoundRect(c.x,c.y,c.width-1,c.height-1,10,10);
                g.setClip(c.x+3,c.y,c.width-6,c.height);g.setFont(new Font("Segoe UI",Font.PLAIN,10));g.setColor(c.active?ModerationTheme.TEXT:ModerationTheme.MUTED);
                if(c.text.equals("←")||c.text.equals("—")){var stroke=g.getStroke();g.setStroke(new BasicStroke(1.4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));int cx=c.x+c.width/2,cy=c.y+c.height/2;g.drawLine(cx-4,cy,cx+4,cy);if(c.text.equals("←")){g.drawLine(cx-4,cy,cx,cy-4);g.drawLine(cx-4,cy,cx,cy+4);}g.setStroke(stroke);}else{String text=c.text+(c.field&&c.focused?" │":"");int x=c.field?c.x+6:c.x+(c.width-g.getFontMetrics().stringWidth(text))/2;g.drawString(text,x,c.y+(c.height+8)/2);}g.setClip(null);
            }
            for(var line:lines){g.setClip(7,70,width-17,height-75);g.setFont(new Font("Segoe UI",Font.PLAIN,10));g.setColor(new Color(line.color,true));g.drawString(line.text,line.x,line.y+9);g.setClip(null);}
            if(profile!=null){
                int px=ModerationPanelLayout.CONTENT_X,py=ModerationPanelLayout.PROFILE_Y,pw=ModerationPanelLayout.contentWidth(width);
                g.setColor(ModerationTheme.CHROME);g.fillRoundRect(px,py,pw,ModerationPanelLayout.PROFILE_H,8,8);
                g.setColor(ModerationTheme.ACCENT);g.fillRect(px,py+6,2,ModerationPanelLayout.PROFILE_H-12);
                g.setFont(new Font("Segoe UI",Font.BOLD,11));g.setColor(ModerationTheme.TEXT);g.setClip(px+10,py,pw-144,24);g.drawString(profile.name,px+10,py+16);g.setClip(null);
                g.setFont(new Font("Segoe UI",Font.PLAIN,9));g.setColor(ModerationTheme.MUTED);
                String details=profile.state+" · Пинг: "+profile.ping+" · Сессия: "+profile.session+" · Наказаний: "+profile.active;
                int start=px+10;
                while(!details.isEmpty()&&g.getFontMetrics().stringWidth(details)>width-start-16)details=details.substring(0,details.length()-1);
                g.drawString(details,start,py+35);
            }else if(!player.isBlank()){
                // Selected player is already shown in the card; keep the title centred.
            }
            if(account!=null){int y=height-40;g.setColor(account.hovered?ModerationTheme.HOVER:ModerationTheme.CHROME);g.fillRoundRect(8,y,width-20,30,10,10);g.setColor(ModerationTheme.BORDER);g.drawLine(8,y-5,width-12,y-5);InFlowBrandPainter.avatar(g,account.avatar,account.name,14,y+4,22);g.setColor(ModerationTheme.TEXT);g.setFont(new Font("Segoe UI",Font.BOLD,9));g.drawString(account.name,44,y+12);g.setColor(ModerationTheme.MUTED);g.setFont(new Font("Segoe UI",Font.PLAIN,8));g.drawString("Аккаунт модератора",44,y+24);String label="Личный кабинет  ›";g.setColor(ModerationTheme.ACCENT);g.drawString(label,width-24-g.getFontMetrics().stringWidth(label),y+18);}
        }finally{g.dispose();}return image;
    }
}
