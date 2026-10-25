package com.wildfire.common.config.value;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.wildfire.common.config.validator.ConfigRange;
import com.wildfire.common.config.validator.ConfigValidator;
import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

public record ConfigKey<TYPE>(
   Either<TYPE, Supplier<TYPE>> defaultValueSupplier, Codec<TYPE> codec, StreamCodec<ByteBuf, TYPE> streamCodec, ConfigValidator<TYPE> validator
) {
   private static final ConfigValidator<?> ALWAYS_TRUE = new ConfigValidator<Object>() {
      @Override
      public boolean validate(Object value) {
         return true;
      }

      @Override
      public DataResult<Object> codecValidation(Object value) {
         return DataResult.success(value);
      }

      @Override
      public boolean hasCodecValidation() {
         return false;
      }
   };
   public static final ConfigKey<Boolean> DEFAULT_TRUE = new ConfigKey<>(true, Codec.BOOL, ByteBufCodecs.BOOL);
   public static final ConfigKey<Boolean> DEFAULT_FALSE = new ConfigKey<>(false, Codec.BOOL, ByteBufCodecs.BOOL);

   public ConfigKey {
      if (validator.hasCodecValidation()) {
         codec = codec.validate(validator::codecValidation).promotePartial(var0 -> {});
         final StreamCodec<ByteBuf, TYPE> finalStreamCodec = streamCodec;
         streamCodec = new StreamCodec<ByteBuf, TYPE>() {
            public TYPE decode(ByteBuf input) {
               TYPE decoded = (TYPE)finalStreamCodec.decode(input);
               return validator.codecValidation(decoded).resultOrPartial().orElseGet(ConfigKey.this::defaultValue);
            }

            public void encode(ByteBuf output, TYPE value) {
               finalStreamCodec.encode(output, value);
            }
         };
      }
   }

   public ConfigKey(TYPE defaultValue, Codec<TYPE> baseCodec, StreamCodec<ByteBuf, TYPE> streamCodec) {
      this(defaultValue, baseCodec, streamCodec, alwaysTrueValidator());
   }

   public ConfigKey(TYPE defaultValue, Codec<TYPE> baseCodec, StreamCodec<ByteBuf, TYPE> streamCodec, ConfigValidator<TYPE> range) {
      this(Either.left(defaultValue), baseCodec, streamCodec, range);
   }

   public ConfigKey(Supplier<TYPE> defaultValueSupplier, Codec<TYPE> baseCodec, StreamCodec<ByteBuf, TYPE> streamCodec) {
      this(defaultValueSupplier, baseCodec, streamCodec, alwaysTrueValidator());
   }

   public ConfigKey(Supplier<TYPE> defaultValueSupplier, Codec<TYPE> baseCodec, StreamCodec<ByteBuf, TYPE> streamCodec, ConfigValidator<TYPE> range) {
      this(Either.right(defaultValueSupplier), baseCodec, streamCodec, range);
   }

   public static <TYPE> ConfigValidator<TYPE> alwaysTrueValidator() {
      return (ConfigValidator<TYPE>)ALWAYS_TRUE;
   }

   public TYPE defaultValue() {
      return (TYPE)this.defaultValueSupplier.map(Function.identity(), Supplier::get);
   }

   @Contract("null -> false")
   public boolean validate(@Nullable TYPE value) {
      return value != null && this.validator.validate(value);
   }

   public MapCodec<TYPE> codecOrDefault(String key) {
      return this.codec.fieldOf(key).orElseGet(this::defaultValue);
   }

   public ConfigValue<TYPE> createValueHandler(TYPE value) {
      return new KeyBackedConfigValue<>(this, value);
   }

   public static ConfigKey<Float> create(float defaultValue, float minInclusive, float maxInclusive) {
      return new ConfigKey<>(defaultValue, Codec.FLOAT, ByteBufCodecs.FLOAT, new ConfigRange<>(minInclusive, maxInclusive));
   }
}
