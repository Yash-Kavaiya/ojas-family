# Keep file names and line numbers so Crashlytics stack traces from release builds can be read
# (the Crashlytics plugin uploads the mapping file; without these attributes lines show as "Unknown Source").
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WebRTC calls into these classes from native code by name
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Crashlytics groups reports by exception class: keep our own exception names readable
-keepnames class com.ojashealing.family.** extends java.lang.Throwable
