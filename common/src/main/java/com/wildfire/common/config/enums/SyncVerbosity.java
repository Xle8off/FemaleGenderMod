package com.wildfire.common.config.enums;

import com.mojang.serialization.Codec;
import com.wildfire.common.WildfireLang;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.function.IntFunction;
import net.minecraft.commands.arguments.StringRepresentableArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.ByIdMap.OutOfBoundsStrategy;

public enum SyncVerbosity implements StringRepresentable {
   DEFAULT(WildfireLang.SYNC_LOG_VERBOSITY_DEFAULT),
   SHOW_FETCHES(WildfireLang.SYNC_LOG_VERBOSITY_SHOW_FETCHES);

   public static final IntFunction<SyncVerbosity> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), OutOfBoundsStrategy.CLAMP);
   public static final Codec<SyncVerbosity> CODEC = StringRepresentable.fromEnum(SyncVerbosity::values);
   public static final Codec<SyncVerbosity> CODEC_OR_LEGACY = CODEC.withAlternative(ExtraCodecs.idResolverCodec(Enum::ordinal, BY_ID, 0));
   public static final StreamCodec<ByteBuf, SyncVerbosity> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);
   private final String saveName = this.name().toLowerCase(Locale.ROOT);
   private final WildfireLang name;

   SyncVerbosity(WildfireLang name) {
      this.name = name;
   }

   public String getSerializedName() {
      return this.saveName;
   }

   public Component getTranslatedName() {
      return this.name.translate();
   }

   public static class SyncVerbosityArgumentType extends StringRepresentableArgument<SyncVerbosity> {
      public SyncVerbosityArgumentType() {
         super(StringRepresentable.fromEnum(SyncVerbosity::values), SyncVerbosity::values);
      }
   }
}
