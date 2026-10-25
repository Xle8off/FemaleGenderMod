package dev.aeltorx.inflow.client;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;
/** Circular wheel. Rendering, animation masks and hit testing share exact sector geometry. */
public final class WheelPainter {
 public static final int SIZE=560,RES=3;
 private WheelPainter(){}
 public static BufferedImage paint(String t,List<String> l,int h,boolean c,List<String> s,String target,String m,int p,int ps){return paint(t,l,h,c,s,target,m,p,ps,0,0);}
 public static BufferedImage paint(String t,List<String> l,int h,boolean c,List<String> s,String target,String m,int p,int ps,int phase){return paint(t,l,h,c,s,target,m,p,ps,phase,0);}
 public static BufferedImage paint(String t,List<String> l,int h,boolean c,List<String> s,String target,String m,int p,int ps,int phase,double rotation){
  return paint(t,l,h,c,s,target,m,p,ps,l.stream().map(WheelIcons::forLabel).toList(),java.util.stream.IntStream.range(0,l.size()).mapToObj(i->!l.get(i).isBlank()&&!(i==WheelGridLayout.BACK&&p==0||i==WheelGridLayout.NEXT&&p+1>=ps)).toList());
 }
 public static BufferedImage paint(String title,List<String> labels,int hover,boolean confirm,List<String> summary,String target,String message,int page,int pages,List<WheelIcons.Kind> icons,List<Boolean> flags){
  var image=new BufferedImage(560*RES,440*RES,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
  try{
   g.scale(RES,RES);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
   text(g,title,280,28,13,400,ModerationTheme.TEXT);
   g.setColor(new Color(21,26,34,235));g.fill(WheelGridLayout.atlasShape(8));
   text(g,target.isBlank()?"Страница":target,280,170,9,110,ModerationTheme.MUTED);
   text(g,(page+1)+" / "+pages,280,203,13,110,ModerationTheme.TEXT);
   String selected=hover>=0&&hover<7&&hover<labels.size()?labels.get(hover):confirm?"Подтверждение":"Выбери действие";
   var caption=labelLines(g,selected,8,100);for(int n=0;n<Math.min(2,caption.size());n++)text(g,caption.get(n),280,226+n*11,8,100,ModerationTheme.TEXT);
   for(int i=0;i<WheelGridLayout.CLOSE;i++){
    var shape=WheelGridLayout.shape(i);var r=WheelGridLayout.tile(i);String label=i<labels.size()?labels.get(i):"";
    boolean enabled=i<flags.size()&&flags.get(i),hot=enabled&&hover==i;
    g.setColor(hot?ModerationTheme.HOVER:new Color(21,26,34,220));g.fill(shape);
    var clip=g.getClip();g.clip(shape);
    int cx=r.x()+r.width()/2,cy=r.y()+r.height()/2;
    if(i<7&&!label.isBlank()){
     var lines=labelLines(g,label,9,76);int count=Math.min(3,lines.size());g.setFont(new Font("Segoe UI",Font.PLAIN,9));var fm=g.getFontMetrics();
     int th=fm.getAscent()+fm.getDescent()+Math.max(0,count-1)*11,top=cy-(28+6+th)/2;
     WheelIcons.drawCentered(g,i<icons.size()?icons.get(i):WheelIcons.forLabel(label),cx,top+14);
     for(int n=0;n<count;n++)text(g,lines.get(n),cx,top+34+fm.getAscent()+n*11,9,76,enabled?ModerationTheme.TEXT:ModerationTheme.MUTED);
    }else if(i>=7){
     g.setColor(enabled?ModerationTheme.ACCENT:ModerationTheme.MUTED);g.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));int d=i==WheelGridLayout.BACK?-1:1;
     g.drawLine(cx-7,cy-9,cx+7,cy-9);g.drawLine(cx+d*7,cy-9,cx,cy-16);g.drawLine(cx+d*7,cy-9,cx,cy-2);
     text(g,i==WheelGridLayout.BACK?"Назад":"Вперёд",cx,cy+14,8,40,enabled?ModerationTheme.TEXT:ModerationTheme.MUTED);
    }
    g.setClip(clip);
   }
   g.setStroke(new BasicStroke(1));g.setColor(ModerationTheme.BORDER);
   for(int i=0;i<8;i++){double a=Math.toRadians(-112.5+i*45);g.draw(new Line2D.Double(280+Math.cos(a)*67,200+Math.sin(a)*67,280+Math.cos(a)*149.5,200+Math.sin(a)*149.5));}
   g.draw(new Line2D.Double(280,267,280,349.5));g.draw(new Ellipse2D.Double(130.5,50.5,299,299));g.draw(new Ellipse2D.Double(213,133,134,134));
   var close=WheelGridLayout.tile(WheelGridLayout.CLOSE);boolean hot=hover==WheelGridLayout.CLOSE;
   g.setColor(hot?ModerationTheme.ACCENT:ModerationTheme.BORDER);g.fillRoundRect(close.x(),close.y(),20,20,8,8);g.setColor(hot?ModerationTheme.HOVER:ModerationTheme.SURFACE);g.fillRoundRect(close.x()+1,close.y()+1,18,18,6,6);
   g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(ModerationTheme.TEXT);double cx=close.x()+10,cy=close.y()+10;g.draw(new Line2D.Double(cx-3.5,cy-3.5,cx+3.5,cy+3.5));g.draw(new Line2D.Double(cx+3.5,cy-3.5,cx-3.5,cy+3.5));
   g.setColor(new Color(21,26,34,220));g.fillRoundRect(14,360,532,68,10,10);var lines=message.isBlank()?summary:StaffProfilePainter.lines(message,WheelGridLayout.SUMMARY_CHARS);
   for(int i=0;i<Math.min(4,lines.size());i++)text(g,lines.get(i),280,374+i*13,9,518,message.isBlank()?ModerationTheme.TEXT:new Color(255,193,126));
   text(g,"Колесо мыши: страницы · ПКМ: назад · Esc: закрыть",280,436,8,532,ModerationTheme.MUTED);
  }finally{g.dispose();}return image;
 }
 /** Isolated cached sectors prevent neighbouring segments leaking during GPU reveal/close. */
 public static BufferedImage atlas(BufferedImage source){
  var image=new BufferedImage(560*RES,WheelGridLayout.ATLAS_HEIGHT*RES,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
  try{g.drawImage(source,0,0,560*RES,50*RES,0,0,560*RES,50*RES,null);g.drawImage(source,0,WheelGridLayout.ATLAS_FOOTER*RES,560*RES,WheelGridLayout.ATLAS_HEIGHT*RES,0,350*RES,560*RES,440*RES,null);for(int slot=0;slot<9;slot++){
   var b=WheelGridLayout.atlasBounds(slot);if(b.width()>WheelGridLayout.ATLAS_CELL||b.height()>WheelGridLayout.ATLAS_CELL)throw new IllegalStateException("Wheel atlas cell too small");
   var piece=(Graphics2D)g.create();try{piece.scale(RES,RES);piece.translate((slot%4)*WheelGridLayout.ATLAS_CELL-b.x(),50+(slot/4)*WheelGridLayout.ATLAS_CELL-b.y());piece.clip(WheelGridLayout.atlasShape(slot));piece.drawImage(source,0,0,560,440,null);}finally{piece.dispose();}
  }}finally{g.dispose();}return image;
 }
    private static void text(Graphics2D g,String value,int x,int y,int size,int width,Color color){
        g.setFont(new Font("Segoe UI",Font.PLAIN,size));g.setColor(color);String text=value;
        while(!text.isEmpty()&&g.getFontMetrics().stringWidth(text)>width)text=text.substring(0,text.offsetByCodePoints(text.length(),-1));
        g.drawString(text,x-g.getFontMetrics().stringWidth(text)/2,y);
    }
    private static List<String> labelLines(Graphics2D g,String value,int size,int width){
        g.setFont(new Font("Segoe UI",Font.PLAIN,size));var out=new java.util.ArrayList<String>();String current="";
        for(String word:value.split("\\s+")){
            if(!current.isEmpty()&&g.getFontMetrics().stringWidth(current+" "+word)>width){out.add(current);current="";}
            while(g.getFontMetrics().stringWidth(word)>width){
                int cut=word.length();while(cut>1&&g.getFontMetrics().stringWidth(word.substring(0,cut))>width)cut=word.offsetByCodePoints(cut,-1);
                out.add(word.substring(0,cut));word=word.substring(cut);
            }
            current=current.isEmpty()?word:current+" "+word;
        }
        if(!current.isEmpty())out.add(current);return out;
    }
}
