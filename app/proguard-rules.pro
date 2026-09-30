# Proguard & R8 Rules for MadoGit (Production Release)

# Preserve source file and line numbers for crash reporting and debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Annotations and Generic Signatures for reflection/serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Retrofit 2 rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Moshi rules
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonClass <methods>;
}
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keep class com.aipos.madogit.data.api.models.** { *; }
-keepclassmembers class com.aipos.madogit.data.api.models.** { *; }

# OkHttp 3 & Okio rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Room Database rules
-keep class * extends androidx.room.RoomDatabase
-keep class com.aipos.madogit.data.database.entities.** { *; }
-keep class com.aipos.madogit.data.database.daos.** { *; }
-keep class com.aipos.madogit.data.database.AppDatabase { *; }
-keep class com.aipos.madogit.data.database.AppDatabase_Impl { *; }
-dontwarn androidx.room.paging.**

# WorkManager rules
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class com.aipos.madogit.worker.** { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# Coil image loading
-keep class coil.** { *; }
-dontwarn coil.**
