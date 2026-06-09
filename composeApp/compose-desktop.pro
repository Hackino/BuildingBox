# ============================================================================
# BuildingBox — ProGuard rules for the Compose DESKTOP release build.
#
# Used by  compose.desktop.application.buildTypes.release.proguard  (NOT the
# Android block — that uses proguard-rules.pro and carries Firebase/GMS rules
# irrelevant on the JVM desktop target).
#
# Goal: shrink the bundled JVM jars (Compose, Ktor, kotlinx, app code) without
# breaking the parts that resolve reflectively or via ServiceLoader at runtime.
# ProGuard breakage is RUNTIME-only (the build stays green), so exercise sign-in,
# RTDB reads, and report export after building before trusting these rules.
# ============================================================================

# --- Attributes required by serialization / generics / annotations ----------
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault,*Annotation*

# ============================================================================
# kotlinx.serialization — generated serializers are looked up reflectively.
# 10 @Serializable classes in the desktop path (commonMain DTOs + the REST
# request/response data classes in Platform.desktop.kt).
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

# --- Serialized enums keep values()/valueOf() -------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================================
# Ktor client — the CIO engine and content-negotiation plugins are discovered
# through ServiceLoader / reflection; keep their entry points.
# ============================================================================
-keep class io.ktor.client.engine.cio.** { *; }
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.serialization.** { *; }
# ServiceLoader-registered engine container.
-keep class * implements io.ktor.client.engine.HttpClientEngineContainer { *; }
-keep class io.ktor.utils.io.** { *; }
-dontwarn io.ktor.**

# ============================================================================
# kotlinx-coroutines — the Main dispatcher is provided by a ServiceLoader
# factory (kotlinx-coroutines-swing on desktop). Losing it re-introduces the
# "Module with the Main dispatcher is missing" crash, so keep the factories.
# ============================================================================
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class * implements kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.swing.** { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keep class * implements kotlinx.coroutines.CoroutineExceptionHandler { *; }
-dontwarn kotlinx.coroutines.**

# ============================================================================
# Misc nets — optional deps referenced by the libs above.
# ============================================================================
-dontwarn kotlinx.datetime.**
-dontwarn org.slf4j.**

# ============================================================================
# Apache PDFBox / FontBox / pdfbox-graphics2d (desktop PDF report export).
# PDFBox loads resources (glyph lists, ICC profiles) and uses some reflection;
# keep its packages and silence warnings about its optional deps.
# ============================================================================
-keep class org.apache.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }
-keep class de.rototor.pdfbox.** { *; }
-keepclassmembers class org.apache.pdfbox.** { *; }
-dontwarn org.apache.pdfbox.**
-dontwarn org.apache.fontbox.**
-dontwarn de.rototor.pdfbox.**
# PDFBox initializes a logger via Apache Commons Logging in PDDocument.<clinit>.
# Commons Logging loads its impl reflectively by class name, so ProGuard can't see
# it used and strips it → ExceptionInInitializerError at runtime. KEEP it (not just
# -dontwarn, which only silences the warning and still removes the class).
-keep class org.apache.commons.logging.** { *; }
# PDFBox optionally references these; absent on our classpath — don't fail R8.
-dontwarn org.bouncycastle.**
-dontwarn javax.imageio.**

# Skiko bundles JBR-only entry points whose descriptor classes aren't on the
# classpath; and PDFBox/other libs reference assorted optional deps. ProGuard 7.7
# treats unresolved refs as fatal, so silence them broadly.
-dontwarn com.jetbrains.**
-dontwarn org.jetbrains.skiko.**
-dontwarn org.apache.commons.**
-dontwarn org.apache.logging.**
-dontwarn com.google.**
-dontwarn javax.**
-dontwarn java.awt.**
-ignorewarnings

# ============================================================================
# Koin — resolves by KClass / constructor refs (no reflection on our types).
# No keep rules required; left here as documentation.
# ============================================================================
