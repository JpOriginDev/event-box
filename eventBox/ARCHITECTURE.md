# Event Box -- code structure and implementation notes

Developer-facing guide to the `:eventBox` module. For *using* the library (turning it on, API
cheat-sheet, behaviour as the tester sees it) read the root [`README.md`](../README.md). This file
explains **how it is built and why**, so the code can be reviewed or changed quickly.

---

## 1. Context

Event Box is a **testing-only, on-device event log**. A host app records the analytics events it
fires; while debug mode is on, they appear in a floating bubble / panel drawn over whatever
Activity is in front, so a tester can verify them on the phone instead of in logcat. Each event is
also checked against Firebase Analytics limits and flagged with a warning when it breaks one.

Design constraints that drive most decisions:

| Constraint | Consequence |
|---|---|
| No permission may be required | Window is `TYPE_APPLICATION` on the Activity token, not `SYSTEM_ALERT_WINDOW` |
| Must float above dialogs | Own window + re-raise on focus loss (not a view in `content`) |
| Must not steal touches from the app | Window wraps the box; it is never full-screen while collapsed |
| Free for production users | Off by default; `log()` is a no-op while off; host gates it with `debugModeEnabled` |
| Must never crash the host | Window ops, prefs access and event recording are wrapped in `runCatching` |
| Reusable by anyone | Pure Kotlin, `minSdk 21`, only `core-ktx` + coroutines, own resources, fixed colours |
| Hard to lift the source | Not guaranteed: AGP ignores `isMinifyEnabled` for the published AAR, so obfuscation comes from the consuming app's R8, which honours the keep rules |

Module facts: library `com.ogmediaapps.eventbox`, `minSdk 21`, `compileSdk 36`, Java 11,
`kotlinx-coroutines-android` exposed as `api` (StateFlow in the public API), `androidx.recyclerview`
for the event list. Published to JitPack as `com.github.JpOriginDev:event-box` (version comes from
the git tag via `VERSION`).

---

## 2. Source layout

```
eventBox/
  build.gradle.kts        library config, minify on for release, deps (core-ktx, coroutines,
                          recyclerview), maven-publish
  proguard-rules.pro      rules applied when the library itself is built (obfuscation)
  consumer-rules.pro      packaged into the AAR as proguard.txt (keeps public API only)
  src/main/
    AndroidManifest.xml   one <provider> (share FileProvider), no permissions
    java/com/ogmediaapps/eventbox/
      OGEventBoxLog.kt          PUBLIC facade: fluent config, session control, logging API
      OGEventBoxAnalytics.kt    PUBLIC: wrapper that logs to your SDK and Event Box in one call
      model/
        OGEventBoxDebugEvent.kt PUBLIC: log row (index, time, name, params, warnings)
        OGEventBoxPosition.kt   bubble rail + Y ratio
        OGEventBoxUiState.kt    presentation state for MVVM
      data/
        OGEventBoxRepository.kt in-memory events, session state, expiry timer
        OGEventBoxStore.kt      SharedPreferences persistence (expiry, bubble position,
                                panel height and opacity, one-time hint flags)
      filter/
        OGEventBoxQuery.kt      search: terms, key:value, !exclude, is: keywords
        OGEventBoxFormat.kt     value type tags, +delta time, key/value rows
        OGEventBoxExport.kt     event / log to JSON for copy and share
      validator/
        OGEventBoxValidator.kt  PUBLIC: Firebase limit checks -> warning strings
      viewmodel/
        OGEventBoxViewModel.kt  collapsed/pinned state, anchor, unread badge, panel height
      view/
        OGEventBoxView.kt       the overlay view: panel, bubble, drag/snap, resize, close target
        OGEventBoxAdapter.kt    RecyclerView ListAdapter: row binding, stripes, x N badge, highlight
        OGEventBoxFileProvider.kt  shares long logs as a .json file from the cache folder
        OGEventBoxOverlay.kt    ActivityLifecycleCallbacks; owns the box window
    res/
      layout/   og_event_box.xml        <merge>: panel (header, list, resize handle) + bubble
                og_event_box_item.xml   one log row + expandable warning details
                og_event_box_close.xml  drop-to-close target
      drawable/ og_event_box_*.xml      shapes and vector icons
      xml/      og_event_box_file_paths.xml   cache folder exposed by the share FileProvider
      values/   colors.xml  dimens.xml  strings.xml   (all prefixed og_event_box_)
  src/test/     OGEventBox*Test.kt, OGEventBoxJavaInteropTest.java (JVM unit tests)
```

