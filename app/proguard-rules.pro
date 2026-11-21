# Room entities and DAOs
-keep class com.smartaccountant.pro.data.model.** { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Entity class * {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers @androidx.room.Dao class * {
    @androidx.room.* <methods>;
}
-keep class * implements androidx.room.TypeConverter { *; }

# Gson serialized names
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# Kotlin
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Kotlinx coroutines / serialization
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ML Kit
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_vision_*.**

# CameraX
-dontwarn androidx.camera.**

# WorkManager
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# App models used with Gson/Firestore
-keep class com.smartaccountant.pro.** { *; }

