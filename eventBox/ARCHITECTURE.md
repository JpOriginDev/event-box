# Event Box -- code structure and implementation notes

Developer-facing guide to the `:eventBox` module. For *using* the library (turning it on, API
cheat-sheet, behaviour as the tester sees it) read the root [`README.md`](../README.md). This file
explains **how it is built and why**, so the code can be reviewed or changed quickly.

---

## 1. Context

Event Box is a **testing-only, on-device event log**. A host app records the analytics events it
fires; while debug mode is on, they appear in a floating bubble / panel drawn over whatever
Activity is in front, so a tester can verify them on the phone instead of in logcat.

Design constraints that drive most decisions:

| Constraint | Consequence |
|---|---|
| No permission may be required | Window is `TYPE_APPLICATION` on the Activity token, not `SYSTEM_ALERT_WINDOW` |
| Must float above dialogs | Own window + re-raise on focus loss (not a view in `content`) |
| Must not steal touches from the app | Window wraps the box; it is never full-screen |
| Free for production users | Off by default; `log()` is a no-op while off; host gates it with `debugModeEnabled` |
| Reusable by anyone | Pure Kotlin, `minSdk 21`, only `core-ktx` + coroutines, own resources, fixed colours |
| Hard to lift the source | Release build is minified/obfuscated; only the public API keeps names |

Module facts: library `com.ogmediaapps.eventbox`, `minSdk 21`, `compileSdk 37`, Java 11,
`kotlinx-coroutines-android` exposed as `api` (StateFlow in the public API).

---

## 2. Source layout

```
eventBox/
  build.gradle.kts        library config, minify on for release, deps
  proguard-rules.pro      rules applied when the library itself is built (obfuscation)
  consumer-rules.pro      packaged into the AAR as proguard.txt (keeps public API only)
  src/main/
    AndroidManifest.xml   empty (no components, no permissions)
    java/com/ogmediaapps/eventbox/
      OGEventBoxLog.kt         PUBLIC Facade: public state + API + backward aliases
      model/
        OGEventBoxDebugEvent.kt PUBLIC Model: log row data class + DebugEvent alias
        OGEventBoxPosition.kt   internal Model: bubble rail and Y ratio
        OGEventBoxUiState.kt    internal Model: presentation state for MVVM
      data/
        OGEventBoxRepository.kt internal Data: in-memory events, state flow, sync
        OGEventBoxStore.kt      internal Data: SharedPreferences persistence
      viewmodel/
        OGEventBoxViewModel.kt  internal ViewModel: presentation logic, badge, timers
      view/
        OGEventBoxView.kt       internal View: custom view UI, drag/snap, close target
        OGEventBoxOverlay.kt    internal View: ActivityLifecycleCallbacks overlay window
    res/
      layout/   og_event_box.xml        <merge>: panel + bubble
                og_event_box_close.xml  drop-to-close target
      drawable/ og_event_box_*.xml      shapes (panel/header/bubble/badge/button/count/close/logo)
                og_event_box_ic_*.xml   vectors (bolt, pin, unpin, minimize, close)
      values/   colors.xml  dimens.xml  strings.xml   (all prefixed og_event_box_)
```

Everything is `internal` except `OGEventBoxLog` and `OGEventBoxDebugEvent`.

---

## 3. Components

### 3.1 `OGEventBoxLog` (public facade `object`)

- Styling setters: `setBubbleIconResId()`, `setBubbleIconScaleType()`, `setBubbleBackgroundColor()`, `setBubbleBorderColor()`, `setBubbleBorderWidthDp()`, `setBubbleCornerRadiusDp()` (all fluent returning `OGEventBoxLog`).
- Configuration & install: `setDebugModeEnabled()`, `setDebugModeDurationHours()`, `install()` (all fluent returning `OGEventBoxLog`, annotated with `@JvmStatic` for Kotlin/Java interop).
- Public functions: `setEnabled()`, `updateDebugModeDurationHours()`, `remainingMinutes()`, `log()`, `clear()`, `disable()`.
- Public StateFlows: `events` (capped at `MAX_EVENTS = 200`), `isActive`, `isActiveFlow`.

### 3.2 `OGEventBoxDebugEvent` (public `data class`)