Everything is `internal` except `OGEventBoxLog`, `OGEventBoxAnalytics`, `OGEventBoxDebugEvent`,
`OGEventBoxBackgroundMode` and `OGEventBoxValidator`. Those are the compatibility surface and the
only names kept by ProGuard.

---

## 3. Components

### 3.1 `OGEventBoxLog` (public facade `object`)

- Fluent setters (all `@JvmStatic`, return `OGEventBoxLog`): `setDebugModeEnabled`,
  `setDebugModeDurationHours`, `setFirebaseChecksEnabled`, `setPanelResizingEnabled`,
  `setBubbleIconResId`, `setBubbleIconScaleType`, `setBubbleBackgroundColor`,
  `setBubbleBorderColor`, `setBubbleBorderWidthDp`, `setBubbleCornerRadiusDp`,
  `setBackgroundMode`, `setMaxEvents`, `setDetectDuplicates`, `setDuplicateWindowMs`,
  `setPanelAlpha`, `install`.
- Session: `setEnabled`, `disable`, `updateDebugModeDurationHours`, `remainingMinutes`,
  `isActive`, `isActiveFlow`, `hasOverlayPermission`, `requestOverlayPermission`.
- Logging: `log(name)`, `log(name, Bundle?)`, `log(name, Map?)` (each also with a `source` tag),
  `setUserProperty`, `clear`, `events` (default `MAX_EVENTS = 200`, adjustable 10..1000 with
  `setMaxEvents`; `droppedEventCount` says how many were dropped).
- Property setters are `@JvmSynthetic` so Java sees only the fluent form.

### 3.2 `OGEventBoxDebugEvent` (public `data class`)

`(index, timestampMillis, name, params: Map?, warnings: List<String>)` plus `hasWarning`.
`index` is 1-based and restarts after `clear()`.

### 3.3 Data layer

- `OGEventBoxRepository` -- `events` / `isActive` as `StateFlow`; `log` and `setUserProperty` run
  validation (when Firebase checks are on) and append under a lock. Recording is wrapped in
  `runCatching` and the caller's params map is copied first, so a mutating or throwing map can
  never crash the host. Owns the expiry `Handler`. `onStateChangedListener` lets the overlay
  re-sync when the session starts/stops.
- `OGEventBoxStore` -- prefs file `og_event_box_prefs`: session expiry epoch and the bubble's
  resting position (right edge flag + Y ratio). Every access is `runCatching`.

### 3.4 `OGEventBoxValidator`

Checks distinct event names (500), name/param/value lengths (40/40/100), parameter count (25),
characters and reserved prefixes (`ga_`, `google_`, `firebase_`), user properties (25 / 24 / 36)
and a ~100k/day volume warning. Firebase automatic events are excluded from the distinct count.
`reset()` is called from `clear()`.

### 3.5 `OGEventBoxViewModel`

Plain class (no Android `ViewModel`), singleton instance held by the overlay so state survives
Activity changes: `isCollapsed`, `isPinned`, `anchorX/anchorY`, `lastSeenIndex` (badge),
`customPanelHeight` (resize), `uiState`.

### 3.6 `OGEventBoxOverlay` and `OGEventBoxView`

- `OGEventBoxOverlay` registers lifecycle callbacks, keeps exactly one `OGEventBoxView` over the
  resumed Activity, re-raises its window when focus is lost (dialogs), and falls back to adding
  the view to `android.R.id.content` if the window cannot be added.
- `OGEventBoxView` is a `FrameLayout` that inflates `og_event_box.xml`. Responsibilities:
  rendering rows, header mirroring for the bubble's side, expand/collapse, drag and snap,
  panel height resize, close target, bubble styling.

---

## 4. Key mechanisms in `OGEventBoxView`

