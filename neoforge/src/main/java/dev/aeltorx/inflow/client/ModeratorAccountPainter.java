package dev.aeltorx.inflow.client;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class ModeratorAccountPainter {
    public static BufferedImage paint(int w,int h,String name,BufferedImage avatar,java.util.List<PanelPainter.Control> controls,String status){
        var image=PanelPainter.dialog(w,h,controls,java.util.List.of());var g=image.createGraphics();try{g.scale(3,3);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int cy=Math.min(95,h/3);InFlowBrandPainter.avatar(g,avatar,name,w/2-25,cy-25,50);g.setColor(new Color(232,241,252));g.setFont(new Font("Segoe UI",Font.BOLD,13));g.drawString(name,(w-g.getFontMetrics().stringWidth(name))/2,cy+47);g.setColor(new Color(157,181,205));g.setFont(new Font("Segoe UI",Font.PLAIN,9));String label="Локальный профиль модератора";g.drawString(label,(w-g.getFontMetrics().stringWidth(label))/2,cy+64);g.setFont(new Font("Segoe UI",Font.PLAIN,8));g.setClip(14,h-78,w-32,27);g.drawString(status,14,h-63);g.setClip(null);
        }finally{g.dispose();}return image;
    }
}
