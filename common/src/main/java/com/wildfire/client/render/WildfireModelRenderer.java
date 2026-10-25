package com.wildfire.client.render;

import com.google.common.base.Preconditions;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.wildfire.api.uvs.UVDirection;
import com.wildfire.api.uvs.UVLayout;
import com.wildfire.api.uvs.UVQuad;
import com.wildfire.client.appearance.BreastShape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

public final class WildfireModelRenderer {
   private WildfireModelRenderer() {
      throw new UnsupportedOperationException();
   }

   public static void renderBox(WildfireModelRenderer.ModelBox model, Pose entry, VertexConsumer vertexConsumer, int light, int overlay, int color) {
      renderBox(model, entry, vertexConsumer, light, overlay, color, 0.0F, 0.0F);
   }

   public static void renderBox(
      WildfireModelRenderer.ModelBox model,
      Pose entry,
      VertexConsumer vertexConsumer,
      int light,
      int overlay,
      int color,
      float displacementX,
      float displacementY
   ) {
      Matrix4f matrix4f = entry.pose();
      Matrix3f matrix3f = entry.normal();

      for (WildfireModelRenderer.TexturedQuad quad : model.quads) {
         if (quad.uvs[0] != 0.0F || quad.uvs[1] != 0.0F || quad.uvs[2] != 0.0F || quad.uvs[3] != 0.0F) {
            Vector3f vector3f = new Vector3f(quad.normal.x(), quad.normal.y(), quad.normal.z()).mul(matrix3f);
            float normalX = vector3f.x;
            float normalY = vector3f.y;
            float normalZ = vector3f.z;

            for (int index = 0; index < quad.vertexPositions.length; index++) {
               WildfireModelRenderer.PositionTextureVertex vertex = quad.vertexPositions[index];
               if (quad.vertexNormals != null) {
                  vector3f.set(quad.vertexNormals[index]).mul(matrix3f).normalize();
                  normalX = vector3f.x;
                  normalY = vector3f.y;
                  normalZ = vector3f.z;
               }

               float j = vertex.x() / 16.0F;
               float k = vertex.y() / 16.0F;
               float l = vertex.z() / 16.0F;
               if (displacementX != 0.0F || displacementY != 0.0F) {
                  float depth = Math.max(0.01F, -model.posZ1);
                  float weight = deformationWeight(vertex.z(), depth);
                  j += displacementX * weight / 16.0F;
                  k += displacementY * weight / 16.0F;
                  Vector3fc sourceNormal = quad.vertexNormals == null ? quad.normal : quad.vertexNormals[index];
                  float derivative = vertex.z() < 0.0F && vertex.z() >= -depth ? 2.0F * vertex.z() / (depth * depth) : 0.0F;
                  vector3f.set(
                        sourceNormal.x(),
                        sourceNormal.y(),
                        sourceNormal.z() - derivative * (displacementX * sourceNormal.x() + displacementY * sourceNormal.y())
                     )
                     .mul(matrix3f)
                     .normalize();
                  normalX = vector3f.x;
                  normalY = vector3f.y;
                  normalZ = vector3f.z;
               }

               Vector4f vector4f = new Vector4f(j, k, l, 1.0F).mul(matrix4f);
               vertexConsumer.addVertex(vector4f.x(), vector4f.y(), vector4f.z(), color, vertex.u(), vertex.v(), overlay, light, normalX, normalY, normalZ);
            }
         }
      }
   }

   public static float deformationWeight(float z, float depth) {
      float fraction = Math.clamp(-z / Math.max(0.01F, depth), 0.0F, 1.0F);
      return fraction * fraction;
   }

