# ============================================================================
# BuildingBox — R8 / ProGuard rules for the Android RELEASE build.
# Goal: shrink + optimize the APK while guaranteeing zero runtime crashes.
#
# Most libraries here (Compose, Firebase, coroutines, datetime, lifecycle,
# kotlinx.serialization) ship their own *consumer* R8 rules, which AGP applies
# automatically. The rules below are explicit safety nets for the parts that
# reflect at runtime in this app — primarily kotlinx.serialization models that
# are encoded/decoded through the Firebase gateway.
# ============================================================================

# --- Crashlytics: keep readable stack traces (line numbers + source name) ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Attributes required by serialization / generics / annotations ----------
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault,*Annotation*

# ============================================================================
# kotlinx.serialization
# Canonical rules (cover R8 full mode). The runtime artifact also bundles these,
# but keeping them here makes the build self-documenting and future-proof.
# ============================================================================
-keepclassmembers class **$$serializer { *; }

# Keep `Companion` of every @Serializable class.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companions (default + named) of @Serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of @Serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-dontnote kotlinx.serialization.**

# --- This app's @Serializable DTOs / domain models (extra explicit safety) ---
-keep,includedescriptorclasses class com.buildingbox.app.**$$serializer { *; }
-keepclassmembers class com.buildingbox.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers,allowobfuscation class com.buildingbox.app.** {
    @kotlinx.serialization.Serializable <methods>;
}

# --- Serialized enums (e.g. expense categories) keep values()/valueOf() ------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================================
# Firebase (Crashlytics / Analytics / Realtime Database) + GitLive wrapper
# These ship consumer rules; the following are quiet-down nets so R8 full mode
# doesn't fail on optional/absent references.
# ============================================================================
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontwarn dev.gitlive.firebase.**

# ============================================================================
# Coroutines / datetime — consumer rules exist; nets for optional deps.
# ============================================================================
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.datetime.**
-dontwarn org.slf4j.**

# ============================================================================
# Koin — resolves by KClass / constructor refs (no reflection on our types).
# No keep rules required; left here as documentation.
# ============================================================================
