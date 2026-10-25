package com.wildfire.common.config;

import org.jspecify.annotations.Nullable;

public interface ConfigTranslation {
   String getTranslationKey();

   String title();

   String tooltip();

   default @Nullable String button() {
      return null;
   }

   static @Nullable String getSectionTitle(String title, boolean isSection) {
      return isSection ? "Edit " + title : null;
   }
}
