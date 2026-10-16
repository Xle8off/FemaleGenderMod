package com.wildfire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/** Draws updated polygons, rather than an optimizer's constructor-time Box UV cache. */
public final class CosmeticCubeRenderer {
    private CosmeticCubeRenderer() {}
    public interface CustomAtlas { void springplus$markCustomAtlas(); }
    public static void mark(ModelPart.Cube cube) {
        if (cube instanceof CustomAtlas custom) custom.springplus$markCustomAtlas();
    }
    public static void compile(ModelPart.Cube cube, PoseStack.Pose pose, VertexConsumer vertices,
                               int light, int overlay, int color) {
        var matrix = pose.pose();
        var scratch = new Vector3f();
        for (var polygon : cube.polygons) {
            if (polygon.vertices().length == 0) continue;
            pose.transformNormal(polygon.normal(), scratch);
            float nx = scratch.x, ny = scratch.y, nz = scratch.z;
            for (var vertex : polygon.vertices()) {
                matrix.transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), scratch);
                vertices.addVertex(scratch.x, scratch.y, scratch.z, color, vertex.u(), vertex.v(),
                    overlay, light, nx, ny, nz);
            }
        }
    }
}
