# Nekuva R8/ProGuard rules (release). Keeps for the reflection- / serialization-dependent parts so the
# shrunk release behaves like debug. Erring on the side of keeping (this is a beta release build).

# ---------- Kotlin metadata / attributes ----------
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Enums are reflected on by name (valueOf/name) all over (e.g. MangaParserSource, ReaderMode).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    *** name();
}

# ---------- kotlinx.serialization (official R8 rules + app/exts coverage) ----------
-dontnote kotlinx.serialization.**
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
# Generated serializers + companions for every Nekuva @Serializable class (models, routes, DTOs, backup).
-keep,includedescriptorclasses class org.nekosukuriputo.nekuva.**$$serializer { *; }
-keepclassmembers class org.nekosukuriputo.nekuva.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keep class <1> { *; }

# ---------- nekuva-exts (parsers + models): KSP-registered parsers + reflective access; keep wholesale ----------
-keep class org.nekosukuriputo.nekuva.parsers.** { *; }
-keep class org.nekosukuriputo.nekuva.core.db.entity.** { *; }
-keep class org.nekosukuriputo.nekuva.**.data.model.** { *; }

# ---------- Runtime extensions (DexClassLoader plugin) ----------
# The downloaded/imported bundle is loaded at runtime via a child-first DexClassLoader and links against the
# HOST's shared contract through reflection. If R8 renames/removes any of the host loader, the plugin model,
# or a class the bundle links against, loadExtension() throws and the UI shows
# "Incompatible or invalid extension bundle". Keep them ABI-stable + the HTML/HTTP libs the parsers use.
-keep class org.nekosukuriputo.nekuva.core.extensions.** { *; }
-keep class org.nekosukuriputo.nekuva.core.model.** { *; }
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class androidx.collection.** { *; }

# ---------- Koin DI ----------
-keep class org.koin.** { *; }
-dontwarn org.koin.**
-keepnames class * extends org.koin.core.module.Module

# ---------- Room ----------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.**

# ---------- OkHttp / Okio / DoH ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.publicsuffix.** { *; }

# ---------- Conscrypt (TLS provider) ----------
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# ---------- KizzyRPC (Discord Rich Presence, Android) ----------
-keep class com.my.kizzy.** { *; }
-dontwarn com.my.kizzy.**

# ---------- Coil 3 ----------
-dontwarn coil3.**

# ---------- AVIF decoder (native) ----------
-keep class org.aomedia.avif.android.** { *; }

# ---------- org.json (bundled on Android) ----------
-dontwarn org.json.**

# ---------- Misc desktop-only deps referenced in shared code (harmless to ignore on Android) ----------
-dontwarn org.cef.**
-dontwarn dev.datlag.kcef.**
-dontwarn java.awt.**
-dontwarn javax.swing.**
