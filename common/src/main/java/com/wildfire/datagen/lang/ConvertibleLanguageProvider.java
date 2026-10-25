package com.wildfire.datagen.lang;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;

public abstract class ConvertibleLanguageProvider {
   private final Map<String, String> data = new TreeMap<>();
   private final String locale;

   public ConvertibleLanguageProvider(String locale) {
      this.locale = locale;
   }

   public abstract void convert(String var1, String var2, List<FormatSplitter.Component> var3);

   public CompletableFuture<?> save(CachedOutput cache, Function<String, Path> pathCreator) {
      JsonElement json = (JsonElement)Codec.unboundedMap(Codec.STRING, Codec.STRING).encode(this.data, JsonOps.INSTANCE, new JsonObject()).getOrThrow();
      return DataProvider.saveStable(cache, json, pathCreator.apply(this.locale));
   }

   protected void add(String key, String translation) {
      this.data.put(key, translation);
   }
}
