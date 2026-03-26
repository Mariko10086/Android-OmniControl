# Retrofit + OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Gson data models — keep all field names for JSON serialization
-keep class com.omnicontrol.agent.data.model.** { *; }
-keep class com.omnicontrol.agent.network.auth.** { *; }

# HiveMQ MQTT Client
-keep class com.hivemq.** { *; }
-dontwarn io.netty.**
-dontwarn org.slf4j.**

# WorkManager workers
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
