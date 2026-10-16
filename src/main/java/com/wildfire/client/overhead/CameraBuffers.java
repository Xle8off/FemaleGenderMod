package com.wildfire.client.overhead;

import dev.onvoid.webrtc.media.video.VideoFrameBuffer;

/** Returns one owned reference, including when no resizing is necessary. */
public final class CameraBuffers {
    private CameraBuffers() {}
    public static VideoFrameBuffer resize(VideoFrameBuffer input, int width, int height) {
        if (input.getWidth() == width && input.getHeight() == height) {
            // NativeI420Buffer.cropAndScale's no-scale wrapper has no native handle.
            // Borrow the original storage with an explicit reference instead.
            input.retain();
            return input;
        }
        return input.cropAndScale(0, 0, input.getWidth(), input.getHeight(), width, height);
    }
}
