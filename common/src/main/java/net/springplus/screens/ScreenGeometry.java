package net.springplus.screens;

/** Inclusive block-face rectangle. No Bukkit/Minecraft dependencies. */
public record ScreenGeometry(int x,int y,int z,int width,int height,Face face) {
 public static final int MAX_WIDTH=48,MAX_HEIGHT=24;
 public enum Face {DOWN,UP,NORTH,SOUTH,WEST,EAST}
 public ScreenGeometry {
  if(face==null||width<1||width>MAX_WIDTH||height<1||height>MAX_HEIGHT)throw new IllegalArgumentException("Максимальный размер экрана — 48×24 блока");
  check(x);check(y);check(z);
  check((long)x+(face==Face.WEST||face==Face.EAST?0:width-1));
  check((long)y+(face==Face.UP||face==Face.DOWN?0:height-1));
  check((long)z+(face==Face.WEST||face==Face.EAST?width-1:face==Face.UP||face==Face.DOWN?height-1:0));
 }
 private static void check(long value){if(value < -30_000_000||value>30_000_000)throw new IllegalArgumentException("Координаты вне допустимых границ");}
 public static ScreenGeometry select(int ax,int ay,int az,Face a,int bx,int by,int bz,Face b){
  if(a==null||a!=b)throw new IllegalArgumentException("Выберите одну и ту же сторону блоков");
  boolean horizontal=a==Face.UP||a==Face.DOWN,side=a==Face.WEST||a==Face.EAST;
  if(horizontal?ay!=by:side?ax!=bx:az!=bz)throw new IllegalArgumentException("Углы должны находиться в одной плоскости");
  long w=Math.abs((long)(side?az:ax)-(side?bz:bx))+1,h=Math.abs((long)(horizontal?az:ay)-(horizontal?bz:by))+1;
  if(w>MAX_WIDTH||h>MAX_HEIGHT)throw new IllegalArgumentException("Максимальный размер экрана — 48×24 блока");
  return new ScreenGeometry(Math.min(ax,bx),Math.min(ay,by),Math.min(az,bz),(int)w,(int)h,a);
 }
 public int maxX(){return x+(face==Face.WEST||face==Face.EAST?0:width-1);}
 public int maxY(){return y+(face==Face.UP||face==Face.DOWN?0:height-1);}
 public int maxZ(){return z+(face==Face.WEST||face==Face.EAST?width-1:face==Face.UP||face==Face.DOWN?height-1:0);}
 public boolean overlaps(ScreenGeometry other){return face==other.face&&x<=other.maxX()&&maxX()>=other.x&&y<=other.maxY()&&maxY()>=other.y&&z<=other.maxZ()&&maxZ()>=other.z;}
 public boolean contains(int bx,int by,int bz,Face side){return face==side&&bx>=x&&bx<=maxX()&&by>=y&&by<=maxY()&&bz>=z&&bz<=maxZ();}
 public double distanceSquared(double px,double py,double pz){double dx=Math.max(x-px,Math.max(0,px-maxX()-1)),dy=Math.max(y-py,Math.max(0,py-maxY()-1)),dz=Math.max(z-pz,Math.max(0,pz-maxZ()-1));return dx*dx+dy*dy+dz*dz;}
}
