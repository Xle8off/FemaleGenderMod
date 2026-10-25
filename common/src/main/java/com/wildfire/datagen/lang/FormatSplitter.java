package com.wildfire.datagen.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatSplitter {
   private static final Pattern fsPattern = Pattern.compile("%(\\d+\\$)?([-#+ 0,(<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z])");

   public static List<FormatSplitter.Component> split(String text) {
      Matcher matcher = fsPattern.matcher(text);
      List<FormatSplitter.Component> components = new ArrayList<>();

      int start;
      for (start = 0; matcher.find(); start = matcher.end()) {
         int curStart = matcher.start();
         if (curStart > start) {
            components.add(new FormatSplitter.TextComponent(text.substring(start, curStart)));
         }

         String piece = matcher.group();
         components.add(new FormatSplitter.FormatComponent(piece));
      }

      if (start < text.length()) {
         components.add(new FormatSplitter.TextComponent(text.substring(start)));
      }

      return List.copyOf(components);
   }

   public interface Component {
      String contents();
   }

   public static class FormatComponent implements FormatSplitter.Component {
      private final String formattingCode;

      private FormatComponent(String formattingCode) {
         this.formattingCode = formattingCode;
      }

      @Override
      public String contents() {
         return this.formattingCode;
      }
   }

   private record TextComponent(String contents) implements FormatSplitter.Component {
   }
}
