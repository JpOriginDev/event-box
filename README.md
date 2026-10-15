# Event Box

The on-device counterpart of the Firebase Analytics events an app sends: a floating bubble that
opens into a log panel, so a tester can watch events fire on the phone instead of in logcat. It is
testing-only, switchable off from the host, and records nothing while it is off.

This repository is the Event Box extracted into a standalone Android library module:

| Module | What it is |
|---|---|
| `:eventBox` | the library -- package `com.ogmediaapps.eventbox`, plus its own resources |
| `:app` | sample host app (`com.ogmediaapps.eventbox.sample`); depends on `:eventBox`, currently an empty `MainActivity` (does not yet call `install()`) |

Everything in `:eventBox` lives in one package and its own resources, so it can be lifted into
another project whole (see [Reusing it elsewhere](#reusing-it-elsewhere)).

> **Status:** self-contained -- no NextGen Ads SDK, no Remote Config, no AppCompat/Material.
> `minSdk 21`, Java 11, pure Kotlin. Dependencies: `androidx.core:core-ktx` and
> `kotlinx-coroutines-android` (exposed as `api`, because `StateFlow` is part of the public API).

---

## Turning it on

Two things must both be true. Either one alone does nothing.

1. **Host configuration** -- the app configures and installs the log in `Application.onCreate()`:

   **Kotlin:**
   ```kotlin
   // Application.onCreate()
   val isDebugModeEnabled = BuildConfig.DEBUG

   OGEventBoxLog.setDebugModeEnabled(isDebugModeEnabled)
       .setDebugModeDurationHours(0.5)
       .install(context = this)
   ```

   **Java:**
   ```java
   // Application.onCreate()
   boolean isDebugModeEnabled = BuildConfig.DEBUG;

   OGEventBoxLog.setDebugModeEnabled(isDebugModeEnabled)
       .setDebugModeDurationHours(0.5)
       .install(this);
   ```

   `debugModeEnabled` is the host's own kill switch (e.g. `BuildConfig.DEBUG`): when `false` the
   box can never be shown and any stored session is dropped.
2. **A deliberate gesture in the host app** calling `OGEventBoxLog.setEnabled(context, true)`
   (e.g. five quick taps on a toolbar title, a debug menu, a build-flavour check).

Once on it runs for `debugModeDurationHours` of wall-clock time. The expiry is persisted, so
closing and reopening the app brings the box straight back, and it switches itself off on time
whether the app was running or not.

## Public API

The main public entry point is `OGEventBoxLog` and model `OGEventBoxDebugEvent`.

### Kotlin Usage

```kotlin
// 1. Bubble styling via fluent setters
OGEventBoxLog.setBubbleIconResId(R.mipmap.origin_event_box_logo)
    .setBubbleIconScaleType(ImageView.ScaleType.CENTER_CROP)
    .setBubbleBackgroundColor(Color.parseColor("#0D9379"))
    .setBubbleBorderColor(Color.parseColor("#CCE6E1"))
    .setBubbleBorderWidthDp(1.5f)
    .setBubbleCornerRadiusDp(-1f) // Fully round bubble

// 2. Install EventBox
val isDebugModeEnabled = BuildConfig.DEBUG

OGEventBoxLog.setDebugModeEnabled(isDebugModeEnabled)
    .setDebugModeDurationHours(0.5)
    .install(context = this)

// Or chain styling and install together in a single call:
OGEventBoxLog.setBubbleIconResId(R.mipmap.origin_event_box_logo)
    .setBubbleIconScaleType(ImageView.ScaleType.CENTER_CROP)
    .setBubbleBackgroundColor(Color.parseColor("#0D9379"))
    .setBubbleBorderColor(Color.parseColor("#CCE6E1"))
    .setBubbleBorderWidthDp(1.5f)
    .setBubbleCornerRadiusDp(-1f)
    .setDebugModeEnabled(BuildConfig.DEBUG)
    .setDebugModeDurationHours(0.5)
    .install(context = this)

// Switching & Duration
OGEventBoxLog.setEnabled(context, true)       // false unless install() allowed it
OGEventBoxLog.updateDebugModeDurationHours(context, 1.0) // updates duration and active timer
OGEventBoxLog.disable()                       // ends the session now
OGEventBoxLog.isActive                        // Boolean
OGEventBoxLog.isActiveFlow                    // StateFlow<Boolean>
OGEventBoxLog.remainingMinutes(context)       // 0 when off

// Recording (no-op while off; blank names ignored)
OGEventBoxLog.log("permission_overlay_granted")

// The log itself, if you would rather render it
OGEventBoxLog.events              // StateFlow<List<OGEventBoxDebugEvent>>, oldest first, capped at MAX_EVENTS (200)
OGEventBoxLog.clear()             // empties the log, numbering restarts at 1

// Backward-compatible property assignments are also supported:
OGEventBoxLog.bubbleIconResId = R.mipmap.origin_event_box_logo
OGEventBoxLog.install(this, debugModeEnabled = true, debugModeDurationHours = 0.5)
```

### Java Usage

All methods in `OGEventBoxLog` are annotated with `@JvmStatic`, providing standard, idiomatic Java syntax:

```java
// 1. Bubble styling via fluent setters
OGEventBoxLog.setBubbleIconResId(R.mipmap.origin_event_box_logo)
    .setBubbleIconScaleType(ImageView.ScaleType.CENTER_CROP)
    .setBubbleBackgroundColor(Color.parseColor("#0D9379"))
    .setBubbleBorderColor(Color.parseColor("#CCE6E1"))
    .setBubbleBorderWidthDp(1.5f)
    .setBubbleCornerRadiusDp(-1f);

// 2. Install EventBox
boolean isDebugModeEnabled = BuildConfig.DEBUG;

OGEventBoxLog.setDebugModeEnabled(isDebugModeEnabled)
    .setDebugModeDurationHours(0.5)
    .install(this);

// Or chain styling and installation together:
OGEventBoxLog.setBubbleIconResId(R.mipmap.origin_event_box_logo)
    .setBubbleIconScaleType(ImageView.ScaleType.CENTER_CROP)
    .setBubbleBackgroundColor(Color.parseColor("#0D9379"))
    .setBubbleBorderColor(Color.parseColor("#CCE6E1"))
    .setBubbleBorderWidthDp(1.5f)
    .setBubbleCornerRadiusDp(-1f)
    .setDebugModeEnabled(BuildConfig.DEBUG)
    .setDebugModeDurationHours(0.5)
    .install(this);

// Switching, logging & controls
OGEventBoxLog.setEnabled(context, true);
OGEventBoxLog.updateDebugModeDurationHours(context, 1.0);
OGEventBoxLog.log("user_signed_in");
OGEventBoxLog.clear();
OGEventBoxLog.disable();
int remaining = OGEventBoxLog.remainingMinutes(context);
boolean active = OGEventBoxLog.isActive();
```

`OGEventBoxDebugEvent(index, timestampMillis, name)` -- `index` is 1-based and restarts after `clear()`.

Log each moment **once** -- duplicate or missing events are exactly what the box exists to catch.

## Behaviour

### The bubble

- A debug-mode session **always starts shrunk**: only the bubble shows, the panel never opens by
  itself. Tap the bubble to open the panel.
- It lives on the **left or right edge**. Drag it anywhere; on release it glides to whichever side
  its centre is nearer, keeping the height it was dropped at.
- It is **solid while you are using it** and fades to `BUBBLE_IDLE_ALPHA` (0.55) about 2.5s
  (`BUBBLE_HIGHLIGHT_MS`) after the last touch, so it doesn't cover the app. The unread badge fades
  with it.
- The badge counts events fired since the panel was last open (`99+` cap); it clears when you open
  the panel.
- The icon is clipped to the bubble's own shape, so it stays perfectly round -- or as rounded as
  `bubbleCornerRadiusDp` asks -- whatever drawable it is given. The panel header's mark wears the
  same logo and disc colour.
- The border is drawn in the icon's *foreground* (a host logo that fills the bubble would paint
  over a background stroke). Radius is capped at half the bubble, so anything larger is a circle.
  A fitted logo is inset past the border (`LOGO_INSET_DP`) so the disc shows as a ring;
  `CENTER_CROP` / `FIT_XY` fill to the rim with no inset.

### The panel

- Opens where the bubble was, full width bar the margins, and is dragged by its **header**.
- **Auto-shrinks** back to the bubble after 6s (`AUTO_SHRINK_DELAY_MS`) of no interaction. Touching
  it anywhere -- including scrolling the list -- restarts that countdown.
- **Pin** (maximize) suspends auto-shrink until pressed again; **minimize** -- or tapping the logo
  in the header -- shrinks it at once; **CLEAR** empties the log and restarts numbering at 1.
- Rows are `NN  HH:MM:SS  event_name`, newest at the bottom, bold and highlighted.

### The close target

- Appears only when a drag comes **near** it (within `revealRadius()` -- the screen's half width
  by `CLOSE_TARGET_REVEAL_HEIGHT` of its height), is hidden again a little further out
  (`CLOSE_TARGET_HIDE_SLACK`) so the boundary never flickers.
- **Grows** in two steps (`NEAR` / `OVER`) as the box closes in, so it is easy to aim at.
- Dropping the box on it calls `NextGenEventLog.disable()`: debug mode ends immediately.
- Distance is measured from the bubble's centre when collapsed, from the finger when the panel is
  open (the panel is clamped on screen and can never reach the bottom edge).

### Where the bubble sits, between sessions

| Event | Position |
|---|---|
| Dragged and dropped | saved at once (rail + height as a fraction of the screen) |
| Panel collapsed after being dragged | the rail it lands on is saved |
| App closed, swiped from Recents, reopened | restored exactly where it was |
| Rotation / different screen | the fraction still means the same place |
| Session ends (close target, expiry, disabled) | forgotten -- the next session starts top-right |

## Architecture

Full code walk-through, lifecycle, constants and a "where to change things" table:
[`eventBox/ARCHITECTURE.md`](eventBox/ARCHITECTURE.md).

All source code is organized into an MVVM architecture under `eventBox/src/main/java/com/ogmediaapps/eventbox/`:

| Layer | Class | Role |
|---|---|---|
| **Facade** | `OGEventBoxLog` | Public entry point; manages styling, install, enable/disable, and public StateFlows |
| **Model** | `OGEventBoxDebugEvent`, `OGEventBoxPosition`, `OGEventBoxUiState` | Data models and reactive UI state |
| **Data** | `OGEventBoxRepository`, `OGEventBoxStore` | In-memory log list and `SharedPreferences` persistence (`og_event_box_prefs`) |
| **ViewModel** | `OGEventBoxViewModel` | Coordinates presentation logic, unread count badge, auto-shrink, and pin state |
| **View** | `OGEventBoxView`, `OGEventBoxOverlay` | Custom overlay view, drag/snap gestures, and `ActivityLifecycleCallbacks` overlay window |
| **Util** | `OGEventBoxMainThread` | `runOnMainThread` / `postToMainThread` execution helpers |

Resources (`eventBox/src/main/res/`):
- Layouts: `og_event_box`, `og_event_box_close`
- Drawables: `og_event_box_*` (badge, bubble, button, close, count, header, logo, panel backgrounds) and `og_event_box_ic_*` (bolt, close, minimize, pin, unpin)
- Values: `colors`, `dimens`, `strings` (all prefixed with `og_event_box_`)

### Why it has its own window

The box is added through `WindowManager` as a `TYPE_APPLICATION` window on the Activity's token --
the same kind of window a `Dialog` is -- **not** as a view in `android.R.id.content`.

- It needs **no permission**: no `SYSTEM_ALERT_WINDOW`, because the window belongs to the Activity.
- It is the only way to float **above dialogs**. Z-order between windows belongs to the window
  manager; a view in the content view is always under every dialog of that Activity.
- Among sibling app windows the newest is on top, so a dialog opened later still covers the box.
  `EventBoxOverlay` watches the Activity window's focus and, when it is lost, removes and re-adds
  the box (`raiseAboveNewWindows`) after `RAISE_DELAY_MS` (150ms). That brief dip is expected.
- The window **wraps the box**, never the screen. A full-screen window swallows every touch, even
  non-modal. A small one lets everything around it through.
- If `addView` is ever refused, `attach()` falls back to a view in the content view; everything
  still works except floating above dialogs.

Consequences worth knowing when editing this code:

- Position is the **window's** `x`/`y` in screen coordinates, not a translation inside a parent.
  `settle()` therefore drives a `ValueAnimator` (`animateTo`) -- a window can't be moved by a view
  animator.
- Screen size and system-bar insets are read from the **host Activity's decor view**: the box's own
  window is too small to report them.
- The layout margins are the room the scale animations need. A window sized tight to its content
  clips them -- which is why the close target's window is the circle **plus** slack on every side.
- The close target gets its own `FLAG_NOT_TOUCHABLE` window, placed by computed screen coordinates
  so it lands exactly where the proximity and drop maths believe it is.
- Dropping on the close target removes the view mid-gesture; `DragTouchListener.gestureActive`
  swallows the resulting stray `ACTION_CANCEL` so it can't re-write the just-cleared position.

## Adding it to another project

1. Copy the `:eventBox` module (or publish its AAR) and `include(":eventBox")`.
2. `implementation(project(":eventBox"))`. Requirements: `minSdk 21`, Java 11, Kotlin.
3. Call `OGEventBoxLog.install(...)` in `Application.onCreate()` and `setEnabled(...)` from your
   own gesture (see *Turning it on*). No manifest entries or permissions are needed.
4. Release builds of your app need nothing extra: the AAR carries its own consumer rules.

## Reusing it elsewhere

Copy the package and its resources:

```
java/com/ogmediaapps/eventbox/               MVVM architecture packages and facade
res/layout/og_event_box*.xml                 panel + bubble, drop-to-close target
res/drawable/og_event_box_*.xml              backgrounds and icons
res/values/                                  colors, dimens, strings (all og_event_box_*)
```

Dependencies: `androidx.core:core-ktx` + `kotlinx-coroutines-android` (see `eventBox/build.gradle.kts`). The box deliberately uses **fixed
colours**, not theme attributes -- it floats over any host theme and must stay readable.

`OGEventBoxOverlay` registers itself from `OGEventBoxLog.install()`, which requires an
`Application` context.

## Shrinking and obfuscation

- `eventBox/proguard-rules.pro` is applied when the library is built in release
  (`isMinifyEnabled = true`): only `OGEventBoxLog` and `OGEventBoxDebugEvent` keep their names; every other
  class is renamed into the `com.ogmediaapps.eventbox.a` package, members are renamed, source file
  and line numbers are dropped, and the Kotlin null-check intrinsics and `Log.d/v` calls are
  removed. The AAR therefore ships obfuscated internals.
- `eventBox/consumer-rules.pro` is packaged into the AAR as `proguard.txt` and merged into every
  consuming app's R8 run. It only keeps the public API, so the app's own R8 can keep shrinking and
  renaming the rest.
- Resource shrinking (`isShrinkResources`) is not available for libraries -- it runs in the host
  app. The sample app enables both `isMinifyEnabled` and `isShrinkResources` for release. Resources
  are referenced from code only through `R`, so nothing needs a keep rule.
- The library's `mapping.txt` is at `eventBox/build/outputs/mapping/release/`; keep it if you need
  to de-obfuscate a stack trace.

## Testing

No automated tests yet beyond the template `ExampleUnitTest` / `ExampleInstrumentedTest`. Exercise
it manually: enable, drag to both edges, rotate, open a dialog, drop on the close target, kill the
app from Recents and reopen.

Watch `adb logcat -s EventBox` alongside (debug builds of the library only -- release builds strip the `Log.d` calls) for the `EventBox` tag: `Event log enabled for Nh` / `Event log disabled`.

## Limits

- A dialog covers the box for ~150ms before it lifts back over it.
- Window focus is also lost to the notification shade and the IME, so the box re-adds itself more
  often than just for dialogs. Harmless, but it restarts the auto-shrink countdown.
- The box is removed when the Activity pauses and re-added on the next resume; state lives in
  `EventBoxOverlay`, so nothing visible is lost.
- A ~12dp ring around the bubble belongs to the box's window and will not pass taps through.
