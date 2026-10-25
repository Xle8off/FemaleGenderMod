package dev.aeltorx.inflow.client;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

public final class StaffProfilePainter {
    public record Profile(String name,String about,boolean online,long checks,long cases,BufferedImage avatar,BufferedImage banner){}
    public static BufferedImage paint(int w,int h,String title,List<PanelPainter.Control> controls,List<PanelPainter.Line> lines,Profile profile){var image=PanelPainter.dialog(w,h,controls,lines);var g=image.createGraphics();try{g.scale(3,3);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setClip(70,5,Math.max(1,w-191),26);g.setColor(ModerationTheme.CHROME);g.fillRect(70,5,w-191,26);g.setFont(new Font("Segoe UI",Font.BOLD,10));g.setColor(ModerationTheme.TEXT);g.drawString(title,(w-4-g.getFontMetrics().stringWidth(title))/2,21);g.setClip(null);
        if(profile!=null){var old=g.getClip();g.clip(new RoundRectangle2D.Double(12,37,w-28,62,12,12));g.setPaint(new GradientPaint(12,37,new Color(26,80,95),w-16,99,new Color(77,54,103)));g.fillRect(12,37,w-28,62);if(profile.banner!=null){int iw=profile.banner.getWidth(),ih=profile.banner.getHeight();double scale=Math.max((w-28)/(double)iw,62./ih);int cw=(int)Math.ceil((w-28)/scale),ch=(int)Math.ceil(62/scale),sx=(iw-cw)/2,sy=(ih-ch)/2;g.drawImage(profile.banner,12,37,w-16,99,sx,sy,sx+cw,sy+ch,null);}g.setClip(old);InFlowBrandPainter.avatar(g,profile.avatar,profile.name,24,80,38);g.setColor(new Color(231,241,251));g.setFont(new Font("Segoe UI",Font.BOLD,13));g.drawString(profile.name,73,116);g.setFont(new Font("Segoe UI",Font.PLAIN,8));g.setColor(profile.online?new Color(126,218,172):new Color(155,174,197));g.drawString(profile.online?"В сети":"Не в сети",w-74,115);
        g.setColor(new Color(161,183,207));g.setFont(new Font("Segoe UI",Font.PLAIN,9));g.setClip(14,125,w-31,31);wrap(g,profile.about,14,135,w-31,11,2);g.setClip(null);g.setColor(new Color(139,211,225));g.setFont(new Font("Segoe UI",Font.PLAIN,8));g.drawString("Активных проверок: "+profile.checks+"     Открытых дел: "+profile.cases,14,172);}
    }finally{g.dispose();}return image;}
    public static List<String> lines(String text,int characters){var out=new java.util.ArrayList<String>();for(String paragraph:text.replace('\t',' ').split("\n",-1)){if(paragraph.isEmpty())out.add("");while(!paragraph.isEmpty()){int n=Math.min(characters,paragraph.length());if(n<paragraph.length()){int space=paragraph.lastIndexOf(' ',n);if(space>n/2)n=space;}out.add(paragraph.substring(0,n));paragraph=paragraph.substring(n).stripLeading();}}return out;}
    private static void wrap(Graphics2D g,String text,int x,int y,int width,int step,int max){for(String line:lines(text,Math.max(1,width/6))){if(max--<=0)break;g.drawString(line,x,y);y+=step;}}
}
