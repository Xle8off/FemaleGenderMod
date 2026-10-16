package com.wildfire.gui;

/** Area filtering in premultiplied alpha: small transparent details do not shimmer or gain dark halos. */
public final class StickerResampler {
    private StickerResampler() {}
    public static int[] dimensions(int width, int height, int edge) {
        if(width<1||height<1||width>512||height>512||edge<1)throw new IllegalArgumentException("Image dimensions");
        double scale=Math.min(1,(double)edge/Math.max(width,height));
        return new int[]{Math.max(1,(int)Math.round(width*scale)),Math.max(1,(int)Math.round(height*scale))};
    }
    public static int[] resize(int[] pixels,int width,int height,int targetWidth,int targetHeight) {
        if(width<1||height<1||width>512||height>512||pixels.length!=width*height
            ||targetWidth<1||targetHeight<1||targetWidth>width||targetHeight>height)throw new IllegalArgumentException("Image dimensions");
        if(width==targetWidth&&height==targetHeight)return pixels.clone();
        int[] output=new int[targetWidth*targetHeight];
        double sx=(double)width/targetWidth,sy=(double)height/targetHeight;
        for(int y=0;y<targetHeight;y++)for(int x=0;x<targetWidth;x++){
            double left=x*sx,right=(x+1)*sx,top=y*sy,bottom=(y+1)*sy;
            double alpha=0,red=0,green=0,blue=0;
            for(int yy=(int)top;yy<Math.ceil(bottom);yy++)for(int xx=(int)left;xx<Math.ceil(right);xx++){
                double weight=(Math.min(xx+1,right)-Math.max(xx,left))*(Math.min(yy+1,bottom)-Math.max(yy,top));
                int pixel=pixels[Math.min(yy,height-1)*width+Math.min(xx,width-1)];
                double a=(pixel>>>24)*weight;
                alpha+=a;red+=((pixel>>>16)&255)*a;green+=((pixel>>>8)&255)*a;blue+=(pixel&255)*a;
            }
            int a=(int)Math.round(alpha/(sx*sy));
            output[y*targetWidth+x]=a==0?0:(a<<24)|((int)Math.round(red/alpha)<<16)|((int)Math.round(green/alpha)<<8)|(int)Math.round(blue/alpha);
        }
        return output;
    }
}

