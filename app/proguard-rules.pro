# Keep the generic type and annotation metadata used by JSON/XML adapters.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault

# Kotlin serialization resolves generated serializers from their companion objects.
-keep,allowoptimization,allowaccessmodification class **$$serializer { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers,includedescriptorclasses class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager persists worker class names and creates workers after process restarts.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Verbose/debug log calls must not survive a release build. Authentication headers
# are never logged by application code, and no HTTP logging interceptor is included.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
