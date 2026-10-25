package com.wildfire.client.screens;
import net.springplus.screens.ScreenGeometry;
/** Audio distance is measured from the face centre, never from its nearest edge. */
public final class ScreenAudioMath {
 private ScreenAudioMath(){}
 public static double distance(ScreenGeometry g,double x,double y,double z){
  double cx=g.x()+(g.maxX()-g.x()+1)/2d,cy=g.y()+(g.maxY()-g.y()+1)/2d,cz=g.z()+(g.maxZ()-g.z()+1)/2d;
  switch(g.face()){case NORTH->cz=g.z();case SOUTH->cz=g.z()+1;case WEST->cx=g.x();case EAST->cx=g.x()+1;case DOWN->cy=g.y();case UP->cy=g.y()+1;}
  return Math.sqrt((x-cx)*(x-cx)+(y-cy)*(y-cy)+(z-cz)*(z-cz));
 }
 public static float gain(double distance){if(!Double.isFinite(distance)||distance<0||distance>=48)return 0;double t=1-distance/48;return (float)(t*t);}
}
