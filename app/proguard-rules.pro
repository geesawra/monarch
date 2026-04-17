# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line number information for deobfuscated stack traces (Crashlytics, Play Vitals).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-dontwarn io.ktor.**
-keep class androidx.media3.** { *; }
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn libcore.icu.NativePluralRules

# Keep ozone response types so AtpException.message renders readable status-code
# names (e.g. "AuthenticationRequired") in release builds instead of minified
# gibberish, and so type-based classification of auth errors survives R8.
-keep public class sh.christian.ozone.api.response.StatusCode { *; }
-keep public class sh.christian.ozone.api.response.StatusCode$* { *; }
-keep class sh.christian.ozone.api.response.AtpException { *; }
-keep class sh.christian.ozone.api.response.AtpErrorDescription { *; }
-keepnames class sh.christian.ozone.api.response.AtpException
-keep class sh.christian.ozone.api.response.**$$serializer { *; }

# OAuthToken is round-tripped through JSON in DataStore; field renames would
# silently produce malformed persisted credentials.
-keep class sh.christian.ozone.oauth.OAuthToken { *; }
-keep class sh.christian.ozone.oauth.OAuthToken$* { *; }
-keep class sh.christian.ozone.oauth.DpopKeyPair { *; }
-keep class sh.christian.ozone.oauth.OAuthScope { *; }
-keep class sh.christian.ozone.oauth.**$$serializer { *; }

-keep class sh.christian.ozone.api.BlueskyAuthPlugin { *; }
-keep class sh.christian.ozone.api.BlueskyAuthPlugin$Tokens { *; }
-keep class sh.christian.ozone.api.BlueskyAuthPlugin$Tokens$* { *; }