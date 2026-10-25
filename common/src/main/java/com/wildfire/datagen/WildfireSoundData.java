package com.wildfire.datagen;

import com.wildfire.client.ClientHelper;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireLang;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class WildfireSoundData {
   private WildfireSoundData() {
   }

   public static <RESULT> void generateSounds(BiConsumer<Holder<SoundEvent>, RESULT> builder, WildfireSoundData.SoundCreator<RESULT> soundCreator) {
      builder.accept(
         ClientHelper.INSTANCE.femaleHurt(),
         soundCreator.create(WildfireLang.HURT_SOUND_SUBTITLE.getTranslationKey(), WildfireGender.id("female_damage"), WildfireGender.id("female_damage2"))
      );
   }

   @FunctionalInterface
   public interface SoundCreator<RESULT> {
      RESULT create(String var1, Identifier... var2);
   }
}
