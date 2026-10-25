package com.wildfire.client.contributors;

import com.google.common.base.Preconditions;
import com.google.gson.annotations.SerializedName;
import com.wildfire.common.WildfireLang;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

public record Contributor(int roles, @Nullable Integer color, @Nullable String name, @SerializedName("show_in_credits") @Nullable Boolean showInCredits) {
   private static final TextColor DEFAULT_COLOR = TextColor.GOLD;

   public TextColor getColor() {
      return this.color != null ? TextColor.fromRgb(this.color) : this.getRole().getColor();
   }

   public Component asText() {
      return this.getRole().langEntry.translateColored(this.getColor());
   }

   public Contributor.Role getRole() {
      if (this.roles == 0) {
         return Contributor.Role.GENERIC;
      }

      for (Contributor.Role role : Contributor.Role.values()) {
         if (role.isIn(this.roles)) {
            return role;
         }
      }

      return Contributor.Role.GENERIC;
   }

   public enum Role {
      MOD_CREATOR(0, WildfireLang.CONTRIBUTOR_ROLE_MOD_CREATOR, TextColor.LIGHT_PURPLE),
      FABRIC_MAINTAINER(1, WildfireLang.CONTRIBUTOR_ROLE_FABRIC_MAINTAINER, 10981375),
      NEOFORGE_MAINTAINER(2, WildfireLang.CONTRIBUTOR_ROLE_NEO_MAINTAINER, 10981375),
      CI_MAINTAINER(8, WildfireLang.CONTRIBUTOR_ROLE_CI_MAINTAINER, 5294200),
      DEVELOPER(3, WildfireLang.CONTRIBUTOR_ROLE_DEVELOPER),
      TRANSLATOR(4, WildfireLang.CONTRIBUTOR_ROLE_TRANSLATOR, 6737151),
      MASCOT(5, WildfireLang.CONTRIBUTOR_ROLE_MASCOT),
      VOICE_ACTOR_FEMALE(6, WildfireLang.CONTRIBUTOR_ROLE_FEMALE_VOICE_ACTOR),
      GENERIC(7, WildfireLang.CONTRIBUTOR_ROLE_GENERIC);

      private final int bit;
      private final WildfireLang langEntry;
      private final @Nullable TextColor color;

      Role(int bit, WildfireLang langEntry, int color) {
         this(bit, langEntry, TextColor.fromRgb(color));
      }

      Role(int bit, WildfireLang langEntry, @Nullable TextColor color) {
         this.bit = 1 << bit;
         this.langEntry = langEntry;
         this.color = color;
      }

      Role(int bit, WildfireLang langEntry) {
         this(bit, langEntry, null);
      }

      public int bit() {
         return this.bit;
      }

      public boolean isIn(int bitmask) {
         return (bitmask & this.bit()) == this.bit();
      }

      public TextColor getColor() {
         return this.color == null ? Contributor.DEFAULT_COLOR : this.color;
      }

      public MutableComponent withColor(MutableComponent text) {
         Preconditions.checkNotNull(text);
         return text.withColor(this.getColor());
      }

      public MutableComponent shortName() {
         return this.langEntry.translateShort();
      }
   }
}
