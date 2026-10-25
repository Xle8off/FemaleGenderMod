package com.wildfire.common.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.wildfire.common.LoaderAgnostics;
import com.wildfire.common.WildfireGender;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.util.GsonHelper;

public class Configuration<TYPE> {
   public static final String CONFIG_DIR = "FemaleGenderMod";
   private final Codec<TYPE> codec;
   private final File cfgFile;

   public Configuration(String cfgName, Codec<TYPE> codec) {
      this("FemaleGenderMod", cfgName, codec);
   }

   public Configuration(String directory, String cfgName, Codec<TYPE> codec) {
      this.codec = codec;
      Path saveDir = LoaderAgnostics.INSTANCE.getConfigDir().resolve(directory);
      if (supportsSaving() && !Files.isDirectory(saveDir)) {
         try {
            Files.createDirectory(saveDir);
         } catch (IOException e) {
            WildfireGender.LOGGER.error("Failed to create config directory", e);
         }
      }

      this.cfgFile = saveDir.resolve(cfgName + ".json").toFile();
   }

   public static boolean supportsSaving() {
      return LoaderAgnostics.INSTANCE.onClient();
   }

   public boolean exists() {
      return this.cfgFile.exists();
   }

   public void save(TYPE value) {
      if (supportsSaving()) {
         Optional<JsonElement> result = this.codec.encodeStart(JsonOps.INSTANCE, value).resultOrPartial();
         if (result.isPresent()) {
            try (FileWriter writer = new FileWriter(this.cfgFile, StandardCharsets.UTF_8)) {
               JsonWriter jsonWriter = new JsonWriter(writer);

               try {
                  jsonWriter.setIndent("\t");
                  GsonHelper.writeValue(jsonWriter, result.get(), Comparator.naturalOrder());
               } catch (Throwable var9) {
                  try {
                     jsonWriter.close();
                  } catch (Throwable var8) {
                     var9.addSuppressed(var8);
                  }

                  throw var9;
               }

               jsonWriter.close();
            } catch (IOException e) {
               WildfireGender.LOGGER.error("Failed to save config file", e);
            }
         }
      }
   }

   public TYPE load() {
      return (TYPE)this.codec.parse(JsonOps.INSTANCE, this.read()).getOrThrow();
   }

   private JsonObject read() {
      if (supportsSaving() && this.cfgFile.exists()) {
         try (FileReader configurationFile = new FileReader(this.cfgFile, StandardCharsets.UTF_8)) {
            return GsonHelper.parse(configurationFile);
         } catch (IOException e) {
            WildfireGender.LOGGER.error("Failed to load config file", e);
         }
      }

      return new JsonObject();
   }
}
