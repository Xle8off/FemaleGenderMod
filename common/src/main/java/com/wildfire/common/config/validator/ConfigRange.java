package com.wildfire.common.config.validator;

import com.mojang.serialization.DataResult;
import java.util.function.Supplier;

public record ConfigRange<TYPE extends Comparable<TYPE>>(TYPE minInclusive, TYPE maxInclusive) implements ConfigValidator<TYPE> {
   public boolean validate(TYPE value) {
      return value.compareTo(this.minInclusive) >= 0 && value.compareTo(this.maxInclusive) <= 0;
   }

   public DataResult<TYPE> codecValidation(TYPE value) {
      if (this.validate(value)) {
         return DataResult.success(value);
      }

      Supplier<String> errorMessage = () -> "Value must be within range [" + this.minInclusive + ";" + this.maxInclusive + "]: " + value;
      return value instanceof Float val
         ? DataResult.error(errorMessage, (TYPE)(Object)Float.valueOf(Math.clamp(val, (Float)this.minInclusive, (Float)this.maxInclusive)))
         : DataResult.error(errorMessage);
   }
}
