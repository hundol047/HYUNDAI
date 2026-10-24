# Gson 2.10.1 official R8 guidance (Apache-2.0):
# https://github.com/google/gson/blob/gson-parent-2.10.1/examples/android-proguard-example/proguard.cfg
# Pleos Vehicle capability decoding creates anonymous TypeToken subclasses.
# Signature alone is insufficient in R8 full mode; both endpoints must be retained.
-keepattributes Signature
-keepattributes *Annotation*
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Vehicle's Gson wire models include fields without @SerializedName. Preserve
# their names and generic types so capabilities decode identically in release.
-keep class ai.pleos.playground.vehicle.data.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
