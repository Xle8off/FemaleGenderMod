package com.wildfire.api.impl;

import com.wildfire.api.IBreastArmorTexture;
import com.wildfire.api.uvs.UVMap;
import org.joml.Vector2ic;

public record BreastArmorTexture(Vector2ic textureSize, UVMap uvs) implements IBreastArmorTexture {
}
