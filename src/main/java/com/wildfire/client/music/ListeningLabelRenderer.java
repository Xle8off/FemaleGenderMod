package com.wildfire.client.music;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.Identifier;
import java.util.UUID;
import java.util.WeakHashMap;

/** Independent world-space music card. Never calls name-tag APIs or enables player names. */
public final class ListeningLabelRenderer {
    private record Prepared(int width, FormattedCharSequence title, FormattedCharSequence artist, java.util.List<ListeningCardMesh.Rect> mesh) {}
    private static final WeakHashMap<ListeningState.Card, Prepared> cache = new WeakHashMap<>();
    private ListeningLabelRenderer() {}
    static int cardWidth(int titleWidth, int artistWidth) { return Math.clamp(Math.max(titleWidth, artistWidth) + 42, 128, 176); }
    public static void submit(UUID owner, AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.isInvisible || state.isDiscrete || state.distanceToCameraSq > 1024 || camera.orientation == null) return;
        var client = Minecraft.getInstance();
        if (client.level == null || camera != client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState) return;
        if (client.player != null && owner.equals(client.player.getUUID()) && client.options.getCameraType().isFirstPerson()) return;
        var card = ListeningState.card(owner, client.player == null ? null : client.player.getUUID());
        if (card == null || client.font == null) return;
        Prepared prepared = cache.get(card);
        if (prepared == null) {
            Font font = client.font;
            int w = cardWidth(font.width(card.title()), font.width(card.artist()));
            prepared = new Prepared(w, trimmed(font, card.title(), w - 42), trimmed(font, card.artist(), w - 42), ListeningCardMesh.build(w, card.accent()));
            cache.put(card, prepared);
        }
        float left = -prepared.width / 2f;
        final int width = prepared.width, accent = card.accent();
        final var mesh = prepared.mesh;
        pose.pushPose();
        try {
            pose.translate(0, state.boundingBoxHeight + (state.nameTag == null ? .25 : .80), 0);
            pose.mulPose(camera.orientation);
            pose.scale(.016f, -.016f, .016f);
            collector.submitCustomGeometry(pose, RenderTypes.textBackground(), (matrix, vertices) -> {
                for (var rect : mesh) quad(matrix, vertices, left + rect.left(), rect.top() - ListeningCardMesh.HEIGHT,
                    left + rect.right(), rect.bottom() - ListeningCardMesh.HEIGHT, rect.color());
            });
            Identifier icon = accent == 0xFF1DB954 ? Identifier.fromNamespaceAndPath("appearance_sync", "textures/gui/music/spotify.png")
                : accent == 0xFFFFCC33 ? Identifier.fromNamespaceAndPath("appearance_sync", "textures/gui/music/yandex.png")
                : accent == 0xFFFF3434 ? Identifier.fromNamespaceAndPath("appearance_sync", "textures/gui/music/youtube.png") : null;
            if (icon != null) {
                // Preserve source aspect ratio; leave real depth between panel, logo and text.
                float iconWidth = icon.getPath().endsWith("spotify.png") ? 16f * 512 / 529 : 16f;
                float x = left + 18 - iconWidth / 2;
                collector.submitCustomGeometry(pose, RenderTypes.text(icon), (matrix, vertices) -> {
                    vertices.addVertex(matrix, x, -24, .10f).setColor(0xFFFFFFFF).setUv(0, 0).setLight(0xF000F0);
                    vertices.addVertex(matrix, x, -8, .10f).setColor(0xFFFFFFFF).setUv(0, 1).setLight(0xF000F0);
                    vertices.addVertex(matrix, x + iconWidth, -8, .10f).setColor(0xFFFFFFFF).setUv(1, 1).setLight(0xF000F0);
                    vertices.addVertex(matrix, x + iconWidth, -24, .10f).setColor(0xFFFFFFFF).setUv(1, 0).setLight(0xF000F0);
                });
            }
            pose.translate(0, 0, .20);
            boolean hasArtist = !card.artist().getString().isBlank();
            text(collector, pose, prepared.title, left + 32, hasArtist ? -25 : -20, 0xFFF0EDF7);
            if (hasArtist) text(collector, pose, prepared.artist, left + 32, -12, 0xFFACB6C5);
        } finally { pose.popPose(); }
    }
    private static void text(SubmitNodeCollector collector, PoseStack pose, FormattedCharSequence text, float x, float y, int color) {
        collector.submitText(pose, x, y, text, false, Font.DisplayMode.NORMAL, 0xF000F0, color, 0, 0);
    }
    private static FormattedCharSequence trimmed(Font font, Component value, int width) {
        String text = value.getString();
        if (font.width(text) > width) text = font.plainSubstrByWidth(text, width - font.width("…")) + "…";
        return Component.literal(text).getVisualOrderText();
    }
    private static void quad(PoseStack.Pose pose, VertexConsumer vertices, float x1, float y1, float x2, float y2, int color) {
        vertices.addVertex(pose, x1, y1, 0).setColor(color).setLight(0xF000F0);
        vertices.addVertex(pose, x1, y2, 0).setColor(color).setLight(0xF000F0);
        vertices.addVertex(pose, x2, y2, 0).setColor(color).setLight(0xF000F0);
        vertices.addVertex(pose, x2, y1, 0).setColor(color).setLight(0xF000F0);
    }
}