1. **Own window.** `windowParams()` -> `TYPE_APPLICATION`, flags
   `NOT_FOCUSABLE | NOT_TOUCH_MODAL | LAYOUT_IN_SCREEN | HARDWARE_ACCELERATED`,
   `gravity TOP|START`, `token = activity.window.attributes.token`.
2. **Position = window x/y/width.** `moveTo` mutates `LayoutParams.x/y/width` and calls
   `updateViewLayout` once. Width is the exact bubble width while collapsed and, while expanded,
   the screen width minus the left and right system-bar insets (x is the left inset), so a panel
   on the right is never drawn under a side navigation bar in landscape. Resizing in the *same* update as the move is what keeps a right-side bubble
   from visibly sliding when the panel opens or closes. In fallback mode it sets `translationX/Y`.
3. **Collapse / expand.** The panel scales and fades around a pivot at the bubble's corner. On
   the right side the bubble stays `INVISIBLE` until the window has shrunk and moved
   (`animateBubbleEntryWhenSettled` -> `showBubbleAfterMove`), then fades in; on the left it
   enters immediately.
4. **Header mirroring.** `applyHeaderOrientation` re-adds header children in mirrored order when
   the bubble is on the right, so the logo always sits next to the bubble. Every size comes from
   the `og_event_box_header_*` dimens, which the layout uses too, so the two cannot drift apart.
   On narrow panels `fitHeaderTitle` first drops the title (measured with full-size buttons, so the
   decision does not flip as they shrink), then `fitHeaderButtons` splits the remaining width over
   the five buttons, clamped to 28 dp..`og_event_box_header_button_width`, and trims their side
   padding to match. Both run from the header's layout-change listener.
5. **Snapping.** `snapPosition` = `clampPosition` (inside system-bar insets); for the collapsed
   bubble x is forced to the nearer rail. Settling uses a `ValueAnimator` (`animateTo`) because
   a window cannot be moved by a view animator.
6. **Restore / persist.** `restorePosition` reads the store or the default (right edge, top);
   `persistBubblePosition` writes on drop and on collapse, only while the session is active.
7. **Resize.** `ResizeTouchListener` changes `contentContainer`'s height between
   `og_event_box_min_list_height` and 85% of the screen (also bounded by space below the panel).
   While dragging in either direction the handle bar scales up and takes the accent colour
   (`setHandleHighlighted`). A drag starts only past the touch slop; a double-tap animates back to
   `og_event_box_list_height`. Until the first drag the bar pulses each time the panel opens.
   The height lives in `viewModel.customPanelHeight` and is saved (in dp) by `OGEventBoxStore`.
   Disabled entirely by `setPanelResizingEnabled(false)`.
8. **Warnings.** Rows with warnings show a ⚠ icon; tapping expands the details with
   `TransitionManager`, one open row at a time (`expandedEventIndex`).
9. **Close target.** Separate `FLAG_NOT_TOUCHABLE` window revealed while a drag is near the
   bottom centre. Dropping inside the "over" radius ends the session. `DragTouchListener`
   ignores the stray `ACTION_CANCEL` that arrives after the view is removed (`gestureActive`).
10. **Styling.** `styleBubble()` / `applyLogoTo()` read the `OGEventBoxLog.bubble*` options.
11. **Coroutines.** On attach a `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`
    collects the event flow; cancelled on detach.
12. **Clear with undo.** Clear calls `OGEventBoxRepository.clearUndoable()`, which keeps the removed
    entries, and shows an undo pill for 4 s. Undo calls `restore()`: entries logged in the meantime
    are kept and renumbered after the restored ones. The Firebase validator is reset only by
    `commitClear()`, when the offer ends (timeout, a second Clear, collapse or detach).
13. **Search.** `OGEventBoxQuery` is the only filter: text terms, `key:value`, `!` and the
    `is:warning` / `is:user_property` / `is:event` / `is:repeated` keywords. Pause and Share sit
    in the header; pausing is independent of the search bar, and Clear resumes.

Constants live in the companion object (auto-shrink 6 s, highlight 2.5 s, idle alpha 0.55,
animation durations, close-target radii).

---

## 5. Lifecycle walk-through

