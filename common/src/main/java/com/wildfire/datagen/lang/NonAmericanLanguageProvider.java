package com.wildfire.datagen.lang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.Util;

public class NonAmericanLanguageProvider extends ConvertibleLanguageProvider {
   private static final List<NonAmericanLanguageProvider.WordConversion> CONVERSIONS = Util.make(new HashMap<String,String>(), map -> {
      addEntry(map, "Armor", "Armour");
      addEntry(map, "Personalization", "Personalisation");
      addEntry(map, "Customization", "Customisation");
      addEntry(map, "Customized", "Customised");
   }).entrySet().stream().map(entry -> new NonAmericanLanguageProvider.WordConversion(entry.getKey(), entry.getValue())).toList();

   private static void addEntry(Map<String, String> map, String key, String value) {
      map.put(key, value);
      map.put(key.toLowerCase(Locale.ROOT), value.toLowerCase(Locale.ROOT));
   }

   public NonAmericanLanguageProvider(String locale) {
      super(locale);
   }

   @Override
   public void convert(String key, String raw, List<FormatSplitter.Component> splitEnglish) {
      StringBuilder builder = new StringBuilder();
      boolean foundMatch = false;

      for (FormatSplitter.Component component : splitEnglish) {
         if (component instanceof FormatSplitter.FormatComponent) {
            builder.append(component.contents());
         } else {
            String contents = component.contents();
            List<NonAmericanLanguageProvider.WordConversion> matched = new ArrayList<>();

            for (NonAmericanLanguageProvider.WordConversion conversion : CONVERSIONS) {
               if (conversion.match(contents).find()) {
                  matched.add(conversion);
               }
            }

            if (!matched.isEmpty()) {
               foundMatch = true;

               for (NonAmericanLanguageProvider.WordConversion conversion : matched) {
                  contents = conversion.replace(contents);
               }
            }

            builder.append(contents);
         }
      }

      if (foundMatch) {
         this.add(key, builder.toString());
      }
   }

   private record WordConversion(Pattern matcher, String replacement) {
      private WordConversion(String toReplace, String replacement) {
         this(Pattern.compile("\\b" + toReplace + "\\b"), replacement);
      }

      public Matcher match(String contents) {
         return this.matcher.matcher(contents);
      }

      public String replace(String contents) {
         return this.match(contents).replaceAll(this.replacement);
      }
   }
}
