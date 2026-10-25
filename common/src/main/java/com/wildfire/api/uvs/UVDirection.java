package com.wildfire.api.uvs;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ARGB;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.ByIdMap.OutOfBoundsStrategy;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public enum UVDirection implements StringRepresentable {
   EAST("east", -65536, new Vec3i(1, 0, 0)),
   WEST("west", -16711936, new Vec3i(-1, 0, 0)),
   DOWN("down", -16776961, new Vec3i(0, -1, 0)),
   UP("up", -16711681, new Vec3i(0, 1, 0)),
   NORTH("north", -65281, new Vec3i(0, 0, -1));

   private final Component shortName;
   private final String saveName;
   private final int baseColor;
   private final Vector3fc floatVector;
   public static final IntFunction<UVDirection> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), OutOfBoundsStrategy.WRAP);
   public static final Codec<UVDirection> CODEC = StringRepresentable.fromEnum(UVDirection::values);
   public static final StreamCodec<ByteBuf, UVDirection> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

   UVDirection(String saveName, int baseColor, Vec3i vector) {
      this.saveName = saveName;
      this.baseColor = baseColor;
      this.shortName = Component.translatable(this.getShortTranslationKey());
      this.floatVector = new Vector3f(vector.getX(), vector.getY(), vector.getZ());
   }

   public int getFaceColor(boolean faded) {
      return !faded ? this.baseColor : ARGB.color(51, this.baseColor);
   }

   public Vector3f getUnitVector() {
      return new Vector3f(this.floatVector);
   }

   public FaceDirection getDirection(BreastTypes type) {
      return switch (this) {
         case EAST, WEST -> type.isLeft() ? FaceDirection.INNER : FaceDirection.OUTER;
         case DOWN -> FaceDirection.BOTTOM;
         case UP -> FaceDirection.TOP;
         case NORTH -> FaceDirection.FRONT;
      };
   }

   public MutableComponent getDirectionText(BreastTypes type) {
      return Component.translatable(this.getDirection(type).getTranslationKey());
   }

   public String getShortTranslationKey() {
      return "female_gender_mod.uv.direction." + this.saveName + ".short";
   }

   public Component getShortName() {
      return this.shortName;
   }

   public String getSerializedName() {
      return this.saveName;
   }
}
