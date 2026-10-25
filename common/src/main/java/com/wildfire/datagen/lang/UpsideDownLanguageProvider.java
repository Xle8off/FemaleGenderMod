package com.wildfire.datagen.lang;

import java.util.List;

public class UpsideDownLanguageProvider extends ConvertibleLanguageProvider {
   private static final String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789,.?!;\"'`&_^()[]{}<>≤≥";
   private static final char[] upside_down = "ɐqɔpǝɟᵷɥᴉɾʞꞁɯuodbɹsʇnʌʍxʎzⱯᗺƆᗡƎℲ⅁HIՐꞰꞀWNOԀꝹᴚS⟘∩ΛMX⅄Z0⥝ᘔƐ߈ϛ9ㄥ86'˙¿¡؛„,,⅋‾v)(][}{><⪖⪕".toCharArray();

   public UpsideDownLanguageProvider() {
      super("en_ud");
   }

   @Override
   public void convert(String key, String raw, List<FormatSplitter.Component> splitEnglish) {
      String upsideDown = convertComponents(splitEnglish);
      if (!raw.equals(upsideDown)) {
         this.add(key, upsideDown);
      }
   }

   private static char flip(char c) {
      int index = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789,.?!;\"'`&_^()[]{}<>≤≥".indexOf(c);
      return index == -1 ? c : upside_down[index];
   }

   private static String convertFormattingComponent(FormatSplitter.FormatComponent component, int curIndex, int numArguments) {
      String formattingCode = component.contents();
      int storedIndex = curIndex;
      String[] split = formattingCode.split("\\$");
      String ending;
      if (split.length == 2) {
         ending = split[1];
         storedIndex = Integer.parseInt(split[0].substring(1));
      } else {
         ending = formattingCode.substring(1);
      }

      return storedIndex == numArguments - curIndex + 1 ? "%" + ending : "%" + storedIndex + "$" + ending;
   }

   private static String convertComponents(List<FormatSplitter.Component> splitText) {
      int numArguments = (int)splitText.stream().filter(componentx -> componentx instanceof FormatSplitter.FormatComponent).count();
      StringBuilder converted = new StringBuilder();
      int curIndex = numArguments;

      for (int i = splitText.size() - 1; i >= 0; i--) {
         FormatSplitter.Component component = splitText.get(i);
         if (component instanceof FormatSplitter.FormatComponent formatComponent) {
            converted.append(convertFormattingComponent(formatComponent, curIndex--, numArguments));
         } else {
            char[] toConvertArr = component.contents().toCharArray();

            for (int j = toConvertArr.length - 1; j >= 0; j--) {
               converted.append(flip(toConvertArr[j]));
            }
         }
      }

      return new String(converted);
   }
}