   public static class BreastModelBox extends WildfireModelRenderer.ModelBox {
      public BreastModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta, UVLayout dynamicUvLayouts) {
         super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts);
      }
   }

   public static class ModelBox {
      public final WildfireModelRenderer.TexturedQuad[] quads;
      public final float posX1;
      public final float posY1;
      public final float posZ1;
      public final float posX2;
      public final float posY2;
      public final float posZ2;
      protected final UVLayout dynamicUvLayouts;

      protected ModelBox(float x1, float y1, float z1, float x2, float y2, float z2, WildfireModelRenderer.TexturedQuad[] quads, UVLayout dynamicUvLayouts) {
         this.posX1 = x1;
         this.posY1 = y1;
         this.posZ1 = z1;
         this.posX2 = x2;
         this.posY2 = y2;
         this.posZ2 = z2;
         this.quads = quads;
         this.dynamicUvLayouts = dynamicUvLayouts;
      }

      protected ModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta, int quads, UVLayout dynamicUvLayouts) {
         this.posX1 = x;
         this.posY1 = y;
         this.posZ1 = z;
         this.posX2 = x + dx;
         this.posY2 = y + dy;
         this.posZ2 = z + dz;
         this.quads = new WildfireModelRenderer.TexturedQuad[quads];
         this.dynamicUvLayouts = dynamicUvLayouts;
         float f = x + dx;
         float f1 = y + dy;
         float f2 = z + dz;
         x -= delta;
         y -= delta;
         z -= delta;
         f += delta;
         f1 += delta;
         f2 += delta;
         this.initQuads(
            tW,
            tH,
            dx,
            dy,
            dz,
            quads,
            new WildfireModelRenderer.PositionTextureVertex(f, y, z, 0.0F, 8.0F),
            new WildfireModelRenderer.PositionTextureVertex(f, f1, z, 8.0F, 8.0F),
            new WildfireModelRenderer.PositionTextureVertex(x, f1, z, 8.0F, 0.0F),
            new WildfireModelRenderer.PositionTextureVertex(x, y, f2, 0.0F, 0.0F),
            new WildfireModelRenderer.PositionTextureVertex(f, y, f2, 0.0F, 8.0F),
            new WildfireModelRenderer.PositionTextureVertex(f, f1, f2, 8.0F, 8.0F),
            new WildfireModelRenderer.PositionTextureVertex(x, f1, f2, 8.0F, 0.0F),
            new WildfireModelRenderer.PositionTextureVertex(x, y, z, 0.0F, 0.0F)
         );
      }

      protected void initQuads(
         int tW,
         int tH,
         int dx,
         int dy,
         int dz,
         int quads,
         WildfireModelRenderer.PositionTextureVertex vertex,
         WildfireModelRenderer.PositionTextureVertex vertex1,
         WildfireModelRenderer.PositionTextureVertex vertex2,
         WildfireModelRenderer.PositionTextureVertex vertex3,
         WildfireModelRenderer.PositionTextureVertex vertex4,
         WildfireModelRenderer.PositionTextureVertex vertex5,
         WildfireModelRenderer.PositionTextureVertex vertex6,
         WildfireModelRenderer.PositionTextureVertex vertex7
      ) {
         WildfireModelRenderer.PositionTextureVertex[][] faceVertices = new WildfireModelRenderer.PositionTextureVertex[][]{
            {vertex4, vertex, vertex1, vertex5},
            {vertex7, vertex3, vertex6, vertex2},
            {vertex4, vertex3, vertex7, vertex},
            {vertex1, vertex2, vertex6, vertex5},
            {vertex, vertex7, vertex2, vertex1},
            {vertex3, vertex4, vertex5, vertex6}
         };
         int i = 0;

         for (Entry<UVDirection, UVQuad> entry : this.dynamicUvLayouts.getAllSides().entrySet()) {
            UVDirection direction = entry.getKey();
            UVQuad quad = entry.getValue();
            if (quad != null) {
               this.quads[i] = new WildfireModelRenderer.TexturedQuad(
                  quad.x1(), quad.y1(), quad.x2(), quad.y2(), tW, tH, direction, faceVertices[i][0], faceVertices[i][1], faceVertices[i][2], faceVertices[i][3]
               );
               i++;
            }
         }
      }
   }

   public static class OverlayModelBox extends WildfireModelRenderer.ModelBox {
      public OverlayModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta, UVLayout dynamicUvLayouts) {
         super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts);
      }
   }

   public record PositionTextureVertex(float x, float y, float z, float u, float v) {
      public WildfireModelRenderer.PositionTextureVertex withTexturePosition(float texU, float texV) {
         return new WildfireModelRenderer.PositionTextureVertex(this.x, this.y, this.z, texU, texV);
      }
   }

   public static final class RoundedBreastModelBox extends WildfireModelRenderer.ModelBox {
      public RoundedBreastModelBox(int textureWidth, int textureHeight, float centerX, float size, float inflation, BreastShape shape, UVLayout uvLayout) {
         this(textureWidth, textureHeight, centerX, size, inflation, 0.0F, shape, uvLayout);
      }

      public RoundedBreastModelBox(
         int textureWidth, int textureHeight, float centerX, float size, float inflation, float depthOffset, BreastShape shape, UVLayout uvLayout
      ) {
         this(textureWidth, textureHeight, centerX, size, inflation, depthOffset, shape, uvLayout, false);
      }

      public RoundedBreastModelBox(
         int textureWidth,
         int textureHeight,
         float centerX,
         float size,
         float inflation,
         float depthOffset,
         BreastShape shape,
         UVLayout uvLayout,
         boolean frontOnly
      ) {
         this(textureWidth, textureHeight, centerX, profile(centerX, size, inflation, depthOffset, shape), uvLayout, frontOnly);
      }

      private RoundedBreastModelBox(
         int textureWidth, int textureHeight, float centerX, WildfireModelRenderer.RoundedBreastModelBox.Profile profile, UVLayout uvLayout, boolean frontOnly
      ) {
         super(
            centerX - profile.halfWidth,
            profile.y[0],
            -profile.maxDepth,
            centerX + profile.halfWidth,
            profile.y[profile.y.length - 1],
            1.5F,
            buildQuads(textureWidth, textureHeight, centerX, profile, uvLayout, frontOnly),
            uvLayout
         );
      }

      private static WildfireModelRenderer.RoundedBreastModelBox.Profile profile(
         float centerX, float rawSize, float inflation, float depthOffset, BreastShape shape
      ) {
         float t = Math.clamp(rawSize / 0.8F, 0.0F, 1.0F);
         float smooth = t * t * (3.0F - 2.0F * t);
         boolean legacy = shape == BreastShape.LEGACY;
         boolean blocks = shape == BreastShape.BLOCKS || shape == BreastShape.CONNECTED_BLOCKS;
         boolean leftBreast = centerX < 0.0F;

         float widthFactor = switch (shape) {
            case WIDE -> 1.32F;
            case NARROW -> 0.82F;
            case SOFT, CONNECTED -> 1.04F;
            case ASYMMETRIC -> leftBreast ? 0.97F : 1.03F;
            default -> 1.0F;
         };

         float heightFactor = switch (shape) {
            case ASYMMETRIC -> leftBreast ? 0.98F : 1.02F;
            case SAGGING -> 1.08F;
            case PERKY -> 0.94F;
            case TEARDROP -> 1.12F;
            default -> 1.0F;
         };

         float depthFactor = switch (shape) {
            case SOFT -> 0.94F;
            default -> 1.0F;
            case ASYMMETRIC -> leftBreast ? 0.96F : 1.04F;
            case SAGGING -> 1.05F;
            case TEARDROP -> 1.03F;
         };
         float baseWidth = legacy || shape == BreastShape.CONNECTED_BLOCKS
            ? 4.0F
            : (shape == BreastShape.BLOCKS ? 3.5F : lerp(smooth, 2.75F, 4.0F) * widthFactor);
         float width = baseWidth + inflation * 2.0F;
         float baseHeight = legacy
            ? 5.0F
            : (shape == BreastShape.BLOCKS ? 3.5F : (shape == BreastShape.CONNECTED_BLOCKS ? 4.0F : lerp(smooth, 3.5F, 5.0F) * heightFactor));
         float height = baseHeight + inflation * 2.0F;
         float yStart = (legacy ? 0.0F : lerp(smooth, 0.75F, 0.0F) + (shape == BreastShape.SAGGING ? 0.38F : (shape == BreastShape.PERKY ? -0.18F : 0.0F)))
            - inflation;
         float surfaceDepth = (legacy ? 3.0F : lerp((float)Math.pow(t, 0.82), 0.12F, 3.0F) * depthFactor) + inflation;
         float maxDepth = surfaceDepth + depthOffset;
         float[] xFractions = new float[]{0.0F, 0.23F, 0.5F, 0.77F, 1.0F};
         float[] yFractions = new float[]{0.0F, 0.2F, 0.52F, 0.82F, 1.0F};

         float innerEdge = switch (shape) {
            case SOFT -> 0.48F;
            case CONNECTED -> 0.76F;
            default -> 0.16F;
            case SAGGING -> 0.38F;
         };
         float[] horizontalRoundness = !legacy && !blocks
            ? (leftBreast ? new float[]{0.08F, 0.68F, 1.0F, 0.82F, innerEdge} : new float[]{innerEdge, 0.82F, 1.0F, 0.68F, 0.08F})
            : new float[]{1.0F, 1.0F, 1.0F, 1.0F, 1.0F};
         float[] var35;
         if (!legacy && !blocks) {
            switch (shape) {
               case SOFT:
                  var35 = new float[]{0.15F, 0.62F, 0.96F, 0.82F, 0.18F};
                  break;
               case CONNECTED:
               default:
                  var35 = new float[]{0.04F, 0.48F, 1.0F, 0.76F, 0.04F};
                  break;
               case ASYMMETRIC:
                  var35 = leftBreast ? new float[]{0.09F, 0.65F, 1.0F, 0.65F, 0.08F} : new float[]{0.08F, 0.59F, 1.0F, 0.72F, 0.1F};
                  break;
               case SAGGING:
                  var35 = new float[]{0.08F, 0.34F, 0.76F, 1.0F, 0.36F};
                  break;
               case PERKY:
                  var35 = new float[]{0.18F, 0.84F, 1.0F, 0.52F, 0.08F};
                  break;
               case TEARDROP:
                  var35 = new float[]{0.05F, 0.38F, 0.82F, 1.0F, 0.42F};
            }
         } else {
            var35 = new float[]{1.0F, 1.0F, 1.0F, 1.0F, 1.0F};
         }

         float[] verticalRoundness = var35;
         if (!legacy && !blocks) {
            horizontalRoundness = resample(xFractions, horizontalRoundness);

            float peak = switch (shape) {
               case ASYMMETRIC -> leftBreast ? 0.55F : 0.58F;
               case SAGGING, TEARDROP -> 0.66F;
               case PERKY -> 0.46F;
               default -> 0.56F;
            };
            verticalRoundness = roundedVerticalProfile(peak);
            xFractions = bands(12);
            yFractions = bands(12);
         }

         float[] x = new float[xFractions.length];
         float[] y = new float[yFractions.length];
         float[][] depth = new float[yFractions.length][xFractions.length];

         for (int column = 0; column < xFractions.length; column++) {
            x[column] = -width / 2.0F + xFractions[column] * width;
         }

         for (int row = 0; row < yFractions.length; row++) {
            y[row] = yStart + yFractions[row] * height;

            for (int column = 0; column < xFractions.length; column++) {
               depth[row][column] = Math.max(inflation, surfaceDepth * verticalRoundness[row] * horizontalRoundness[column] + depthOffset);
            }
         }

         return new WildfireModelRenderer.RoundedBreastModelBox.Profile(width / 2.0F, x, y, depth, maxDepth, xFractions, yFractions, !legacy && !blocks);
      }

      private static float[] bands(int segments) {
         float[] result = new float[segments + 1];

         for (int i = 0; i <= segments; i++) {
            result[i] = (float)i / segments;
         }

         return result;
      }

      private static float[] roundedVerticalProfile(float peak) {
         float[] result = new float[13];

         for (int i = 0; i < result.length; i++) {
            float fraction = i / 12.0F;
            float distance = fraction < peak ? (fraction - peak) / peak : (fraction - peak) / (1.0F - peak);
            result[i] = 0.04F + 0.96F * (float)Math.sqrt(Math.max(0.0F, 1.0F - distance * distance));
         }

         return result;
      }

      private static float[] resample(float[] knots, float[] values) {
         float[] slopes = new float[values.length];

         for (int i = 1; i < slopes.length - 1; i++) {
            float a = (values[i] - values[i - 1]) / (knots[i] - knots[i - 1]);
            float b = (values[i + 1] - values[i]) / (knots[i + 1] - knots[i]);
            slopes[i] = a * b <= 0.0F ? 0.0F : 2.0F * a * b / (a + b);
         }

         float[] result = new float[13];

         for (int i = 0; i < result.length; i++) {
            float x = i / 12.0F;
            int k = 0;

            while (k < knots.length - 2 && x > knots[k + 1]) {
               k++;
            }

            float h = knots[k + 1] - knots[k];
            float t = (x - knots[k]) / h;
            result[i] = (2.0F * t * t * t - 3.0F * t * t + 1.0F) * values[k]
               + (t * t * t - 2.0F * t * t + t) * h * slopes[k]
               + (-2.0F * t * t * t + 3.0F * t * t) * values[k + 1]
               + (t * t * t - t * t) * h * slopes[k + 1];
         }

         return result;
      }

      private static WildfireModelRenderer.TexturedQuad[] buildQuads(
         int textureWidth, int textureHeight, float centerX, WildfireModelRenderer.RoundedBreastModelBox.Profile profile, UVLayout uvLayout, boolean frontOnly
      ) {
         List<WildfireModelRenderer.TexturedQuad> quads = new ArrayList<>(32);
         int rows = profile.y.length - 1;
         int columns = profile.x.length - 1;
         UVQuad front = uvLayout.get(UVDirection.NORTH);
         UVQuad east = uvLayout.get(UVDirection.EAST);
         UVQuad west = uvLayout.get(UVDirection.WEST);
         float[] uBands = profile.u;
         float[] vBands = profile.v;
         float back = 1.5F;

         for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
               float x0 = centerX + profile.x[column];
               float x1 = centerX + profile.x[column + 1];
               addPatch(
                  quads,
                  textureWidth,
                  textureHeight,
                  front,
                  UVDirection.NORTH,
                  uBands[column],
                  vBands[row],
                  uBands[column + 1],
                  vBands[row + 1],
                  vertex(x1, profile.y[row], -profile.depth[row][column + 1]),
                  vertex(x0, profile.y[row], -profile.depth[row][column]),
                  vertex(x0, profile.y[row + 1], -profile.depth[row + 1][column]),
                  vertex(x1, profile.y[row + 1], -profile.depth[row + 1][column + 1])
               );
            }

            if (!frontOnly) {
               addPatch(
                  quads,
                  textureWidth,
                  textureHeight,
                  east,
                  UVDirection.EAST,
                  0.0F,
                  vBands[row],
                  1.0F,
                  vBands[row + 1],
                  vertex(centerX + profile.x[columns], profile.y[row], -profile.depth[row][columns]),
                  vertex(centerX + profile.x[columns], profile.y[row], back),
                  vertex(centerX + profile.x[columns], profile.y[row + 1], back),
                  vertex(centerX + profile.x[columns], profile.y[row + 1], -profile.depth[row + 1][columns])
               );
               addPatch(
                  quads,
                  textureWidth,
                  textureHeight,
                  west,
                  UVDirection.WEST,
                  0.0F,
                  vBands[row],
                  1.0F,
                  vBands[row + 1],
                  vertex(centerX + profile.x[0], profile.y[row], back),
                  vertex(centerX + profile.x[0], profile.y[row], -profile.depth[row][0]),
                  vertex(centerX + profile.x[0], profile.y[row + 1], -profile.depth[row + 1][0]),
                  vertex(centerX + profile.x[0], profile.y[row + 1], back)
               );
            }
         }

         if (frontOnly) {
            if (profile.smooth) {
               smoothSurface(quads);
            }

            return quads.toArray(WildfireModelRenderer.TexturedQuad[]::new);
         } else {
            int lastRow = profile.y.length - 1;

            for (int column = 0; column < columns; column++) {
               float x0 = centerX + profile.x[column];
               float x1 = centerX + profile.x[column + 1];
               addPatch(
                  quads,
                  textureWidth,
                  textureHeight,
                  uvLayout.get(UVDirection.DOWN),
                  UVDirection.DOWN,
                  uBands[column],
                  0.0F,
                  uBands[column + 1],
                  1.0F,
                  vertex(x0, profile.y[0], back),
                  vertex(x1, profile.y[0], back),
                  vertex(x1, profile.y[0], -profile.depth[0][column + 1]),
                  vertex(x0, profile.y[0], -profile.depth[0][column])
               );
               addPatch(
                  quads,
                  textureWidth,
                  textureHeight,
                  uvLayout.get(UVDirection.UP),
                  UVDirection.UP,
                  uBands[column],
                  0.0F,
                  uBands[column + 1],
                  1.0F,
                  vertex(x0, profile.y[lastRow], -profile.depth[lastRow][column]),
                  vertex(x1, profile.y[lastRow], -profile.depth[lastRow][column + 1]),
                  vertex(x1, profile.y[lastRow], back),
                  vertex(x0, profile.y[lastRow], back)
               );
            }

            if (profile.smooth) {
               smoothSurface(quads);
            }

            return quads.toArray(WildfireModelRenderer.TexturedQuad[]::new);
         }
      }

      private static WildfireModelRenderer.PositionTextureVertex vertex(float x, float y, float z) {
         return new WildfireModelRenderer.PositionTextureVertex(x, y, z, 0.0F, 0.0F);
      }

      private static void addPatch(
         List<WildfireModelRenderer.TexturedQuad> target,
         int textureWidth,
         int textureHeight,
         UVQuad uv,
         UVDirection direction,
         float fromU,
         float fromV,
         float toU,
         float toV,
         WildfireModelRenderer.PositionTextureVertex... vertices
      ) {
         if (uv != null) {
            target.add(
               new WildfireModelRenderer.TexturedQuad(
                  lerp(fromU, uv.x1(), uv.x2()),
                  lerp(fromV, uv.y1(), uv.y2()),
                  lerp(toU, uv.x1(), uv.x2()),
                  lerp(toV, uv.y1(), uv.y2()),
                  textureWidth,
                  textureHeight,
                  direction,
                  true,
                  vertices
               )
            );
         }
      }

      private static float lerp(float delta, float start, float end) {
         return start + delta * (end - start);
      }

      private static void smoothSurface(List<WildfireModelRenderer.TexturedQuad> quads) {
         record Point(float x, float y, float z) {
         }

         Map<Point, Vector3f> normals = new HashMap<>();

         for (WildfireModelRenderer.TexturedQuad quad : quads) {
            for (WildfireModelRenderer.PositionTextureVertex vertex : quad.vertexPositions) {
               normals.computeIfAbsent(new Point(vertex.x(), vertex.y(), vertex.z()), key -> new Vector3f()).add(quad.normal);
            }
         }

         for (WildfireModelRenderer.TexturedQuad quad : quads) {
            quad.vertexNormals = new Vector3fc[4];

            for (int i = 0; i < 4; i++) {
               WildfireModelRenderer.PositionTextureVertex v = quad.vertexPositions[i];
               quad.vertexNormals[i] = new Vector3f((Vector3fc)normals.get(new Point(v.x(), v.y(), v.z()))).normalize();
            }
         }
      }

      private record Profile(float halfWidth, float[] x, float[] y, float[][] depth, float maxDepth, float[] u, float[] v, boolean smooth) {
      }
   }

   public static class TexturedQuad {
      public final WildfireModelRenderer.PositionTextureVertex[] vertexPositions;
      public final Vector3fc normal;
      public Vector3fc[] vertexNormals;
      public final float[] uvs;

      public TexturedQuad(
         float u1,
         float v1,
         float u2,
         float v2,
         float texWidth,
         float texHeight,
         UVDirection directionIn,
         WildfireModelRenderer.PositionTextureVertex... positionsIn
      ) {
         this(u1, v1, u2, v2, texWidth, texHeight, directionIn, false, positionsIn);
      }

      public TexturedQuad(
         float u1,
         float v1,
         float u2,
         float v2,
         float texWidth,
         float texHeight,
         UVDirection directionIn,
         boolean geometricNormal,
         WildfireModelRenderer.PositionTextureVertex... positionsIn
      ) {
         Preconditions.checkArgument(positionsIn.length == 4, "Incorrect number of vertices; expected 4, got %s", positionsIn.length);
         this.uvs = new float[]{u1, v1, u2, v2};
         this.vertexPositions = positionsIn;
         float f = 0.0F / texWidth;
         float f1 = 0.0F / texHeight;
         positionsIn[0] = positionsIn[0].withTexturePosition(u2 / texWidth - f, v1 / texHeight + f1);
         positionsIn[1] = positionsIn[1].withTexturePosition(u1 / texWidth + f, v1 / texHeight + f1);
         positionsIn[2] = positionsIn[2].withTexturePosition(u1 / texWidth + f, v2 / texHeight - f1);
         positionsIn[3] = positionsIn[3].withTexturePosition(u2 / texWidth - f, v2 / texHeight - f1);
         if (geometricNormal) {
            Vector3f edgeA = new Vector3f(
               positionsIn[1].x() - positionsIn[0].x(), positionsIn[1].y() - positionsIn[0].y(), positionsIn[1].z() - positionsIn[0].z()
            );
            Vector3f edgeB = new Vector3f(
               positionsIn[2].x() - positionsIn[0].x(), positionsIn[2].y() - positionsIn[0].y(), positionsIn[2].z() - positionsIn[0].z()
            );
            Vector3f computed = edgeA.cross(edgeB).normalize();
            this.normal = computed.isFinite() ? computed : directionIn.getUnitVector();
         } else {
            this.normal = directionIn.getUnitVector();
         }
      }
   }
}
