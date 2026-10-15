# Event Box

A floating on-screen log for Android testers. A small bubble sits on top of your app; tap it to
open a panel that shows your analytics events as they fire, so you don't have to watch logcat.

- For testing only: it can be switched off completely and records nothing while off.
- No permissions, no manifest entries.
- `minSdk 21`, pure Kotlin, works from Java too.

## 1. Add the dependency

In `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.JpOriginDev:event-box:1.0.0")
}
```

## 2. Set it up

Call this once in your `Application.onCreate()`:

**Kotlin**
```kotlin
OGEventBoxLog.setDebugModeEnabled(BuildConfig.DEBUG)
    .setDebugModeDurationHours(0.5)
    .install(this)
```

**Java**
```java
OGEventBoxLog.setDebugModeEnabled(BuildConfig.DEBUG)
    .setDebugModeDurationHours(0.5)
    .install(this);
```

- `setDebugModeEnabled` is your kill switch. When `false`, the box can never appear.
- `setDebugModeDurationHours` is how long a session lasts (here, 30 minutes).

## 3. Turn it on

Setup alone shows nothing. Your app must also switch it on, for example from a hidden gesture
(five taps on a title) or a debug menu:

```kotlin
OGEventBoxLog.setEnabled(context, true)
```

The session then runs for the duration you set, and turns itself off when time is up. The bubble
comes back if the app is closed and reopened during the session.

## 4. Log your events

```kotlin
OGEventBoxLog.log("user_signed_in")
```

Java: `OGEventBoxLog.log("user_signed_in");`

Log each event once. Calls are ignored while the box is off.

## Using the box

- **Bubble**: tap to open the panel, drag to move it. It snaps to the left or right edge and fades
  when idle. The badge shows how many events arrived since you last opened the panel.
- **Panel**: shows `number  time  event_name`, newest at the bottom. It shrinks back to the bubble
  after 6 seconds without touch.
    - **Pin**: stops it from auto-shrinking.
    - **Minimize** (or tap the logo): shrinks it now.
    - **CLEAR**: empties the log.
- **Close**: drag the box toward the bottom of the screen and drop it on the close target to end
  the session.

## Customize the bubble (optional)

Call these before `install()`:

```kotlin
OGEventBoxLog.setBubbleIconResId(R.mipmap.my_logo)
    .setBubbleIconScaleType(ImageView.ScaleType.CENTER_CROP)
    .setBubbleBackgroundColor(Color.parseColor("#0D9379"))
    .setBubbleBorderColor(Color.parseColor("#CCE6E1"))
    .setBubbleBorderWidthDp(1.5f)
    .setBubbleCornerRadiusDp(-1f) // -1 = fully round
    .setDebugModeEnabled(BuildConfig.DEBUG)
    .setDebugModeDurationHours(0.5)
    .install(this)
```

## Other controls

| Call | What it does |
|---|---|
| `OGEventBoxLog.disable()` | Ends the session now |
| `OGEventBoxLog.updateDebugModeDurationHours(context, 1.0)` | Changes the session length |
| `OGEventBoxLog.isActive` | Is the box on? (`Boolean`) |
| `OGEventBoxLog.isActiveFlow` | Same, as a `StateFlow<Boolean>` |
| `OGEventBoxLog.remainingMinutes(context)` | Minutes left (0 when off) |
| `OGEventBoxLog.events` | The log as `StateFlow<List<OGEventBoxDebugEvent>>` (max 200) |
| `OGEventBoxLog.clear()` | Empties the log |

## Release builds

Nothing extra is needed. The library ships its own ProGuard/R8 rules and keeps only the public API
(`OGEventBoxLog`, `OGEventBoxDebugEvent`).

## Known limits

- A dialog opened later briefly covers the box (about 150 ms) before it lifts back on top.
- The box re-adds itself when the notification shade or keyboard takes focus, which restarts the
  auto-shrink timer.

## More

How the code is organized: [`eventBox/ARCHITECTURE.md`](eventBox/ARCHITECTURE.md)
