package com.wildfire.client.overhead;

import java.util.*;

/** Camera mode validation shared by capture and its dropdowns. */
public final class CameraOptions {
    public static final int MAX_WIDTH=854,MAX_HEIGHT=480,MAX_FPS=30;
    public record Mode(int width,int height,int fps){
        public boolean allowed(){return width>=16&&height>=16&&width<=MAX_WIDTH&&height<=MAX_HEIGHT&&fps>0&&fps<=MAX_FPS;}
        public String resolution(){return width+" × "+height;}
    }
    private CameraOptions(){}
    public static List<Mode> allowed(Collection<Mode> modes){
        return modes.stream().filter(Mode::allowed).distinct().sorted(Comparator.comparingInt((Mode m)->m.width*m.height).reversed().thenComparing(Comparator.comparingInt(Mode::fps).reversed())).toList();
    }
    public static int[] fit(int width,int height){
        if(width<1||height<1||width>8192||height>8192)throw new IllegalArgumentException("Invalid frame dimensions");
        double scale=Math.min(1,Math.min(MAX_WIDTH/(double)width,MAX_HEIGHT/(double)height));
        return new int[]{Math.max(2,(int)(width*scale)/2*2),Math.max(2,(int)(height*scale)/2*2)};
    }
}
