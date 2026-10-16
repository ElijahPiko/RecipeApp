# Keep Gson model fields
-keep class com.recipe.core.model.** { *; }
-keep class com.recipe.app.data.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
