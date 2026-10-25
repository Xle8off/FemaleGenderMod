package dev.aeltorx.inflow.client;

import java.awt.Shape;
import java.awt.geom.*;
/** One radial geometry for painting, GPU atlas pieces and mouse hit testing. */
public final class WheelGridLayout {
 public static final int WIDTH=560,HEIGHT=440,ACTIONS=7,BACK=7,PREVIOUS=BACK,NEXT=8,CLOSE=9,COUNT=10;
 public static final int CELL=100,LEFT=130,TOP=50,CX=280,CY=200,RADIUS=150,INNER=67,SUMMARY_CHARS=80,SUMMARY_LINES=4;
 public static final int ATLAS_CELL=136,ATLAS_FOOTER=50+3*ATLAS_CELL,ATLAS_HEIGHT=ATLAS_FOOTER+90;
 public record Tile(int x,int y,int width,int height){public boolean contains(double x,double y){return x>=this.x&&y>=this.y&&x<this.x+width&&y<this.y+height;}}
 private WheelGridLayout(){}
 private static final java.util.Map<Integer,java.util.List<int[]>> HOVER_ROWS=new java.util.HashMap<>();
 public static java.util.List<int[]> hoverRows(int index){return HOVER_ROWS.computeIfAbsent(index,key->{var shape=shape(key);var b=shape.getBounds();var rows=new java.util.ArrayList<int[]>();for(int y=b.y;y<b.y+b.height;y++){int first=-1,last=-1;for(int x=b.x;x<b.x+b.width;x++)if(shape.contains(x+.5,y+.5)){if(first<0)first=x;last=x;}if(first>=0)rows.add(new int[]{first,y,last+1});}return java.util.List.copyOf(rows);});}
 private static int sectorFor(int index){return index<4?index:index<ACTIONS?index+1:4;}
 public static int revealOrder(int index){return index==BACK||index==NEXT?4:index<ACTIONS?sectorFor(index):-1;}
 private static double angle(int index){return -90+sectorFor(index)*45+(index==BACK?11.25:index==NEXT?-11.25:0);}
 public static Tile tile(int index){
  if(index<0||index>=COUNT)throw new IllegalArgumentException("Unknown action");
  if(index==CLOSE)return new Tile(408,13,20,20);
  double a=Math.toRadians(angle(index));int radius=index<ACTIONS?110:112;
  int w=index<ACTIONS?76:42,h=index<ACTIONS?76:64;
  return new Tile((int)Math.round(CX+Math.cos(a)*radius)-w/2,(int)Math.round(CY+Math.sin(a)*radius)-h/2,w,h);
 }
 public static Shape shape(int index){
  if(index==CLOSE){var r=tile(index);return new RoundRectangle2D.Double(r.x(),r.y(),r.width(),r.height(),8,8);}
  double half=index<ACTIONS?22.5:11.25;
  var area=new Area(new Arc2D.Double(CX-RADIUS,CY-RADIUS,RADIUS*2,RADIUS*2,-angle(index)-half,half*2,Arc2D.PIE));
  area.subtract(new Area(new Ellipse2D.Double(CX-INNER,CY-INNER,INNER*2,INNER*2)));return area;
 }
 public static Shape atlasShape(int slot){
  if(slot==8)return new Ellipse2D.Double(CX-INNER,CY-INNER,INNER*2,INNER*2);
  if(slot<7)return shape(slot);
  var area=new Area(shape(BACK));area.add(new Area(shape(NEXT)));return area;
 }
 public static Tile atlasBounds(int slot){var r=atlasShape(slot).getBounds();return new Tile(r.x-1,r.y-1,r.width+2,r.height+2);}
 public static boolean insideWheel(double x,double y){return Math.hypot(x-CX,y-CY)<RADIUS;}
 public static int hit(double x,double y){
  if(shape(CLOSE).contains(x,y))return CLOSE;
  double distance=Math.hypot(x-CX,y-CY);if(distance<INNER||distance>=RADIUS)return -1;
  int sector=(int)Math.floor((Math.toDegrees(Math.atan2(y-CY,x-CX))+112.5+360)%360/45);
  return sector==4?(x<CX?BACK:NEXT):sector<4?sector:sector-1;
 }
}
