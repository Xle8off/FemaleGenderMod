package com.wildfire.api;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.ByIdMap.OutOfBoundsStrategy;

public enum Gender implements StringRepresentable {
   FEMALE("female", TextColor.LIGHT_PURPLE, true),
   MALE("male", TextColor.BLUE, false),
   OTHER("other", TextColor.GREEN, true);

   public static final IntFunction<Gender> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), OutOfBoundsStrategy.WRAP);
   public static final Codec<Gender> CODEC = StringRepresentable.fromEnum(Gender::values);
   public static final Codec<Gender> BY_ID_CODEC = ExtraCodecs.idResolverCodec(Enum::ordinal, BY_ID, 0);
   public static final Codec<Gender> CODEC_OR_LEGACY = CODEC.withAlternative(BY_ID_CODEC);
   public static final StreamCodec<ByteBuf, Gender> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);
   private final String saveName;
   private final Component name;
   private final boolean canHaveBreasts;

   Gender(String saveName, TextColor nameColor, boolean canHaveBreasts) {
      this.saveName = saveName;
      this.name = Component.translatable(this.getTranslationKey()).withColor(nameColor);
      this.canHaveBreasts = canHaveBreasts;
   }

   public String getSerializedName() {
      return this.saveName;
   }

   public Component getDisplayName() {
      return this.name;
   }

   public boolean canHaveBreasts() {
      return this.canHaveBreasts;
   }

   public Gender next() {
      return switch (this) {
         case FEMALE -> OTHER;
         case MALE -> FEMALE;
         case OTHER -> MALE;
      };
   }

   public String getTranslationKey() {
      return "female_gender_mod.label." + this.saveName;
   }
}
