package com.wildfire.client.music;

/** Model-space elbow with a two-pixel circular transition, shared by skin and outer layer. */
public final class MenuArmPose {
    public record Bend(float y, float z, float angle) {}
    private MenuArmPose() {}
    public static Bend sample(float y, float angle) {
        if (!Float.isFinite(y) || !Float.isFinite(angle)) throw new IllegalArgumentException("Non-finite elbow pose");
        if (Math.abs(angle) < .0001f || y <= 3f) return new Bend(y, 0, 0);
        float k=angle/2f, curved=Math.min(y-3f,2f), theta=k*curved;
        float cy=3f+(float)Math.sin(theta)/k, cz=(1f-(float)Math.cos(theta))/k;
        if(y>5f) { cy+=(y-5f)*(float)Math.cos(angle); cz+=(y-5f)*(float)Math.sin(angle); }
        return new Bend(cy,cz,theta);
    }
}