`data class OGEventBoxDebugEvent(index: Int, timestampMillis: Long, name: String)`. `index` is 1-based and restarts after `clear()`.

### 3.3 `OGEventBoxRepository` & `OGEventBoxStore` (data layer)

- `OGEventBoxRepository`: Thread-safe in-memory store for `OGEventBoxDebugEvent` list, `_isActive` flow, and timer handlers.
- `OGEventBoxStore`: `SharedPreferences` persistence (`og_event_box_prefs`) storing session expiry epoch and resting bubble position.

### 3.4 `OGEventBoxViewModel` (viewmodel layer)

Coordinates UI presentation logic:
- Tracks `isCollapsed`, `isPinned`, `anchorX`, `anchorY`, and `lastSeenIndex`.
- Exposes `uiState: StateFlow<OGEventBoxUiState>`.
- Calculates unread badge count against `lastSeenIndex`.

### 3.5 `OGEventBoxOverlay` & `OGEventBoxView` (view layer)

- `OGEventBoxOverlay`: `Application.ActivityLifecycleCallbacks` that places an `OGEventBoxView` inside a `TYPE_APPLICATION` window above the resumed Activity. Re-raises the window when focus is lost to float above dialogs.
- `OGEventBoxView`: Custom `FrameLayout` inflating `og_event_box.xml` with drag, rail snapping, auto-shrink, and drop-to-close (`og_event_box_close.xml`).
| close target | reveal/hide with hysteresis, grow near/over, distance maths, separate window |
| position | screen size/insets from host decor view, clamp, snap to rail, restore/persist |

Key mechanisms

1. **Own window.** `windowParams()` -> `WRAP_CONTENT`, `TYPE_APPLICATION`, flags
   `NOT_FOCUSABLE | NOT_TOUCH_MODAL | LAYOUT_IN_SCREEN | HARDWARE_ACCELERATED`,
   `gravity TOP|START`, `token = activity.window.attributes.token`.
2. **Position = window x/y.** `moveTo` mutates `LayoutParams.x/y` and `updateViewLayout`;
   in fallback mode it sets `translationX/Y`. `onLayout` re-applies the anchor unless a drag is in
   progress (`dragInProgress`).
3. **Settling** uses a `ValueAnimator` (`animateTo`) because a window cannot be moved by a view
   animator.
4. **Snapping.** `snapPosition` = `clampPosition` (inside system-bar insets), and for the bubble
   x is forced to the nearer rail. The panel is only clamped.
5. **Restoring.** `restorePosition` writes anchor from `DebugModeStore` or the default
   (`DEFAULT_ON_RIGHT_EDGE = true`, `DEFAULT_Y_RATIO = 0`). It passes an off-screen x so that
   `snapPosition` is the only place that decides the edge.
6. **Persisting.** On drop (when collapsed) and on collapse: `persistBubblePosition`; skipped when
   `!NextGenEventLog.isActive` so a closed session cannot re-write the cleared store.
7. **Close target.** Separate `FLAG_NOT_TOUCHABLE` window placed by computed coordinates. Visible
   only while a drag is within `revealRadius()`; hidden at `radius * CLOSE_TARGET_HIDE_SLACK`.
   Scale 1 -> `NEAR` (<= 1.8 widths) -> `OVER` (<= 1 width). Drop within `OVER` radius calls
   `NextGenEventLog.disable()`. Distance is bubble-centre when collapsed, finger when expanded.
