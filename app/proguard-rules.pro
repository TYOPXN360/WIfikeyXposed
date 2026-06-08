# Xposed Module Rules
-keep class com.ty.wifikeyxposed.** { *; }
-keep class io.github.libxposed.** { *; }

# Keep MainHook class
-keep public class * extends io.github.libxposed.api.XposedModule { *; }

# Kotlin/Compose rules
-dontwarn kotlin.**
-dontwarn androidx.compose.**

# Keep Compose classes
-keep class androidx.compose.** { *; }

# MultiDex
-keep class androidx.multidex.** { *; }

# Keep launcher icon resources
-keepclassmembers class **.R$mipmap {
    <fields>;
}
-keepclassmembers class **.R$drawable {
    <fields>;
}
