package com.wildfire.common.config.enums;

import com.mojang.serialization.Codec;
import com.wildfire.common.WildfireLang;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.ByIdMap.OutOfBoundsStrategy;

public enum ShowPlayerListMode implements StringRepresentable {
   MOD_UI_ONLY(WildfireLang.PLAYER_LIST_MODE_MOD_UI, WildfireLang.PLAYER_LIST_MODE_MOD_UI_TOOLTIP),
   TAB_LIST_OPEN(WildfireLang.PLAYER_LIST_MODE_TAB_LIST, WildfireLang.PLAYER_LIST_MODE_TAB_LIST_TOOLTIP),
   ALWAYS(WildfireLang.PLAYER_LIST_MODE_ALWAYS, WildfireLang.PLAYER_LIST_MODE_ALWAYS_TOOLTIP);

   public static final IntFunction<ShowPlayerListMode> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), OutOfBoundsStrategy.WRAP);
   public static final Codec<ShowPlayerListMode> CODEC = StringRepresentable.fromEnum(ShowPlayerListMode::values);
   public static final Codec<ShowPlayerListMode> CODEC_OR_LEGACY = CODEC.withAlternative(ExtraCodecs.idResolverCodec(Enum::ordinal, BY_ID, 0));
   public static final StreamCodec<ByteBuf, ShowPlayerListMode> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);
   private final String saveName = this.name().toLowerCase(Locale.ROOT);
   private final WildfireLang name;
   private final WildfireLang tooltip;

   ShowPlayerListMode(WildfireLang name, WildfireLang tooltip) {
      this.name = name;
      this.tooltip = tooltip;
   }

   public String getSerializedName() {
      return this.saveName;
   }

   public ShowPlayerListMode next() {
      return BY_ID.apply(this.ordinal() + 1);
   }

   public boolean isVisible() {
      return switch (this) {
         case MOD_UI_ONLY -> false;
         case TAB_LIST_OPEN -> Minecraft.getInstance().gui.hud.getTabList().visible;
         case ALWAYS -> true;
      };
   }

   public Tooltip tooltip() {
      return this == TAB_LIST_OPEN
         ? Tooltip.create(this.tooltip.translate(Minecraft.getInstance().options.keyPlayerList.getTranslatedKeyMessage()))
         : Tooltip.create(this.tooltip.translate());
   }

   public Component getTranslatedName() {
      return this.name.translate();
   }
}
