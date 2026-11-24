# Changelog

All notable changes to Event Box. Install snippets are in the [README](README.md).

## 1.4.2

Fixes to the panel on small screens and in landscape. No API changes, so upgrading from 1.4.0 needs
no code changes. (There is no 1.4.1.)

### Fixed
- **Header on narrow panels**: the five header buttons were a fixed 40 dp wide, so on a narrow
  phone (or at a large display size) the first or last icon was pushed off the edge. They now share
  the room left after the logo and count, between 28 dp and 40 dp wide, and keep their 48 dp touch
  height. The `EVENTS` title still hides first when space is tight.
- **Landscape**: with the navigation bar on the side, an open panel on the right was drawn under
  the bar and its logo was cut off. The open panel's window now spans the area between the system
  bars.

## 1.4.0

A faster, searchable panel. Everything in the public API is additive: nothing was removed, so
upgrading from 1.3.1 needs no code changes. The panel header changed, see **Changed**.

### Added
- **Search**: a search bar with the keyboard ready. Type `login` (name, source, key or value),
  `screen:home` (a parameter's value), `!term` to exclude, or keywords `is:warning`,
  `is:user_property`, `is:event` and `is:repeated`. Terms combine, for example
  `is:warning !purchase`. Matches are highlighted.
- **Pause / Resume**: freeze the list while events keep recording (`▶ Resume (+N)`).
- **Share and copy**: share the visible events as JSON; long logs go out as a `.json` file through
  the library's own `FileProvider`. Long-press a row to copy it as JSON.
- **Undo for Clear**: a `Cleared N events · UNDO` pill for 4 seconds. Events logged in the
  meantime are kept.
- **Source tags**: `OGEventBoxLog.log(name, params, source)` and
  `OGEventBoxAnalytics.wrap(..., source = "firebase")`. The tag shows on the row and is searchable.
- **Duplicate merging**: identical back-to-back events become one row with a `×N` badge.
  `setDetectDuplicates(...)` and `setDuplicateWindowMs(...)` control it (on, 500 ms by default).
- **Configurable log size**: `setMaxEvents(10..1000)` (200 by default) and `droppedEventCount`,
  with an "N older events dropped" notice in the panel.
- **Panel opacity**: `setPanelAlpha(0.6f..1f)`, or long-press the logo to cycle it.
- **Remembered settings**: the panel height and the opacity picked by the tester are saved on
  the device. Double-tap the bottom handle to reset the height.
- **Readability**: colour stripes (amber = warning, blue = user property), `+1.2s` deltas between
  events, a key/value table with value types (`S`/`L`/`D`/`B`), a `↓ N new` button when scrolled up.
- **Accessibility and feel**: TalkBack labels for every control, 48 dp touch targets, haptic
  feedback, a one-time tip for the logo gestures, and a width cap on tablets.
- The `OGEventBoxDebugEvent` constructor and `OGEventBoxLog.log(...)` keep their old shapes
  (`source` and `repeatCount` have defaults), so existing Kotlin and Java callers still compile.

### Changed
- **Header**: `[Logo] [Title] [Count] … [Search] [Pause] [Share] [Pin] [Clear]`, mirrored when the
  bubble is on the right. Clear uses a trash icon. The header **Resize and Minimize buttons are
  removed**: tap the logo to shrink the panel and drag the bottom handle to resize it.
- The event list is a RecyclerView, so only changed rows are redrawn. The library now depends on
  `androidx.recyclerview` (pulled in transitively).
- The library manifest declares one non-exported `<provider>` for sharing logs
  (`${applicationId}.ogeventbox.files`). It uses its own class name, so it does not clash with a
  `FileProvider` of your own.
- Firebase limit counters are reset only when the Clear undo offer ends.
- The box stays visible while the search field is typing: it no longer re-adds its window when
  its own field takes focus.

### Fixed
- The Firebase limit checks called `ConcurrentHashMap.newKeySet()`, which needs API 24 although
  `minSdk` is 21 (a likely crash on API 21-23). They now use `Collections.newSetFromMap`.
- Lint `NewApi` errors from `View.setForeground` (API 23) in the panel view.

## 1.3.1

The box stays visible while a system dialog (such as a runtime permission prompt) pauses the
Activity. It is removed only when the Activity stops or finishes.

## 1.3.0

`OGEventBoxAnalytics`: a wrapper that logs to your analytics SDK and to Event Box in one call.

## 1.2.1

R8 keeps `OGEventBoxBackgroundMode`; the sample app gains a background mode selector.

## 1.2.0

Background overlay mode: `setBackgroundMode(OFF | AUTO | OVERLAY_ONLY)`, `hasOverlayPermission` and
`requestOverlayPermission`.
