# No shrinking/optimizer: Minecraft discovers code through metadata, mixins and reflection.
-dontshrink
-dontoptimize
-overloadaggressively
-dontusemixedcaseclassnames
-flattenpackagehierarchy sp
-adaptclassstrings
-keepattributes Exceptions,InnerClasses,EnclosingMethod,Signature,*Annotation*,Record,NestHost,NestMembers,PermittedSubclasses,LineNumberTable
-renamesourcefileattribute Source

# Loader metadata and runtime injection must keep stable names.
-keep class com.wildfire.mixins.** { *; }
-keep class com.wildfire.**.mixin.** { *; }
-keep class com.wildfire.**.mixins.** { *; }
-keep class * implements net.fabricmc.api.ModInitializer { *; }
-keep class * implements net.fabricmc.api.ClientModInitializer { *; }
-keep class * implements net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint { *; }
-keep class * implements com.terraformersmc.modmenu.api.ModMenuApi { *; }
-keep @net.neoforged.fml.common.Mod class * { *; }
-keep,includedescriptorclasses class com.wildfire.api.** { *; }
-keep class **.package-info
-keep class * implements com.sun.jna.Library { *; }
-keep,includedescriptorclasses interface * extends com.sun.jna.Callback { *; }
-keep class * implements org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# JSON/config fields, records and enum identifiers form persistent/wire contracts.
-keepclassmembers class * { <fields>; }
-keepclassmembers class * extends java.lang.Record { <methods>; }
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
