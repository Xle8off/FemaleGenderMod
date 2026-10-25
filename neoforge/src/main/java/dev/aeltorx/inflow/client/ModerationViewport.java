package dev.aeltorx.inflow.client;

/** One immutable logical canvas for every moderation dialog and its input coordinates. */
public record ModerationViewport(double x,double y,double scale) {
    public static final int WIDTH=584, HEIGHT=456;
    public static ModerationViewport of(int width,int height){
        double scale=Math.max(.001,Math.min(1,Math.min(Math.max(1,width-16)/(double)WIDTH,Math.max(1,height-16)/(double)HEIGHT)));
        return new ModerationViewport((width-WIDTH*scale)/2,(height-HEIGHT*scale)/2,scale);
    }
    public double localX(double screenX){return (screenX-x)/scale;}
    public double localY(double screenY){return (screenY-y)/scale;}
    public boolean contains(double screenX,double screenY){return localX(screenX)>=0&&localX(screenX)<WIDTH&&localY(screenY)>=0&&localY(screenY)<HEIGHT;}
}
