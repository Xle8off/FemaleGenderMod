package com.wildfire.common.config.value;

import com.wildfire.common.config.validator.ConfigValidator;

public class KeyBackedConfigValue<TYPE> implements ConfigValue<TYPE> {
   private final ConfigKey<TYPE> key;
   private TYPE value;

   KeyBackedConfigValue(ConfigKey<TYPE> key, TYPE value) {
      this.key = key;
      assert key.validate(value);
      this.value = value;
   }

   @Override
   public ConfigValidator<TYPE> validator() {
      return this.key.validator();
   }

   @Override
   public TYPE get() {
      return this.value;
   }

   @Override
   public boolean reset() {
      TYPE defaultValue = this.key.defaultValue();
      if (!this.value.equals(defaultValue)) {
         this.value = defaultValue;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean update(TYPE newValue) {
      if (this.validator().validate(newValue)) {
         this.value = newValue;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public String toString() {
      return this.get().toString();
   }
}
