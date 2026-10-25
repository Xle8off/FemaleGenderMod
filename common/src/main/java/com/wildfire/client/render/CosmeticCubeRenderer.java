package com.wildfire.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.model.geom.ModelPart.Polygon;
import net.minecraft.client.model.geom.ModelPart.Vertex;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CosmeticCubeRenderer {
   private CosmeticCubeRenderer() {
   }

   public static void mark(Cube cube) {
      if (cube instanceof CosmeticCubeRenderer.CustomAtlas custom) {
         custom.springplus$markCustomAtlas();
      }
   }

   public static void compile(Cube cube, Pose pose, VertexConsumer vertices, int light, int overlay, int color) {
      Matrix4f matrix = pose.pose();
      Vector3f scratch = new Vector3f();

      for (Polygon polygon : cube.polygons) {
         if (polygon.vertices().length != 0) {
            pose.transformNormal(polygon.normal(), scratch);
            float nx = scratch.x;
            float ny = scratch.y;
            float nz = scratch.z;

            for (Vertex vertex : polygon.vertices()) {
               matrix.transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), scratch);
               vertices.addVertex(scratch.x, scratch.y, scratch.z, color, vertex.u(), vertex.v(), overlay, light, nx, ny, nz);
            }
         }
      }
   }

   public interface CustomAtlas {
      void springplus$markCustomAtlas();
   }
}
