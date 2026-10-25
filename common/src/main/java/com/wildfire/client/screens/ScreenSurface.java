package com.wildfire.client.screens;
import net.springplus.screens.ScreenGeometry;
/** Top-left pixel origin, as viewed from the selected face. All six sides share one UV convention. */
public final class ScreenSurface {
 public record Point(double x,double y,double z){}
 private ScreenSurface(){}
 public static Point point(ScreenGeometry g,double u,double v){
  return point(g,u,v,.002);
 }
 public static Point point(ScreenGeometry g,double u,double v,double offset){
  double x=g.x(),y=g.y(),z=g.z(),w=g.width(),h=g.height();
  return switch(g.face()){
   case SOUTH->new Point(x+u,y+h-v,z+1+offset);
   case NORTH->new Point(x+w-u,y+h-v,z-offset);
   case EAST->new Point(x+1+offset,y+h-v,z+w-u);
   case WEST->new Point(x-offset,y+h-v,z+u);
   case UP->new Point(x+u,y+1+offset,z+v);
   case DOWN->new Point(x+u,y-offset,z+h-v);
  };
 }
 public static boolean front(ScreenGeometry g,double x,double y,double z){var c=point(g,g.width()/2.0,g.height()/2.0);return switch(g.face()){case SOUTH->z>c.z;case NORTH->z<c.z;case EAST->x>c.x;case WEST->x<c.x;case UP->y>c.y;case DOWN->y<c.y;};}
}
