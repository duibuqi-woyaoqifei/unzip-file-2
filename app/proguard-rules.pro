# Project specific ProGuard rules
-keep class com.example.unzipfile.NativeLib {
    native <methods>;
}

# Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Retrofit rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses

# Keep model classes from being obfuscated/shrunk (used for JSON serialization)
-keep class com.example.unzipfile.network.** { *; }
-keep class com.example.unzipfile.membership.PaymentModels { *; }
-keep class com.example.unzipfile.membership.UserProfile { *; }
-keep class com.example.unzipfile.membership.AdConfig { *; }
-keep class com.example.unzipfile.membership.AdItem { *; }

# Alipay SDK rules
# 友盟 SDK 混淆规则
-keep class com.umeng.** { *; }
-keep class com.uc.** { *; }
-keep interface com.umeng.** { *; }
-keep enum com.umeng.** { *; }

# 友盟 Union (广告) 专项
-keep class com.umeng.umsdk.union.** { *; }
-keep class com.umeng.umsdk.common.** { *; }
-keep class com.umeng.umsdk.asms.** { *; }

# 友盟 U-Link
-keep class com.umeng.umlink.** { *; }

# 防止资源混淆影响广告
-keepclassmembers class * {
   @android.webkit.JavascriptInterface <methods>;
}
-keep class com.alipay.** { *; }
-dontwarn com.alipay.**