8. **Styling.** `styleBubble()` builds the disc (background) and border (foreground drawable, so
   a full-bleed logo can't cover it). `applyLogoTo()` sets a `ViewOutlineProvider` with the
   bubble's corner radius and `clipToOutline`; inset logic keeps a ring of disc for fitted logos.
9. **Coroutines.** On attach a `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`
   collects `NextGenEventLog.events`; cancelled on detach.
10. **Stray cancel guard.** `DragTouchListener.gestureActive` ignores the `ACTION_CANCEL` that
    arrives after a drop-to-close removed the view.

Timers / constants (companion object): `AUTO_SHRINK_DELAY_MS 6000`, `BUBBLE_HIGHLIGHT_MS 2500`,
`BUBBLE_IDLE_ALPHA 0.55`, `BUBBLE_DRAG_SCALE 1.15`, `ANIMATION_MS 150`, `DRAG_ANIMATION_MS 120`,
`SNAP_ANIMATION_MS 240`, `BUBBLE_FADE_MS 300`, badge sizes 16/18/20 dp, `LOGO_INSET_DP 3`,
`CLOSE_TARGET_REVEAL_HEIGHT 0.22`.

---

## 4. Lifecycle walk-through

```
Application.onCreate
  OGEventBoxLog.install(ctx, enabled, hours)
    -> EventBoxOverlay.install(app)                    (lifecycle callbacks)
    -> stored, unexpired, host-enabled session?  yes -> activate(expiry)

Host gesture
  NextGenEventLog.setEnabled(ctx, true)
    -> persist expiry -> activate -> isActive=true, expiry timer, EventBoxOverlay.sync()
    -> attach(): new EventBoxView in its own window (collapsed bubble)

During session
  log("x") -> StateFlow emits -> EventBoxView.renderEvents (rows / badge)
  Activity change: pause -> detach(); resume -> attach(); state is in EventBoxOverlay
  Dialog opens: focus lost -> 150 ms -> remove + re-add window (back on top)
  Drag/drop: settle -> anchor + DebugModeStore.setBubblePosition

End of session (any of)
  close target drop | expiry runnable | setEnabled(false) | install() sees host disabled
    -> disable(): clear store, reset anchor, isActive=false, clear log, detach box
```

---

## 5. Build, shrink and obfuscation

- `eventBox/build.gradle.kts`: `release { isMinifyEnabled = true; proguardFiles(optimize,
  proguard-rules.pro) }`, `consumerProguardFiles("consumer-rules.pro")`.
  `isShrinkResources` is not available for libraries -- the host app does it.
- `proguard-rules.pro`: keeps `NextGenEventLog` + `DebugEvent` public members; everything else
  `-repackageclasses 'com.ogmediaapps.eventbox.a'`, `-allowaccessmodification`,
  `-renamesourcefileattribute ''`, strips Kotlin intrinsics null checks and `Log.d/v`.
- `consumer-rules.pro`: same two keep rules only (public API), shipped as `proguard.txt` in the AAR.
- Verified artefacts: `build/outputs/aar/eventBox-release.aar` (classes renamed to `a`, `a/a`,
  ...) and `build/outputs/mapping/release/mapping.txt`.
- If you add a **public** class, add it to *both* rules files. If you add reflection or XML-referenced
  classes (none today), add a keep rule for them.

---

## 6. Where to change things

| I want to... | Edit |
|---|---|
| Change auto-shrink / fade timings, animation speeds | constants in `EventBoxView` companion |
| Change default parking corner | `DEFAULT_ON_RIGHT_EDGE`, `DEFAULT_Y_RATIO` in `EventBoxView` |
| Change max log length | `NextGenEventLog.MAX_EVENTS` |
| Change colours / sizes / strings | `res/values/*.xml` (all `nextgen_event_box_*`) |
| Change panel or bubble structure | `res/layout/nextgen_event_box.xml` (then the `findViewById`s in `EventBoxView.init`) |
| Add a new styling option | property in `NextGenEventLog` + read in `EventBoxView.styleBubble/applyLogoTo` |
| Persist something new | key + accessors in `DebugModeStore`; remember `clear()` |
| Change session end behaviour | `NextGenEventLog.disable()` |

---

## 7. Known limits and gotchas

- A dialog covers the box ~150 ms before it is lifted back; IME and shade also trigger a re-add,
  which restarts the auto-shrink timer.
- The window is removed on Activity pause, re-added on resume (state lives in `EventBoxOverlay`).
- A ~12 dp margin around the bubble belongs to its window and does not pass touches through.
- Content-view fallback mode cannot float above dialogs.
- `install()` needs an `Application` context to register lifecycle callbacks.
- No automated tests yet besides the template examples; behaviour is verified manually
  (enable, drag to both edges, rotate, open a dialog, drop on close target, swipe from Recents).
- Public API is intentionally small; `DebugEvent` fields and `NextGenEventLog` members are the
  compatibility surface -- everything else may change freely.
