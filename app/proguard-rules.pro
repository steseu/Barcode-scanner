# ML Kit barcode scanning models are loaded via reflection.
-keep class com.google.mlkit.vision.barcode.** { *; }

# Room entities/DAOs accessed via generated code.
-keep class com.barcodebridge.app.data.local.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}
