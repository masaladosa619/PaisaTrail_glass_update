# Minification is off by default (see app/build.gradle.kts).
# If you enable it, these rules keep the reflection-heavy libraries working.

# PdfBox-Android loads fonts and security handlers reflectively.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**

# ML Kit text recognition.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Room entities are referenced by generated code; enum names are stored in the database.
-keepclassmembers enum com.paisatrail.domain.model.** { *; }
