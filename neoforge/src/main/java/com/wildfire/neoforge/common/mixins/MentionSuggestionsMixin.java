package com.wildfire.neoforge.common.mixins;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChatScreen.class, priority = 1100)
abstract class MentionSuggestionsMixin {
    @Shadow protected EditBox input;
    @Unique private int springplus$selected;
    @Unique private String springplus$query = "";
    @Unique private String springplus$dismissed = "";
    @Unique private int springplus$start;
    @Unique private long springplus$refreshed;
    @Unique private String springplus$cachedInput = "";
    @Unique private List<PlayerInfo> springplus$matches = List.of();

    @Unique private void springplus$update() {
        com.wildfire.gui.PublicChatControls.syncMentionCursor(input);
        var client = Minecraft.getInstance();
        if (input != null && client.getConnection() != null) {
            String state = input.getValue() + "\u0000" + input.getCursorPosition() + "\u0000" + input.getHighlighted();
            long now = System.currentTimeMillis();
            if (state.equals(springplus$cachedInput) && now - springplus$refreshed < 250) return;
            springplus$cachedInput = state;
            springplus$refreshed = now;
        }
        springplus$matches = List.of();
        if (input == null || client.getConnection() == null || !input.getHighlighted().isEmpty()) return;
        String value = input.getValue();
        int cursor = input.getCursorPosition();
        if (value.startsWith("/")) return;
        int start = cursor;
        while (start > 0 && (Character.isLetterOrDigit(value.charAt(start - 1)) || value.charAt(start - 1) == '_')) start--;
        if (start == 0 || value.charAt(start - 1) != '@') return;
        int at = start - 1;
        if (at > 0 && !Character.isWhitespace(value.charAt(at - 1))) return;
        String query = value.substring(start, cursor).toLowerCase(Locale.ROOT);
        String signature = value.substring(0, cursor);
        if (!signature.equals(springplus$query)) {
            springplus$query = signature;
            springplus$selected = 0;
            springplus$dismissed = "";
        }
        if (signature.equals(springplus$dismissed)) return;
        springplus$start = at;
        springplus$matches = client.getConnection().getListedOnlinePlayers().stream()
            .filter(player -> player.getProfile().name().toLowerCase(Locale.ROOT).startsWith(query))
            .sorted(java.util.Comparator.comparing(player -> player.getProfile().name(), String.CASE_INSENSITIVE_ORDER))
            .limit(6).toList();
        springplus$selected = Math.min(springplus$selected, Math.max(0, springplus$matches.size() - 1));
    }

    @Unique private void springplus$complete(int index) {
        int cursor = input.getCursorPosition();
        String value = input.getValue();
        int end = cursor;
        while (end < value.length() && (Character.isLetterOrDigit(value.charAt(end)) || value.charAt(end) == '_')) end++;
        String mention = "@" + springplus$matches.get(index).getProfile().name();
        String suffix = value.substring(end);
        String separator = suffix.startsWith(" ") ? "" : " ";
        input.setValue(value.substring(0, springplus$start) + mention + separator + suffix);
        input.setCursorPosition(springplus$start + mention.length() + separator.length());
        input.setHighlightPos(input.getCursorPosition());
        springplus$matches = List.of();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void springplus$key(KeyEvent event, CallbackInfoReturnable<Boolean> ci) {
        springplus$update();
        if (springplus$matches.isEmpty()) return;
        int key = event.key();
        if (key == 264 || key == 265) {
            springplus$selected = Math.floorMod(springplus$selected + (key == 264 ? 1 : -1), springplus$matches.size());
            ci.setReturnValue(true);
        } else if (key == 258) {
            springplus$complete(springplus$selected);
            ci.setReturnValue(true);
        } else if (key == 256) {
            springplus$dismissed = springplus$query;
            springplus$matches = List.of();
            ci.setReturnValue(true);
        }
    }

    @Unique private int springplus$width() {
        var font = Minecraft.getInstance().font;
        return springplus$matches.stream().mapToInt(player -> font.width(player.getProfile().name()) + 30).max().orElse(80);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void springplus$click(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> ci) {
        springplus$update();
        int top = input.getY() - 4 - springplus$matches.size() * 22;
        if (click.button() == 0 && click.x() >= input.getX() && click.x() < input.getX() + springplus$width()
            && click.y() >= top && click.y() < input.getY() - 4) {
            springplus$complete((int) ((click.y() - top) / 22));
            ci.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void springplus$render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        springplus$update();
        if (springplus$matches.isEmpty()) return;
        int left = input.getX(), width = springplus$width();
        int top = input.getY() - 4 - springplus$matches.size() * 22;
        for (int i = 0; i < springplus$matches.size(); i++) {
            int y = top + i * 22;
            boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= y && mouseY < y + 22;
            graphics.fill(left, y, left + width, y + 22, i == springplus$selected || hovered ? 0xF0423554 : 0xF01D222B);
            var player = springplus$matches.get(i);
            PlayerFaceExtractor.extractRenderState(graphics, player.getSkin(), left + 3, y + 3, 16);
            graphics.text(Minecraft.getInstance().font, player.getProfile().name(), left + 24, y + 7, 0xFFF0EDF7, false);
        }
    }
}
