# Applied automatically to every app that depends on Event Box.
# Only the public API is kept by name; everything else may be obfuscated and removed by the
# consuming app's R8.
-keep public class com.ogmediaapps.eventbox.OGEventBoxLog {
    public *;
}
-keep public class com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent {
    public *;
}
-keep public class com.ogmediaapps.eventbox.validator.OGEventBoxValidator {
    public *;
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
