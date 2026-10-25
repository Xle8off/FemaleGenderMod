package net.springplus.screens;

/** Exact block aspect, bounded browser raster; not the source video's encoding quality. */
public record ScreenViewport(int width,int height) {
 public static final int MAX_HEIGHT=920,MAX_WIDTH=2560,MAX_PIXELS=1_800_000;
 public static ScreenViewport of(int blocksWide,int blocksHigh){
  if(blocksWide<1||blocksWide>48||blocksHigh<1||blocksHigh>24)throw new IllegalArgumentException("Screen dimensions");
  int scale=Math.max(1,Math.min(Math.min(MAX_HEIGHT/blocksHigh,MAX_WIDTH/blocksWide),(int)Math.sqrt((double)MAX_PIXELS/(blocksWide*blocksHigh))));
  return new ScreenViewport(blocksWide*scale,blocksHigh*scale);
 }
}
