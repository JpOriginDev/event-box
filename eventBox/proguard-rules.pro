# Rules used when the library itself is built (release).
# Goal: only the public API survives by name; internals are renamed, merged and stripped.

# ---- Public API (must match consumer-rules.pro) ----
-keep public class com.ogmediaapps.eventbox.OGEventBoxLog {
    public *;
}
-keep public class com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent {
    public *;
}
-keep public class com.ogmediaapps.eventbox.validator.OGEventBoxValidator {
    public *;
}

# ---- Hide the rest ----
# Move every remaining class into one package and let R8 rename members aggressively.
-repackageclasses 'com.ogmediaapps.eventbox.a'
-allowaccessmodification
-overloadaggressively

# No file names or line numbers in stack traces of the library's internals.
-renamesourcefileattribute ''
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Kotlin null-check intrinsics only leak parameter names; drop them.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkParameterIsNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
}

# Debug logging is not needed in release builds of the library.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

-keep public enum com.ogmediaapps.eventbox.model.OGEventBoxBackgroundMode {
    *;
}

-keep public class com.ogmediaapps.eventbox.OGEventBoxAnalytics {
    public *;
}
-keep public interface com.ogmediaapps.eventbox.OGEventBoxAnalytics$EventSink {
    *;
}
-keep public interface com.ogmediaapps.eventbox.OGEventBoxAnalytics$UserPropertySink {
    *;
}
