# Keep kotlinx.serialization generated serializers for the shared core's model types.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class org.fisabilillah.core.model.**$$serializer { *; }
-keepclassmembers class org.fisabilillah.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class org.fisabilillah.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Value classes used as typed identifiers throughout the core.
-keep class org.fisabilillah.core.model.*Id { *; }
