# Salvia Offline AI — R8 rules.
#
# The native layer looks up classes and methods BY NAME through JNI, so the
# exact spellings below must survive shrinking/obfuscation.

# JNI entry points (static-style methods on Kotlin objects).
-keep class com.example.core.llm.NativeLlama { *; }
-keep class com.example.core.asr.NativeWhisper { *; }

# Anything with native methods keeps its name and its native members.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# JNI upcalls use env->GetObjectClass + GetMethodID("onToken"/"onLoadProgress")
# on callback objects — those interface method names must not be renamed.
-keep interface com.example.core.llm.TokenListener { *; }
-keep interface com.example.core.llm.LoadListener { *; }
-keepclassmembers class * implements com.example.core.llm.TokenListener {
    void onToken(java.lang.String);
}
-keepclassmembers class * implements com.example.core.llm.LoadListener {
    void onLoadProgress(float);
}

# Readable crash reports without shipping source file paths.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
