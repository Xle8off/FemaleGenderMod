package com.wildfire.common.entitydata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildfire.common.WildfireHelper;
import com.wildfire.common.config.validator.ConfigRange;
import com.wildfire.common.config.value.ConfigKey;
import com.wildfire.common.config.value.ConfigValue;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public record Breasts(
   ConfigValue<Float> xOffset,
   ConfigValue<Float> yOffset,
   ConfigValue<Float> zOffset,
   ConfigValue<Float> bustSize,
   ConfigValue<Float> cleavage,
   Breasts.Physics physics
) {
   public static final ConfigKey<Float> BREASTS_OFFSET_X = ConfigKey.create(0.0F, -1.0F, 1.0F);
   public static final ConfigKey<Float> BREASTS_OFFSET_Y = ConfigKey.create(0.0F, -1.0F, 1.0F);
   public static final ConfigKey<Float> BREASTS_OFFSET_Z = ConfigKey.create(0.0F, -1.0F, 0.0F);
   public static final ConfigKey<Float> BUST_SIZE = ConfigKey.create(0.6F, 0.0F, 0.8F);
   public static final ConfigKey<Float> BREASTS_CLEAVAGE = ConfigKey.create(0.0F, 0.0F, 0.1F);
   public static final Codec<Vector3fc> OFFSET_CODEC = WildfireHelper.validatedVector(
         (ConfigRange<Float>)BREASTS_OFFSET_X.validator(), (ConfigRange<Float>)BREASTS_OFFSET_Y.validator(), (ConfigRange<Float>)BREASTS_OFFSET_Z.validator()
      )
      .promotePartial(var0 -> {});
   public static final Codec<Breasts> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            OFFSET_CODEC.fieldOf("offset").forGetter(Breasts::offset),
            BUST_SIZE.codec().fieldOf("size").forGetter(config -> config.bustSize.get()),
            BREASTS_CLEAVAGE.codec().fieldOf("cleavage").forGetter(breasts -> breasts.cleavage.get()),
            Breasts.Physics.CODEC.fieldOf("physics").forGetter(Breasts::physics)
         )
         .apply(instance, Breasts::new)
   );
   public static final MapCodec<Breasts> LEGACY_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            BREASTS_OFFSET_X.codecOrDefault("breasts_xOffset").forGetter(breasts -> breasts.xOffset.get()),
            BREASTS_OFFSET_Y.codecOrDefault("breasts_yOffset").forGetter(breasts -> breasts.yOffset.get()),
            BREASTS_OFFSET_Z.codecOrDefault("breasts_zOffset").forGetter(breasts -> breasts.zOffset.get()),
            BUST_SIZE.codecOrDefault("bust_size").forGetter(config -> config.bustSize.get()),
            BREASTS_CLEAVAGE.codecOrDefault("breasts_cleavage").forGetter(breasts -> breasts.cleavage.get()),
            Breasts.Physics.LEGACY_CODEC.forGetter(Breasts::physics)
         )
         .apply(instance, Breasts::new)
   );
   public static final MapCodec<Breasts> CODEC_OR_LEGACY = WildfireHelper.withAlternative(CODEC.fieldOf("breasts"), LEGACY_CODEC);
   public static final StreamCodec<ByteBuf, Breasts> STREAM_CODEC = StreamCodec.composite(
      BREASTS_OFFSET_X.streamCodec(),
      breasts -> breasts.xOffset.get(),
      BREASTS_OFFSET_Y.streamCodec(),
      breasts -> breasts.yOffset.get(),
      BREASTS_OFFSET_Z.streamCodec(),
      breasts -> breasts.zOffset.get(),
      BUST_SIZE.streamCodec(),
      breasts -> breasts.bustSize.get(),
      BREASTS_CLEAVAGE.streamCodec(),
      breasts -> breasts.cleavage.get(),
      Breasts.Physics.STREAM_CODEC,
      Breasts::physics,
      Breasts::new
   );

   private Breasts(Vector3fc offset, float bustSize, float cleavage, Breasts.Physics physics) {
      this(offset.x(), offset.y(), offset.z(), bustSize, cleavage, physics);
   }

   private Breasts(float xOffset, float yOffset, float zOffset, float bustSize, float cleavage, Breasts.Physics physics) {
      this(
         BREASTS_OFFSET_X.createValueHandler(xOffset),
         BREASTS_OFFSET_Y.createValueHandler(yOffset),
         BREASTS_OFFSET_Z.createValueHandler(zOffset),
         BUST_SIZE.createValueHandler(bustSize),
         BREASTS_CLEAVAGE.createValueHandler(cleavage),
         physics
      );
   }

   public Vector3f offset() {
      return new Vector3f(this.xOffset.get(), this.yOffset.get(), this.zOffset.get());
   }

   public void updateFromComponent(BreastDataComponent component) {
      this.physics.enabled().update(false);
      this.xOffset.update(component.offsets().x());
      this.yOffset.update(component.offsets().y());
      this.zOffset.update(component.offsets().z());
      this.bustSize.update(component.breastSize());
      this.cleavage.update(component.cleavage());
   }

   public List<String> getDebugInfo() {
      List<String> info = new ArrayList<>();
      info.add("Breast size: " + this.bustSize());
      info.add("Physics enabled: " + this.physics().enabled());
      info.add("Uniboob: " + this.physics().uniboob());
      info.add("Cleavage: " + this.cleavage());
      info.add("Offsets: (" + this.xOffset.get() + ", " + this.yOffset.get() + ", " + this.zOffset.get() + ")");
      return info;
   }

   public record Physics(ConfigValue<Boolean> enabled, ConfigValue<Boolean> uniboob, ConfigValue<Float> bounceMultiplier, ConfigValue<Float> floppiness) {
      private static final ConfigKey<Boolean> BREAST_PHYSICS = ConfigKey.DEFAULT_TRUE;
      private static final ConfigKey<Boolean> BREASTS_UNIBOOB = ConfigKey.DEFAULT_TRUE;
      private static final ConfigKey<Float> BOUNCE_MULTIPLIER = ConfigKey.create(0.333F, 0.0F, 0.5F);
      private static final ConfigKey<Float> FLOPPINESS = ConfigKey.create(0.75F, 0.25F, 1.0F);
      private static final Codec<Breasts.Physics> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(
               BREAST_PHYSICS.codec().fieldOf("enabled").forGetter(physics -> physics.enabled.get()),
               BREASTS_UNIBOOB.codec().fieldOf("uniboob").forGetter(physics -> physics.uniboob.get()),
               BOUNCE_MULTIPLIER.codec().fieldOf("bounce_multiplier").forGetter(physics -> physics.bounceMultiplier.get()),
               FLOPPINESS.codec().fieldOf("floppiness").forGetter(physics -> physics.floppiness.get())
            )
            .apply(instance, Breasts.Physics::new)
      );
      private static final MapCodec<Breasts.Physics> LEGACY_CODEC = RecordCodecBuilder.mapCodec(
         instance -> instance.group(
               BREAST_PHYSICS.codecOrDefault("breast_physics").forGetter(physics -> physics.enabled.get()),
               BREASTS_UNIBOOB.codecOrDefault("breasts_uniboob").forGetter(physics -> physics.uniboob.get()),
               BOUNCE_MULTIPLIER.codecOrDefault("bounce_multiplier").forGetter(physics -> physics.bounceMultiplier.get()),
               FLOPPINESS.codecOrDefault("floppy_multiplier").forGetter(physics -> physics.floppiness.get())
            )
            .apply(instance, Breasts.Physics::new)
      );
      private static final StreamCodec<ByteBuf, Breasts.Physics> WITH_PHYSICS_STREAM_CODEC = StreamCodec.composite(
         BREASTS_UNIBOOB.streamCodec(),
         physics -> physics.uniboob.get(),
         BOUNCE_MULTIPLIER.streamCodec(),
         physics -> physics.bounceMultiplier.get(),
         FLOPPINESS.streamCodec(),
         physics -> physics.floppiness.get(),
         (uniboob, bounceMultiplier, floppiness) -> new Breasts.Physics(true, uniboob, bounceMultiplier, floppiness)
      );
      private static final StreamCodec<ByteBuf, Breasts.Physics> STREAM_CODEC = new StreamCodec<ByteBuf, Breasts.Physics>() {
         public Breasts.Physics decode(ByteBuf input) {
            return input.readBoolean()
               ? (Breasts.Physics)Breasts.Physics.WITH_PHYSICS_STREAM_CODEC.decode(input)
               : new Breasts.Physics(
                  false,
                  Breasts.Physics.BREASTS_UNIBOOB.defaultValue(),
                  Breasts.Physics.BOUNCE_MULTIPLIER.defaultValue(),
                  Breasts.Physics.FLOPPINESS.defaultValue()
               );
         }

         public void encode(ByteBuf output, Breasts.Physics physics) {
            if (physics.enabled.get()) {
               output.writeBoolean(true);
               Breasts.Physics.WITH_PHYSICS_STREAM_CODEC.encode(output, physics);
            } else {
               output.writeBoolean(false);
            }
         }
      };

      private Physics(boolean physics, boolean uniboob, float bounceMultiplier, float floppiness) {
         this(
            BREAST_PHYSICS.createValueHandler(physics),
            BREASTS_UNIBOOB.createValueHandler(uniboob),
            BOUNCE_MULTIPLIER.createValueHandler(bounceMultiplier),
            FLOPPINESS.createValueHandler(floppiness)
         );
      }
   }
}
