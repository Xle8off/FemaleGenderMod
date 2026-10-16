package com.wildfire.client.music;
import java.util.*;

/** Single coplanar mesh: every panel pixel belongs to exactly one colored rectangle. */
final class ListeningCardMesh {
    static final int HEIGHT = 32;
    record Rect(int left, int top, int right, int bottom, int color) {}
    private record Run(int left, int right, int color) {}
    private ListeningCardMesh() {}
    static List<Rect> build(int width, int accent) {
        if (width < 128 || width > 176) throw new IllegalArgumentException("Card width");
        List<Rect> result = new ArrayList<>();
        Map<Run, Rect> previous = new HashMap<>();
        for (int y=0; y<HEIGHT; y++) {
            Map<Run, Rect> current = new HashMap<>();
            for (int x=0; x<width;) {
                int color = color(x,y,width,accent), end=x+1;
                while(end<width && color(end,y,width,accent)==color) end++;
                if(color!=0) {
                    Run run = new Run(x,end,color);
                    Rect old=previous.remove(run);
                    current.put(run,new Rect(x,old==null?y:old.top,end,y+1,color));
                }
                x=end;
            }
            result.addAll(previous.values()); previous=current;
        }
        result.addAll(previous.values());
        return List.copyOf(result);
    }
    static int color(int x,int y,int width,int accent) {
        if(!inside(x,y,width,HEIGHT,5)) return 0;
        if(!inside(x-1,y-1,width-2,HEIGHT-2,4)) return 0xFF3E4757;
        if(x>=2 && x<4 && y>=5 && y<HEIGHT-5) return accent;
        // Pixel-drawn platform marks are part of the same disjoint mesh.
        // Provider logos use bundled textures on a separate, slightly raised plane.
        if (accent == 0xFF1DB954 || accent == 0xFFFFCC33 || accent == 0xFFFF3434) return 0xF01D222B;
        if (accent == 0xFFFF5500) {
            int ix=x-10, iy=y-8;
            boolean bars = ix>=0 && ix<13 && ix%2==0 && iy>=11-(2+(ix/2)/2) && iy<12;
            boolean cloud = (ix>=7&&ix<13&&iy>=5&&iy<12) || (ix>=5&&ix<16&&iy>=8&&iy<12);
            return bars || cloud ? accent : 0xF01D222B;
        }
        boolean stem=x>=17&&x<19&&y>=10&&y<24;
        boolean flag=x>=18&&x<24&&y>=10&&y<13;
        double dx=(x-15.5)/3.5, dy=(y-23.5)/2.5;
        boolean head=dx*dx+dy*dy<=1;
        if(stem||flag||head) return accent;
        return 0xF01D222B;
    }
    private static boolean inside(int x,int y,int w,int h,int radius) {
        if(x<0||y<0||x>=w||y>=h)return false;
        double cx=Math.max(radius,Math.min(w-radius,x+.5));
        double cy=Math.max(radius,Math.min(h-radius,y+.5));
        double dx=x+.5-cx,dy=y+.5-cy;
        return dx*dx+dy*dy<=radius*radius;
    }
}
