package com.wildfire.gui;

/** Shared picker frame: different cell sizes, identical outer bounds at every GUI scale. */
public final class ReactionPickerLayout {
    private ReactionPickerLayout(){}
    public static int emojiRows(int height){return Math.clamp((height-254)/24,1,8);}
    public static int stickerRows(int height){return Math.max(1,(emojiRows(height)*24+21)/36);}
    public static int top(int height){return height-132-49-emojiRows(height)*24;}
}
