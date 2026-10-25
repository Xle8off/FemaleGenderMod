package com.wildfire.client.overhead;

/** Clockwise in billboard GUI coordinates, matching text/sticker quads after Y inversion. */
public final class CameraCircleGeometry {
    public static final int SEGMENTS=48;
    private CameraCircleGeometry(){}
    public static double angle(int edge){return -edge*Math.PI*2/SEGMENTS;}
}
