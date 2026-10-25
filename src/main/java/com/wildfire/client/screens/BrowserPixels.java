package com.wildfire.client.screens;

/** Byte-order conversion only; never encodes or decodes an image. */
public final class BrowserPixels {
 private BrowserPixels() {}
 public static void bgraToRgba(byte[] pixels) {
  if((pixels.length&3)!=0)throw new IllegalArgumentException("Incomplete pixel");
  for(int i=0;i<pixels.length;i+=4){byte blue=pixels[i];pixels[i]=pixels[i+2];pixels[i+2]=blue;}
 }
}
