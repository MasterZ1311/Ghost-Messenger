# ============================================================
# Ghost Messenger ProGuard Rules
# ============================================================

# --- libsignal-android ---
-keep class org.signal.** { *; }
-keep class org.whispersystems.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.signal.**
-dontwarn org.whispersystems.**

# --- SQLCipher ---
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# --- Socket.IO / OkHttp / Engine.IO ---
-keep class io.socket.** { *; }
-keep class io.engineio.** { *; }
-dontwarn io.socket.**
-dontwarn io.engineio.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# --- WebRTC ---
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class org.ghostmessenger.**$$serializer { *; }
-keepclassmembers class org.ghostmessenger.** {
    *** Companion;
}
-keepclasseswithmembers class org.ghostmessenger.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Ktor ---
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# --- BIP39 ---
-keep class cash.z.ecc.android.bip39.** { *; }
-dontwarn cash.z.ecc.android.bip39.**

# --- Hilt ---
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# --- Tink / Security Crypto ---
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**

# --- General ---
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
