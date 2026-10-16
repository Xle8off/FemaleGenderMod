/*
 * Wildfire's Female Gender Mod is a female gender mod created for Minecraft.
 * Copyright (C) 2023-present WildfireRomeo
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wildfire.render;

import com.google.common.base.Preconditions;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.main.uvs.UVDirection;
import com.wildfire.main.uvs.UVLayout;
import com.wildfire.main.uvs.UVQuad;
import com.wildfire.main.networking.appearance.BreastShape;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/// @apiNote Only use this on the client side
public final class WildfireModelRenderer {
    private WildfireModelRenderer() {
        throw new UnsupportedOperationException();
    }

    public static void renderBox(WildfireModelRenderer.ModelBox model, PoseStack.Pose entry, VertexConsumer vertexConsumer,
                                 int light, int overlay, int color) {
        renderBox(model, entry, vertexConsumer, light, overlay, color, 0f, 0f);
    }

    /** Root stays fixed; displacement grows smoothly towards the outer surface. */
    public static void renderBox(ModelBox model, PoseStack.Pose entry, VertexConsumer vertexConsumer,
                                 int light, int overlay, int color, float displacementX, float displacementY) {
        Matrix4f matrix4f = entry.pose();
        Matrix3f matrix3f = entry.normal();
        for(var quad : model.quads) {

            //Make sure UVs aren't set to zero. If they are, the textures screw up. Don't render the quad at all.
            if(quad.uvs[0] == 0.0F && quad.uvs[1] == 0.0F && quad.uvs[2] == 0.0F && quad.uvs[3] == 0.0F) continue;

            Vector3f vector3f = new Vector3f(quad.normal.x(), quad.normal.y(), quad.normal.z()).mul(matrix3f);
            float normalX = vector3f.x;
            float normalY = vector3f.y;
            float normalZ = vector3f.z;
            for (int index = 0; index < quad.vertexPositions.length; index++) {
                var vertex = quad.vertexPositions[index];
                if (quad.vertexNormals != null) {
                    vector3f.set(quad.vertexNormals[index]).mul(matrix3f).normalize();
                    normalX = vector3f.x; normalY = vector3f.y; normalZ = vector3f.z;
                }
                float j = vertex.x() / 16.0F;
                float k = vertex.y() / 16.0F;
                float l = vertex.z() / 16.0F;
                if (displacementX != 0f || displacementY != 0f) {
                    float depth = Math.max(0.01f, -model.posZ1);
                    float weight = deformationWeight(vertex.z(), depth);
                    j += displacementX * weight / 16f;
                    k += displacementY * weight / 16f;
                    // Inverse-transpose of the displacement Jacobian, before the pose transform.
                    Vector3fc sourceNormal = quad.vertexNormals == null ? quad.normal : quad.vertexNormals[index];
                    float derivative = vertex.z() < 0f && vertex.z() >= -depth
                        ? 2f * vertex.z() / (depth * depth) : 0f;
                    vector3f.set(sourceNormal.x(), sourceNormal.y(), sourceNormal.z()
                        - derivative * (displacementX * sourceNormal.x() + displacementY * sourceNormal.y()))
                        .mul(matrix3f).normalize();
                    normalX = vector3f.x; normalY = vector3f.y; normalZ = vector3f.z;
                }
                Vector4f vector4f = new Vector4f(j, k, l, 1.0F).mul(matrix4f);
                vertexConsumer.addVertex(vector4f.x(), vector4f.y(), vector4f.z(), color, vertex.u(), vertex.v(),
                    overlay, light, normalX, normalY, normalZ);
            }
        }
    }

    public static float deformationWeight(float z, float depth) {
        float fraction = Math.clamp(-z / Math.max(0.01f, depth), 0f, 1f);
        return fraction * fraction;
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

        protected ModelBox(float x1, float y1, float z1, float x2, float y2, float z2,
                           TexturedQuad[] quads, UVLayout dynamicUvLayouts) {
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
            this.quads = new TexturedQuad[quads];
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

            initQuads(tW, tH, dx, dy, dz, quads,
                    new PositionTextureVertex(f, y, z, 0.0F, 8.0F),
                    new PositionTextureVertex(f, f1, z, 8.0F, 8.0F),
                    new PositionTextureVertex(x, f1, z, 8.0F, 0.0F),
                    new PositionTextureVertex(x, y, f2, 0.0F, 0.0F),
                    new PositionTextureVertex(f, y, f2, 0.0F, 8.0F),
                    new PositionTextureVertex(f, f1, f2, 8.0F, 8.0F),
                    new PositionTextureVertex(x, f1, f2, 8.0F, 0.0F),
                    new PositionTextureVertex(x, y, z, 0.0F, 0.0F)
            );
        }

        protected void initQuads(int tW, int tH, int dx, int dy, int dz, int quads,
                                 PositionTextureVertex vertex, PositionTextureVertex vertex1, PositionTextureVertex vertex2,
                                 PositionTextureVertex vertex3, PositionTextureVertex vertex4, PositionTextureVertex vertex5,
                                 PositionTextureVertex vertex6, PositionTextureVertex vertex7) {
            PositionTextureVertex[][] faceVertices = {
                    {vertex4, vertex, vertex1, vertex5},	// EAST
                    {vertex7, vertex3, vertex6, vertex2},	// WEST
                    {vertex4, vertex3, vertex7, vertex},	// DOWN
                    {vertex1, vertex2, vertex6, vertex5},	// UP
                    {vertex, vertex7, vertex2, vertex1},	// NORTH
                    {vertex3, vertex4, vertex5, vertex6}	// SOUTH
            };

            int i = 0;
            for(var entry : dynamicUvLayouts.getAllSides().entrySet()) {
                UVDirection direction = entry.getKey();
                UVQuad quad = entry.getValue();
                if(quad == null) continue;

                this.quads[i] = new TexturedQuad(
                        quad.x1(), quad.y1(), quad.x2(), quad.y2(),
                        tW, tH,
                        direction,
                        faceVertices[i][0],
                        faceVertices[i][1],
                        faceVertices[i][2],
                        faceVertices[i][3]
                );
                i++;
            }
        }
    }

    public static class OverlayModelBox extends ModelBox {
        public OverlayModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta, UVLayout dynamicUvLayouts) {
            super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts);
        }
    }

    public static class BreastModelBox extends ModelBox {
        public BreastModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta, UVLayout dynamicUvLayouts) {
            super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts);
        }
    }

    /** A continuous Minecraft-style dome, rounded both vertically and horizontally. */
    public static final class RoundedBreastModelBox extends ModelBox {
        public RoundedBreastModelBox(int textureWidth, int textureHeight, float centerX, float size,
                                     float inflation, BreastShape shape, UVLayout uvLayout) {
            this(textureWidth, textureHeight, centerX, size, inflation, 0f, shape, uvLayout);
        }

        public RoundedBreastModelBox(int textureWidth, int textureHeight, float centerX, float size,
                                     float inflation, float depthOffset, BreastShape shape, UVLayout uvLayout) {
            this(textureWidth, textureHeight, centerX, size, inflation, depthOffset, shape, uvLayout, false);
        }

        public RoundedBreastModelBox(int textureWidth, int textureHeight, float centerX, float size, float inflation,
                                     float depthOffset, BreastShape shape, UVLayout uvLayout, boolean frontOnly) {
            this(textureWidth, textureHeight, centerX, profile(centerX, size, inflation, depthOffset, shape), uvLayout, frontOnly);
        }

        private RoundedBreastModelBox(int textureWidth, int textureHeight, float centerX, Profile profile, UVLayout uvLayout, boolean frontOnly) {
            super(
                centerX - profile.halfWidth, profile.y[0], -profile.maxDepth,
                centerX + profile.halfWidth, profile.y[profile.y.length - 1], 1.5f,
                buildQuads(textureWidth, textureHeight, centerX, profile, uvLayout, frontOnly), uvLayout
            );
        }

        private static Profile profile(float centerX, float rawSize, float inflation, float depthOffset, BreastShape shape) {
            float t = Math.clamp(rawSize / 0.8f, 0f, 1f);
            float smooth = t * t * (3f - 2f * t);
            boolean legacy = shape == BreastShape.LEGACY;
            boolean blocks = shape == BreastShape.BLOCKS || shape == BreastShape.CONNECTED_BLOCKS;
            boolean leftBreast = centerX < 0f;
            float widthFactor = switch (shape) {
                case WIDE -> 1.32f;
                case NARROW -> 0.82f;
                case SOFT, CONNECTED -> 1.04f;
                case ASYMMETRIC -> leftBreast ? 0.97f : 1.03f;
                default -> 1f;
            };
            float heightFactor = switch (shape) {
                case SAGGING -> 1.08f;
                case PERKY -> 0.94f;
                case TEARDROP -> 1.12f;
                case ASYMMETRIC -> leftBreast ? 0.98f : 1.02f;
                default -> 1f;
            };
            float depthFactor = switch (shape) {
                case SAGGING -> 1.05f;
                case SOFT -> 0.94f;
                case TEARDROP -> 1.03f;
                case ASYMMETRIC -> leftBreast ? 0.96f : 1.04f;
                default -> 1f;
            };
            float baseWidth = legacy || shape == BreastShape.CONNECTED_BLOCKS ? 4f
                : shape == BreastShape.BLOCKS ? 3.5f
                : lerp(smooth, 2.75f, 4f) * widthFactor;
            float width = baseWidth + inflation * 2f;
            float baseHeight = legacy ? 5f
                : shape == BreastShape.BLOCKS ? 3.5f
                : shape == BreastShape.CONNECTED_BLOCKS ? 4f
                : lerp(smooth, 3.5f, 5f) * heightFactor;
            float height = baseHeight + inflation * 2f;
            float yStart = (legacy ? 0f : lerp(smooth, 0.75f, 0f)
                + (shape == BreastShape.SAGGING ? 0.38f : shape == BreastShape.PERKY ? -0.18f : 0f)) - inflation;
            float surfaceDepth = (legacy ? 3f : lerp((float) Math.pow(t, 0.82), 0.12f, 3f) * depthFactor) + inflation;
            float maxDepth = surfaceDepth + depthOffset;
            float[] xFractions = {0f, 0.23f, 0.5f, 0.77f, 1f};
            float[] yFractions = {0f, 0.2f, 0.52f, 0.82f, 1f};
            float innerEdge = switch (shape) {
                case CONNECTED -> 0.76f;
                case SOFT -> 0.48f;
                case SAGGING -> 0.38f;
                default -> 0.16f;
            };
            float[] horizontalRoundness = legacy || blocks
                ? new float[]{1f, 1f, 1f, 1f, 1f}
                : leftBreast
                    ? new float[]{0.08f, 0.68f, 1f, 0.82f, innerEdge}
                    : new float[]{innerEdge, 0.82f, 1f, 0.68f, 0.08f};
            float[] verticalRoundness = legacy || blocks
                ? new float[]{1f, 1f, 1f, 1f, 1f}
                : switch (shape) {
                    case PERKY -> new float[]{0.18f, 0.84f, 1f, 0.52f, 0.08f};
                    case SOFT -> new float[]{0.15f, 0.62f, 0.96f, 0.82f, 0.18f};
                    case SAGGING -> new float[]{0.08f, 0.34f, 0.76f, 1f, 0.36f};
                    case TEARDROP -> new float[]{0.05f, 0.38f, 0.82f, 1f, 0.42f};
                    case ASYMMETRIC -> leftBreast
                        ? new float[]{0.09f, 0.65f, 1f, 0.65f, 0.08f}
                        : new float[]{0.08f, 0.59f, 1f, 0.72f, 0.10f};
                    default -> new float[]{0.04f, 0.48f, 1f, 0.76f, 0.04f};
                };
            if (!legacy && !blocks) {
                horizontalRoundness = resample(xFractions, horizontalRoundness);
                // An elliptical silhouette has a broad, tangent-continuous apex rather than
                // the narrow central bump produced by interpolating five depth samples.
                float peak = switch (shape) {
                    case PERKY -> 0.46f;
                    case SAGGING, TEARDROP -> 0.66f;
                    case ASYMMETRIC -> leftBreast ? 0.55f : 0.58f;
                    default -> 0.56f;
                };
                verticalRoundness = roundedVerticalProfile(peak);
                xFractions = bands(12); yFractions = bands(12);
            }
            float[] x = new float[xFractions.length];
            float[] y = new float[yFractions.length];
            float[][] depth = new float[yFractions.length][xFractions.length];
            for (int column = 0; column < xFractions.length; column++) {
                x[column] = -width / 2f + xFractions[column] * width;
            }
            for (int row = 0; row < yFractions.length; row++) {
                y[row] = yStart + yFractions[row] * height;
                for (int column = 0; column < xFractions.length; column++) {
                    depth[row][column] = Math.max(inflation,
                        surfaceDepth * verticalRoundness[row] * horizontalRoundness[column] + depthOffset);
                }
            }
            return new Profile(width / 2f, x, y, depth, maxDepth, xFractions, yFractions, !legacy && !blocks);
        }

        private static float[] bands(int segments) {
            float[] result = new float[segments + 1];
            for (int i = 0; i <= segments; i++) result[i] = (float)i / segments;
            return result;
        }

        private static float[] roundedVerticalProfile(float peak) {
            float[] result = new float[13];
            for (int i = 0; i < result.length; i++) {
                float fraction = i / 12f;
                float distance = fraction < peak ? (fraction - peak) / peak
                    : (fraction - peak) / (1f - peak);
                result[i] = 0.04f + 0.96f * (float)Math.sqrt(Math.max(0f, 1f - distance * distance));
            }
            return result;
        }

        /** Shape-preserving cubic interpolation: zero slope at peaks, no overshoot. */
        private static float[] resample(float[] knots, float[] values) {
            float[] slopes = new float[values.length];
            for (int i = 1; i < slopes.length - 1; i++) {
                float a = (values[i] - values[i-1]) / (knots[i] - knots[i-1]);
                float b = (values[i+1] - values[i]) / (knots[i+1] - knots[i]);
                slopes[i] = a * b <= 0 ? 0 : 2 * a * b / (a + b);
            }
            float[] result = new float[13];
            for (int i = 0; i < result.length; i++) {
                float x = i / 12f; int k = 0;
                while (k < knots.length - 2 && x > knots[k+1]) k++;
                float h = knots[k+1] - knots[k], t = (x - knots[k]) / h;
                result[i] = (2*t*t*t-3*t*t+1)*values[k] + (t*t*t-2*t*t+t)*h*slopes[k]
                    + (-2*t*t*t+3*t*t)*values[k+1] + (t*t*t-t*t)*h*slopes[k+1];
            }
            return result;
        }

        private static TexturedQuad[] buildQuads(int textureWidth, int textureHeight, float centerX, Profile profile, UVLayout uvLayout, boolean frontOnly) {
            List<TexturedQuad> quads = new ArrayList<>(32);
            int rows = profile.y.length - 1;
            int columns = profile.x.length - 1;

            UVQuad front = uvLayout.get(UVDirection.NORTH);
            UVQuad east = uvLayout.get(UVDirection.EAST);
            UVQuad west = uvLayout.get(UVDirection.WEST);
            float[] uBands = profile.u;
            float[] vBands = profile.v;
            // Hidden root overlap closes the lower seam during local profile/bounce rotation.
            float back = 1.5f;

            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    float x0 = centerX + profile.x[column];
                    float x1 = centerX + profile.x[column + 1];
                    addPatch(quads, textureWidth, textureHeight, front, UVDirection.NORTH,
                        uBands[column], vBands[row], uBands[column + 1], vBands[row + 1],
                        vertex(x1, profile.y[row], -profile.depth[row][column + 1]),
                        vertex(x0, profile.y[row], -profile.depth[row][column]),
                        vertex(x0, profile.y[row + 1], -profile.depth[row + 1][column]),
                        vertex(x1, profile.y[row + 1], -profile.depth[row + 1][column + 1]));
                }

                if (frontOnly) continue;
                addPatch(quads, textureWidth, textureHeight, east, UVDirection.EAST,
                    0f, vBands[row], 1f, vBands[row + 1],
                    vertex(centerX + profile.x[columns], profile.y[row], -profile.depth[row][columns]),
                    vertex(centerX + profile.x[columns], profile.y[row], back),
                    vertex(centerX + profile.x[columns], profile.y[row + 1], back),
                    vertex(centerX + profile.x[columns], profile.y[row + 1], -profile.depth[row + 1][columns]));
                addPatch(quads, textureWidth, textureHeight, west, UVDirection.WEST,
                    0f, vBands[row], 1f, vBands[row + 1],
                    vertex(centerX + profile.x[0], profile.y[row], back),
                    vertex(centerX + profile.x[0], profile.y[row], -profile.depth[row][0]),
                    vertex(centerX + profile.x[0], profile.y[row + 1], -profile.depth[row + 1][0]),
                    vertex(centerX + profile.x[0], profile.y[row + 1], back));
            }

            if (frontOnly) {
                if (profile.smooth) smoothSurface(quads);
                return quads.toArray(TexturedQuad[]::new);
            }
            int lastRow = profile.y.length - 1;
            for (int column = 0; column < columns; column++) {
                float x0 = centerX + profile.x[column];
                float x1 = centerX + profile.x[column + 1];
                addPatch(quads, textureWidth, textureHeight, uvLayout.get(UVDirection.DOWN), UVDirection.DOWN,
                    uBands[column], 0f, uBands[column + 1], 1f,
                    vertex(x0, profile.y[0], back), vertex(x1, profile.y[0], back),
                    vertex(x1, profile.y[0], -profile.depth[0][column + 1]), vertex(x0, profile.y[0], -profile.depth[0][column]));
                addPatch(quads, textureWidth, textureHeight, uvLayout.get(UVDirection.UP), UVDirection.UP,
                    uBands[column], 0f, uBands[column + 1], 1f,
                    vertex(x0, profile.y[lastRow], -profile.depth[lastRow][column]),
                    vertex(x1, profile.y[lastRow], -profile.depth[lastRow][column + 1]),
                    vertex(x1, profile.y[lastRow], back), vertex(x0, profile.y[lastRow], back));
            }
            if (profile.smooth) smoothSurface(quads);
            return quads.toArray(TexturedQuad[]::new);
        }

        private static PositionTextureVertex vertex(float x, float y, float z) {
            return new PositionTextureVertex(x, y, z, 0f, 0f);
        }

        private static void addPatch(List<TexturedQuad> target, int textureWidth, int textureHeight, UVQuad uv,
                                     UVDirection direction, float fromU, float fromV, float toU, float toV,
                                     PositionTextureVertex... vertices) {
            if (uv == null) return;
            target.add(new TexturedQuad(
                lerp(fromU, uv.x1(), uv.x2()), lerp(fromV, uv.y1(), uv.y2()),
                lerp(toU, uv.x1(), uv.x2()), lerp(toV, uv.y1(), uv.y2()),
                textureWidth, textureHeight, direction, true, vertices));
        }

        private static float lerp(float delta, float start, float end) {
            return start + delta * (end - start);
        }

        private static void smoothSurface(List<TexturedQuad> quads) {
            record Point(float x, float y, float z) {}
            java.util.Map<Point, Vector3f> normals = new java.util.HashMap<>();
            for (var quad : quads) {
                for (var vertex : quad.vertexPositions)
                    normals.computeIfAbsent(new Point(vertex.x(), vertex.y(), vertex.z()), key -> new Vector3f()).add(quad.normal);
            }
            for (var quad : quads) {
                quad.vertexNormals = new Vector3fc[4];
                for (int i = 0; i < 4; i++) {
                    var v = quad.vertexPositions[i];
                    quad.vertexNormals[i] = new Vector3f(normals.get(new Point(v.x(), v.y(), v.z()))).normalize();
                }
            }
        }

        private record Profile(float halfWidth, float[] x, float[] y, float[][] depth, float maxDepth,
                               float[] u, float[] v, boolean smooth) {}
    }

    public record PositionTextureVertex(float x, float y, float z, float u, float v) {
        public PositionTextureVertex withTexturePosition(float texU, float texV) {
            return new PositionTextureVertex(x, y, z, texU, texV);
        }
    }

    public static class TexturedQuad {
        public final WildfireModelRenderer.PositionTextureVertex[] vertexPositions;
        public final Vector3fc normal;
        public Vector3fc[] vertexNormals;
        public final float[] uvs;

        public TexturedQuad(float u1, float v1, float u2, float v2, float texWidth, float texHeight, UVDirection directionIn, PositionTextureVertex... positionsIn) {
            this(u1, v1, u2, v2, texWidth, texHeight, directionIn, false, positionsIn);
        }

        public TexturedQuad(float u1, float v1, float u2, float v2, float texWidth, float texHeight,
                            UVDirection directionIn, boolean geometricNormal, PositionTextureVertex... positionsIn) {
            Preconditions.checkArgument(positionsIn.length == 4, "Incorrect number of vertices; expected 4, got %s", positionsIn.length);

            //Set UVs in array to reference in render side.
            this.uvs = new float[]{ u1, v1, u2, v2 };

            this.vertexPositions = positionsIn;
            float f = 0.0F / texWidth;
            float f1 = 0.0F / texHeight;
            positionsIn[0] = positionsIn[0].withTexturePosition(u2 / texWidth - f, v1 / texHeight + f1);
            positionsIn[1] = positionsIn[1].withTexturePosition(u1 / texWidth + f, v1 / texHeight + f1);
            positionsIn[2] = positionsIn[2].withTexturePosition(u1 / texWidth + f, v2 / texHeight - f1);
            positionsIn[3] = positionsIn[3].withTexturePosition(u2 / texWidth - f, v2 / texHeight - f1);
            if (geometricNormal) {
                Vector3f edgeA = new Vector3f(
                    positionsIn[1].x() - positionsIn[0].x(),
                    positionsIn[1].y() - positionsIn[0].y(),
                    positionsIn[1].z() - positionsIn[0].z());
                Vector3f edgeB = new Vector3f(
                    positionsIn[2].x() - positionsIn[0].x(),
                    positionsIn[2].y() - positionsIn[0].y(),
                    positionsIn[2].z() - positionsIn[0].z());
                Vector3f computed = edgeA.cross(edgeB).normalize();
                this.normal = computed.isFinite() ? computed : directionIn.getUnitVector();
            } else {
                this.normal = directionIn.getUnitVector();
            }
        }
    }
}
