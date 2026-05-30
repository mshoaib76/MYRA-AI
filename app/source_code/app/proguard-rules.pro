-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class org.json.** { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class com.myra.assistant.** { *; }
