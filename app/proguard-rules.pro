-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.phinet.app.data.** { *** Companion; }
-keepclasseswithmembers class com.phinet.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
