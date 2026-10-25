package com.wildfire.common;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.wildfire.common.config.validator.ConfigRange;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.StringJoiner;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class WildfireHelper {
   private static final Logger LOGGER = LogUtils.getLogger();
   @Internal
   private static final ClassLoader SERVICE_CL = WildfireGender.class.getClassLoader();

   private WildfireHelper() {
      throw new UnsupportedOperationException();
   }

   public static float round(float num, float decimalPlaces) {
      float factor = (float)Math.pow(10.0, decimalPlaces);
      return Math.round(num * factor) / factor;
   }

   public static String toFormattedPercent(double value) {
      return ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(value * 100.0);
   }

   public static double snapToStep(double value, double stepSize) {
      return Math.round(value / stepSize) * stepSize;
   }

   public static <T> MapCodec<T> withAlternative(MapCodec<T> primary, MapCodec<? extends T> alternative) {
      return Codec.mapEither(primary, alternative).xmap(Either::unwrap, Either::left);
   }

   public static Codec<Vector3fc> validatedVector(ConfigRange<Float> xRange, ConfigRange<Float> yRange, ConfigRange<Float> zRange) {
      return ExtraCodecs.VECTOR3F
         .validate(
            value -> {
               boolean xValid = xRange.validate(value.x());
               boolean yValid = yRange.validate(value.y());
               boolean zValid = zRange.validate(value.z());
               return xValid && yValid && zValid
                  ? DataResult.success(value)
                  : DataResult.error(
                     () -> {
                        StringJoiner message = new StringJoiner(". ");
                        if (!xValid) {
                           message.add("X value must be within range [" + xRange.minInclusive() + ";" + xRange.maxInclusive() + "]: " + value.x());
                        }

                        if (!yValid) {
                           message.add("Y value must be within range [" + yRange.minInclusive() + ";" + yRange.maxInclusive() + "]: " + value.y());
                        }

                        if (!zValid) {
                           message.add("Z value must be within range [" + zRange.minInclusive() + ";" + zRange.maxInclusive() + "]: " + value.z());
                        }

                        return message.toString();
                     },
                     new Vector3f(
                        Math.clamp(value.x(), xRange.minInclusive(), xRange.maxInclusive()),
                        Math.clamp(value.y(), yRange.minInclusive(), yRange.maxInclusive()),
                        Math.clamp(value.z(), zRange.minInclusive(), zRange.maxInclusive())
                     )
                  );
            }
         );
   }

   @Internal
   public static <SERVICE> SERVICE getService(Class<SERVICE> serviceClass) {
      SERVICE service = getOptionalService(serviceClass);
      if (service != null) {
         return service;
      }

      IllegalStateException illegalStateException = new IllegalStateException("No valid ServiceImpl for " + serviceClass.getSimpleName() + " found");
      LOGGER.error("Failed to load service", illegalStateException);
      LOGGER.error("CL: {} CCL: {}", SERVICE_CL, Thread.currentThread().getContextClassLoader());
      throw illegalStateException;
   }

   @Internal
   public static <SERVICE> @Nullable SERVICE getOptionalService(Class<SERVICE> serviceClass) {
      Iterator<SERVICE> service = ServiceLoader.load(serviceClass, SERVICE_CL).iterator();
      return service.hasNext() ? service.next() : null;
   }
}
