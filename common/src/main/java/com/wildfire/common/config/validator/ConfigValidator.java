package com.wildfire.common.config.validator;

import com.mojang.serialization.DataResult;

public interface ConfigValidator<TYPE> {
   boolean validate(TYPE var1);

   DataResult<TYPE> codecValidation(TYPE var1);

   default boolean hasCodecValidation() {
      return true;
   }
}
