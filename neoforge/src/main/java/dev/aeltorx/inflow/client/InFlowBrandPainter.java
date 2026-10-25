package dev.aeltorx.inflow.client;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/** Code-native brand artwork, rasterized once at 3x resolution. */
public final class InFlowBrandPainter {
    public static final int RES=3,LOGO_W=420,LOGO_H=112,WELCOME_W=440,WELCOME_H=90;
    private static Graphics2D graphics(BufferedImage image){var g=image.createGraphics();g.scale(RES,RES);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);return g;}
    public static BufferedImage window(int width,int height){var image=new BufferedImage(width*RES,height*RES,BufferedImage.TYPE_INT_ARGB);var g=graphics(image);try{
        g.setColor(new Color(0,0,0,45));g.fillRoundRect(2,3,width-4,height-4,18,18);
        g.setPaint(new GradientPaint(0,0,new Color(7,18,34),0,height,new Color(16,43,67)));g.fillRoundRect(0,0,width-4,height-5,16,16);
        g.setColor(new Color(43,56,78));g.drawRoundRect(0,0,width-5,height-6,16,16);
    }finally{g.dispose();}return image;}
    public static void mark(Graphics2D g,double x,double y,double size){
        var old=g.getTransform();g.translate(x,y);g.scale(size/72,size/72);
        g.setColor(new Color(11,27,45));g.fill(new RoundRectangle2D.Double(0,0,72,72,22,22));
        g.setPaint(new GradientPaint(5,5,new Color(73,227,219),68,68,new Color(137,114,255)));g.setStroke(new BasicStroke(6.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        var a=new Path2D.Double();a.moveTo(16,49);a.curveTo(21,46,22,22,32,22);a.curveTo(42,22,40,50,50,50);a.curveTo(55,50,57,38,58,32);g.draw(a);
        var b=new Path2D.Double();b.moveTo(16,34);b.curveTo(18,28,20,20,23,18);g.setStroke(new BasicStroke(4,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(b);g.setTransform(old);
    }
    public static BufferedImage logo(){var image=new BufferedImage(LOGO_W*RES,LOGO_H*RES,BufferedImage.TYPE_INT_ARGB);var g=graphics(image);try{
        var font=new Font("Segoe UI",Font.BOLD,46);g.setFont(font);int textWidth=g.getFontMetrics().stringWidth("InFlow"),left=(LOGO_W-(72+20+textWidth))/2;
        for(int i=14;i>0;i--){g.setColor(new Color(76,167,225,Math.max(1,12-i/2)));g.fill(new RoundRectangle2D.Double(left-i/2.,20-i/2.,72+i,72+i,22+i,22+i));}
        mark(g,left,20,72);int tx=left+92;g.setColor(new Color(242,249,255));g.drawString("In",tx,73);tx+=g.getFontMetrics().stringWidth("In");
        var outline=font.createGlyphVector(g.getFontRenderContext(),"Flow").getOutline(tx,73);g.setPaint(new GradientPaint(tx,35,new Color(100,231,224),tx+150,85,new Color(154,145,255)));g.fill(outline);
    }finally{g.dispose();}return image;}
    public static void avatar(Graphics2D g,BufferedImage avatar,String name,int x,int y,int size){var clip=g.getClip();g.clip(new Ellipse2D.Double(x,y,size,size));g.setColor(new Color(30,67,86));g.fillOval(x,y,size,size);if(avatar!=null)g.drawImage(avatar,x,y,size,size,null);else{g.setColor(new Color(143,237,225));g.setFont(new Font("Segoe UI",Font.BOLD,Math.max(9,size/2)));String initial=name.isEmpty()?"M":name.substring(0,1).toUpperCase(java.util.Locale.ROOT);var fm=g.getFontMetrics();g.drawString(initial,x+(size-fm.stringWidth(initial))/2,y+(size-fm.getHeight())/2+fm.getAscent());}g.setClip(clip);}
    public static BufferedImage welcome(String name){return welcome(name,null);}
    public static BufferedImage welcome(String name,BufferedImage avatar){var image=new BufferedImage(WELCOME_W*RES,WELCOME_H*RES,BufferedImage.TYPE_INT_ARGB);var g=graphics(image);try{
        g.setColor(new Color(18,36,55,235));g.fillRoundRect(99,22,242,46,14,14);g.setColor(new Color(85,168,196,100));g.drawRoundRect(99,22,242,46,14,14);
        avatar(g,avatar,name,111,31,28);
        g.setFont(new Font("Segoe UI",Font.PLAIN,9));g.setColor(new Color(166,192,213));g.drawString("Добро пожаловать,",149,41);g.setFont(new Font("Segoe UI",Font.BOLD,13));g.setColor(new Color(242,248,255));g.setClip(148,44,181,21);g.drawString(name,148,57);g.setClip(null);
    }finally{g.dispose();}return image;}
    public static BufferedImage flowerPetal(){var image=new BufferedImage(80*RES,80*RES,BufferedImage.TYPE_INT_ARGB);var g=graphics(image);try{
        var petal=new Path2D.Double();petal.moveTo(40,43);petal.curveTo(31,38,20,24,25,14);petal.curveTo(28,7,35,6,40,13);petal.curveTo(45,6,52,7,55,14);petal.curveTo(60,24,49,38,40,43);petal.closePath();
        g.setPaint(new GradientPaint(40,8,new Color(255,236,246),40,43,new Color(237,121,174)));g.fill(petal);
        g.setColor(new Color(255,247,252,180));g.setStroke(new BasicStroke(.8f));g.draw(petal);
        g.setColor(new Color(201,75,138,90));g.setStroke(new BasicStroke(.7f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(new QuadCurve2D.Double(40,40,38,29,40,20));
    }finally{g.dispose();}return image;}
    public static BufferedImage flowerCenter(){var image=new BufferedImage(80*RES,80*RES,BufferedImage.TYPE_INT_ARGB);var g=graphics(image);try{
        g.setPaint(new RadialGradientPaint(40,40,8,new float[]{0,1},new Color[]{new Color(255,243,248),new Color(246,122,174,0)}));g.fillOval(32,32,16,16);
        for(int i=0;i<5;i++){double a=i*Math.PI*2/5-Math.PI/2;double x=40+5*Math.cos(a),y=40+5*Math.sin(a);g.setColor(new Color(201,62,123));g.setStroke(new BasicStroke(.8f));g.draw(new Line2D.Double(40,40,x,y));g.setColor(new Color(255,187,216));g.fill(new Ellipse2D.Double(x-1.25,y-1.25,2.5,2.5));}
        g.setColor(new Color(255,224,238));g.fillOval(38,38,4,4);
    }finally{g.dispose();}return image;}
}
