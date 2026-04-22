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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# SLF4J
-dontwarn org.slf4j.**

# BND annotations
-dontwarn aQute.bnd.annotation.**

# XML / StAX
-dontwarn org.codehaus.stax2.**
-dontwarn javax.xml.stream.**

# Jackson / transcript parsing keeps generic type info required by TypeReference.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keep class com.fasterxml.jackson.core.type.TypeReference { *; }
-keep class io.github.thoroldvix.api.TranscriptContent { *; }
-keep class io.github.thoroldvix.api.TranscriptContent$Fragment { *; }
-keep class io.github.thoroldvix.api.TranscriptContentExtractor { *; }
-keep class io.github.thoroldvix.api.TranscriptContentExtractor$1 { *; }

# ONNX Runtime JNI bridge relies on exact Java class names/signatures.
# Keep runtime and extensions APIs from being obfuscated/stripped in release.
-keep class ai.onnxruntime.** { *; }
-keep class ai.onnxruntime.extensions.** { *; }
