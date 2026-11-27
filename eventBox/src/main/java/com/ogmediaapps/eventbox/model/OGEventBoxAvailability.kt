package com.ogmediaapps.eventbox.model

/**
 * In which kinds of builds of the host app Event Box may appear. Set it with
 * `OGEventBoxLog.setAvailability` before `install`.
 *
 * The build kind is read from the host app itself (its `debuggable` flag), so the host does not
 * have to pass `BuildConfig.DEBUG` around. With [NEVER] the box can never appear and `log()`
 * stays a no-op.
 */
enum class OGEventBoxAvailability {
    /** Only in debug builds (the app is debuggable). The usual choice. */
    DEBUG_ONLY,

    /** Only in release builds. Useful to test a minified release build. */
    RELEASE_ONLY,

    /** In both debug and release builds. Testers still need to switch it on in the app. */
    DEBUG_AND_RELEASE,

    /** In no build: Event Box is switched off. */
    NEVER;

    internal fun allows(isDebugBuild: Boolean): Boolean = when (this) {
        DEBUG_ONLY -> isDebugBuild
        RELEASE_ONLY -> !isDebugBuild
        DEBUG_AND_RELEASE -> true
        NEVER -> false
    }
}
