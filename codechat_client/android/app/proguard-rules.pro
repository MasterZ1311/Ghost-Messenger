# Flutter ProGuard Rules
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }
-dontwarn com.google.android.play.core.**
-dontwarn io.flutter.embedding.engine.deferredcomponents.**

# WebRTC (flutter_webrtc)
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# SQLCipher (sqflite_sqlcipher)
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# Flutter Secure Storage / AndroidX Crypto
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
