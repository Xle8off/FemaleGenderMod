package com.wildfire.common.config.value;

import com.wildfire.common.config.validator.ConfigValidator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface ConfigValue<TYPE> extends Supplier<TYPE>, Consumer<TYPE> {
   UnaryOperator<Boolean> TOGGLE = value -> !value;

   ConfigValidator<TYPE> validator();

   boolean reset();

   default boolean update(UnaryOperator<TYPE> transformer) {
      return this.update(transformer.apply(this.get()));
   }

   boolean update(TYPE var1);

   @Override
   default void accept(TYPE newValue) {
      this.update(newValue);
   }
}