```
Application.onCreate
  OGEventBoxLog.install(ctx, enabled, hours)
    -> OGEventBoxOverlay.install(app)             (lifecycle callbacks)
    -> stored, unexpired, host-enabled session?   yes -> activate(expiry)

Host gesture
  OGEventBoxLog.setEnabled(ctx, true)
    -> persist expiry -> activate -> isActive=true, expiry timer, overlay.sync()
    -> attach(): new OGEventBoxView in its own window (collapsed bubble)

During session
  log("x") -> validate -> StateFlow emits -> OGEventBoxView.renderEvents (rows / badge)
  Activity change: resume -> attach() to the new Activity; stop (or finishing pause) of the
    current Activity -> detach() / overlay hand-over; plain pause keeps the box attached
    (system permission dialogs, translucent screens); state lives in the ViewModel
  Dialog opens: focus lost -> 150 ms -> remove + re-add window (back on top)
  Drag/drop: settle -> anchor + store position

End of session (any of)
  close target drop | expiry runnable | setEnabled(false) | install() sees host disabled
    -> disable(): clear store, reset anchor, isActive=false, clear log + validator, detach box
```

---

## 6. Build, shrink and obfuscation

- `eventBox/build.gradle.kts`: `release { isMinifyEnabled = true; proguardFiles(optimize,
  proguard-rules.pro) }`, `consumerProguardFiles("consumer-rules.pro")`.
  `isShrinkResources` is not available for libraries -- the host app does it.
- `proguard-rules.pro`: keeps the public API (`OGEventBoxLog`, `OGEventBoxAnalytics` and its sinks,
  `OGEventBoxDebugEvent`, `OGEventBoxBackgroundMode`, `OGEventBoxValidator`); everything else is repackaged into `com.ogmediaapps.eventbox.a`,
  renamed, and stripped of Kotlin intrinsic null checks and `Log.d/v`.
- `consumer-rules.pro`: the same keep rules only, shipped as `proguard.txt` in the AAR.
- Publishing: `jitpack.yml` runs `publishReleasePublicationToMavenLocal`; coordinates
  `com.github.JpOriginDev:event-box`, version from the `VERSION` env var (the git tag).
- If you add a **public** class, add it to *both* rules files.

---

## 7. Where to change things

| I want to... | Edit |
|---|---|
| Change auto-shrink / fade timings, animation speeds | constants in `OGEventBoxView` companion |
| Change default parking corner | `DEFAULT_ON_RIGHT_EDGE`, `DEFAULT_Y_RATIO` in `OGEventBoxView` |
| Change max log length | `OGEventBoxRepository.MAX_EVENTS` |
| Change colours / sizes / strings | `res/values/*.xml` (all `og_event_box_*`) |
| Change panel or bubble structure | `res/layout/og_event_box.xml` (then the `findViewById`s in `OGEventBoxView.init`) |
| Add a new styling option | property + fluent setter in `OGEventBoxLog`, read in `styleBubble/applyLogoTo` |
| Add or change a Firebase check | `OGEventBoxValidator` (+ a test in `OGEventBoxTest`) |
| Persist something new | key + accessors in `OGEventBoxStore`; remember `clear()` |
| Change session end behaviour | `OGEventBoxRepository.disable()` |

---

## 8. Known limits and gotchas

- A dialog covers the box ~150 ms before it is lifted back; IME and shade also trigger a re-add,
  which restarts the auto-shrink timer.
- The window stays attached while the Activity is only paused (e.g. the system permission dialog
  is showing). It is removed when the Activity stops or finishes, and re-added on resume.
- A ~18 dp margin around the bubble belongs to its window and does not pass touches through.
- Content-view fallback mode cannot float above dialogs.
- `install()` needs an `Application` context to register lifecycle callbacks.
- Validator counters are process-wide and reset on `clear()` / `disable()`.
- Tests are JVM unit tests (models, ViewModel, validator, fluent API, Java interop). Overlay
  behaviour is verified manually: enable, drag to both edges, open/close the panel on each side,
  resize both ways, rotate (portrait and landscape, bubble on either side), open a dialog, drop on
  the close target, swipe from Recents. Check the header on a narrow phone and at a large font or
  display size.
