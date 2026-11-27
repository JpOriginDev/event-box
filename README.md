# Event Box

[![JitPack](https://jitpack.io/v/JpOriginDev/event-box.svg)](https://jitpack.io/#JpOriginDev/event-box)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-21-brightgreen.svg)](#1-add-the-dependency)
[![Kotlin](https://img.shields.io/badge/Kotlin-%20%7C%20Java-7F52FF.svg?logo=kotlin&logoColor=white)](#4-log-your-events)

A floating on-screen log for Android testers. A small bubble sits on top of your app; tap it to
open a panel that shows your analytics events as they fire, so you don't have to watch logcat.

<p align="center">
  <img src="docs/new_event_box.gif" alt="Event Box demo: five taps turn it on, then events and Firebase warnings appear live in the floating panel" width="320">
</p>

Think of it as **Firebase DebugView on the device itself**: no adb, no USB cable, no logcat filters.

- For testing only: it can be switched off completely and records nothing while off.
- No permissions needed (the background overlay is optional and opt-in).
- `minSdk 21`, pure Kotlin, works from Java too.

> **Latest (next release):**
> - **Clock button**: show the time since the previous event on each row (`+1.2s`). It is **off by
>   default**; turn it on in code.
> - **Parameter stripe**: rows that carry parameters get a teal stripe.
> - **Quota notice**: a small pill appears when you go over Firebase's 500 event names or 25 user
>   properties.
> - **System event names**: logging a name Firebase collects itself (like `first_open`) gets a warning.
> - **Choose what shows**: hide any button or part, and pick how the box starts.
> - **Choose where it shows**: `setAvailability` replaces `BuildConfig.DEBUG`.
> - **Bubble style and panel opacity** are set in one call each.
> - The header buttons scroll sideways, so all of them stay reachable on narrow phones.
>
> **New in 1.4.2:** search (`is:warning`, `key:value`, `!exclude`), pause, share as JSON, undo for
> Clear, source tags, duplicate merging, a configurable log size, and a remembered panel height and
> opacity. The header Resize and Minimize buttons are gone: tap the logo to shrink the panel and
> drag the bottom handle to resize it. The header also fits narrow phones, and the open panel stays
> clear of the navigation bar in landscape. See the [changelog](CHANGELOG.md).

## Why use Event Box?

**The problem:** checking analytics events means plugging in a cable, filtering logcat or setting up
DebugView. Testers can't do it on their own phones, and wrong events (bad names, too many
parameters) are often found only after release.

**Event Box fixes that:**

| Who | How it helps |
|---|---|
| **QA and testers** | See every event live on the phone, in the real build, with no cable or tools. |
| **Android developers** | Verify that the right event fires with the right parameters while you build a feature. |
| **Analytics and product teams** | Check a release flow end to end and catch tracking mistakes before users do. |
| **Teams using Firebase Analytics** | Get an instant ⚠ warning when an event breaks a Firebase limit. |

## Features

- **Live event log**: events appear as they fire, with number, time, name and parameters.
- **Floating bubble and panel**: drag it anywhere, it snaps to the screen edge, shows an unread
  badge, and shrinks back on its own.
- **Firebase limit warnings**: a ⚠ icon on any event that breaks a Firebase Analytics rule, with
  the exact reason on tap. This includes names that are too long and names Firebase reserves for
  itself. A small pill tells you when you pass the 500 event name or 25 user property limit.
- **Search**: type `login`, `screen:home`, `firebase`, `is:warning` or `!user_property`.
  Matches are highlighted.
- **Copy and share**: long-press a row to copy it as JSON, or share the visible log. Long
  sessions are shared as a `.json` file.
- **Undo**: Clear shows a `Cleared N events · UNDO` pill for 4 seconds, so a slip never loses
  the log.
- **Fast list**: a RecyclerView that only redraws the rows that changed. The log size can be set
  from 10 to 1000 (200 by default), with an "N older events dropped" notice.
- **Spot bugs fast**: back-to-back duplicates are merged into one row shown as `×N`, rows get a
  colour stripe (amber = warning, blue = user property, teal = has parameters), optional `+1.2s` deltas, a key/value table with
  value types (`S`/`L`/`D`/`B`), and pause/resume.
- **Source tags**: tell Firebase, AppsFlyer, Mixpanel and other events apart, and search by source.
- **Resizable, pinnable panel**: drag the bottom handle to change the height (double-tap it to
  reset), pin to keep it open. Adjustable opacity, 48 dp touch targets, haptic feedback, and a
  width cap on tablets. The height and opacity you pick are remembered. The header fits narrow
  phones, and the open panel stays clear of the system bars in landscape.
- **Accessible**: TalkBack labels for every control, including the logo's tap and long-press,
  the resize handle (reset as its action) and the undo pill.
- **Background overlay** (optional): keep the box visible when your app is in the background or in
  recents (`OFF`, `AUTO`, `OVERLAY_ONLY`).
- **Safe by design**: a kill switch, a timed session that ends by itself, and logging that never
  throws into your app.
- **Simple API**: fluent setup, `log()` with a name, a `Bundle` or a `Map`, plus user properties.
- **Customizable bubble**: your own icon, colors, border and corner radius.
- **Choose what shows**: hide any button or part of the box, and pick how it starts (open or
  bubble, pinned or not).
- **Reactive**: read events and state as Kotlin `StateFlow`.
- **Kotlin and Java**: both are supported, with its own R8 rules built in.

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
    implementation("com.github.JpOriginDev:event-box:1.4.2")
}
```

The library supports compileSdk 36+ and AGP 9.0+.

## 2. Set it up

Call this once in your `Application.onCreate()`:

**Kotlin**
```kotlin
OGEventBoxLog.setAvailability(OGEventBoxAvailability.DEBUG_ONLY)
    .setDebugModeDurationHours(0.5)
    .install(this)
```

**Java**
```java
OGEventBoxLog.setAvailability(OGEventBoxAvailability.DEBUG_ONLY)
    .setDebugModeDurationHours(0.5)
    .install(this);
```

- `setAvailability` chooses the builds where the box may appear. It reads the build kind from your app
  (its `debuggable` flag), so you do not pass `BuildConfig.DEBUG`:

  | Value | Debug build | Release build |
  |---|---|---|
  | `DEBUG_ONLY` | shown | hidden |
  | `RELEASE_ONLY` | hidden | shown |
  | `DEBUG_AND_RELEASE` | shown | shown |
  | `NEVER` | hidden | hidden |

  Hidden means the box can never appear and `log()` does nothing. Without `setAvailability` it stays off.
  The old `setDebugModeEnabled(Boolean)` still works but is deprecated; replace it with `setAvailability`.
- `setDebugModeDurationHours` is how long a session lasts (here, 30 minutes).
- `install(this)` leaves the box off until a tester switches it on. Use `installAndShow(this)` instead to
  start a session straight away, so the box shows on launch. It still respects `setAvailability`.
- The box remembers what the tester does by hand (on by default, `setRememberUserChoices(false)` turns it off):
  a box they minimized stays a bubble until they open it again, and a box they closed (dropped on the close
  target) does not start again by itself, even in `installAndShow`, until the app's data is cleared. Switching
  it on with `setEnabled(context, true)` counts as a new decision and works again. `resetUserChoices(context)`
  forgets both, for QA.

## 3. Turn it on

Setup alone shows nothing. Your app must also switch it on, for example from a hidden gesture
(five taps on a title) or a debug menu:

```kotlin
OGEventBoxLog.setEnabled(context, true)
```

The session then runs for the duration you set, and turns itself off when time is up. The bubble
comes back if the app is closed and reopened during the session.

## 4. Log your events

**Kotlin**
```kotlin
// Simple event
OGEventBoxLog.log("user_signed_in")

// Event with Bundle parameters (Firebase Analytics style)
val bundle = Bundle().apply {
    putString("item_id", "sku_101")
    putDouble("price", 29.99)
}
OGEventBoxLog.log("purchase", bundle)

// Event with Map parameters
OGEventBoxLog.log("item_selected", mapOf("item_name" to "Sneakers"))

// User property
OGEventBoxLog.setUserProperty("user_tier", "premium")
```

**Java**
```java
// Simple event
OGEventBoxLog.log("user_signed_in");

// Event with Bundle parameters
Bundle bundle = new Bundle();
bundle.putString("item_id", "sku_101");
OGEventBoxLog.log("purchase", bundle);

// Event with Map parameters
Map<String, Object> params = new HashMap<>();
params.put("item_name", "Sneakers");
OGEventBoxLog.log("item_selected", params);

// User property
OGEventBoxLog.setUserProperty("user_tier", "premium");
```

### Log to Firebase and Event Box in one call (optional)

Instead of calling `OGEventBoxLog.log()` next to every analytics call, wrap your SDK once. The
wrapper has no analytics dependency of its own, so it works with Firebase, Amplitude, Mixpanel or
anything else.

**Kotlin**
```kotlin
val analytics = OGEventBoxAnalytics.wrap(
    eventSink = { name, params -> firebaseAnalytics.logEvent(name, params) },
    userPropertySink = { name, value -> firebaseAnalytics.setUserProperty(name, value) }
)

analytics.logEvent("purchase", bundle)
analytics.setUserProperty("user_tier", "premium")
```

**Java**
```java
OGEventBoxAnalytics analytics = OGEventBoxAnalytics.wrap(
    (name, params) -> firebaseAnalytics.logEvent(name, params),
    (name, value) -> firebaseAnalytics.setUserProperty(name, value));

analytics.logEvent("purchase", bundle);
```

Pass `source = "firebase"` to `wrap(...)` to tag every event with where it came from; the tag
shows on the row and the search matches it.

The event goes to Event Box first (it never throws), then to your SDK. While the box is off, only
your SDK call runs. `userPropertySink` is optional.

Log each event once. Calls are ignored while the box is off, and logging never throws into your
app.

### Firebase Limits & Validation Warnings

Event Box automatically checks each logged event against Firebase Analytics rules:
1. **Distinct Event Names**: Up to 500 distinct custom event types per app.
2. **Event Name Length**: Max 40 characters (alphanumeric and underscores).
3. **Parameters Per Event**: Max 25 unique parameters per event.
4. **Parameter Name Length**: Max 40 characters (alphanumeric and underscores).
5. **Parameter Value Length**: Max 100 characters (Standard GA).
6. **User Properties**: Max 25 per app (names ≤ 24 chars, values ≤ 36 chars).
7. **Daily Event Volume**: Warns when approaching ~100,000 events/user/day throttle.

Event Box also warns when:
- An event name is **too long**, has invalid characters, or starts with `ga_`, `google_` or `firebase_`.
- An event uses a name **Firebase collects by itself**, such as `first_open` or `session_start`.
  Don't log these yourself. (`screen_view` is allowed.)

If an event breaks any of these rules, a warning icon (**⚠**) appears at the end of its row. Tap
it to expand the row and see the full event name and the exact reason; opening another row closes
the first. Type `is:warning` in search to list only the events with warnings.

**Quota notice:** once you pass 500 distinct event names or 25 user properties, a small amber pill
at the top of the list reads, for example, `Over Firebase limit · events 501/500`. It goes away when
you clear the log.

#### Enabling or Disabling Firebase Checks

Firebase checks are on by default. Turn them off or on at any time. While they are off, there are
no warnings and no quota notice:

**Kotlin**
```kotlin
OGEventBoxLog.setFirebaseChecksEnabled(false)
```

**Java**
```java
OGEventBoxLog.setFirebaseChecksEnabled(false);
```

#### Enabling or Disabling Panel Height Resizing

Panel resizing is on by default. Testers drag the handle at the bottom of the panel up or down to
change its height (the width stays fixed), and double-tap it to go back to the default height.
The chosen height is remembered on the device.

When resizing is off, the handle is hidden and the panel keeps its default height.

**Kotlin**
```kotlin
OGEventBoxLog.setPanelResizingEnabled(false)
```

**Java**
```java
OGEventBoxLog.setPanelResizingEnabled(false);
```

## Using the box

- **Bubble**: tap to open the panel, drag to move it. It snaps to the left or right edge and fades
  when idle. The badge shows how many events arrived since you last opened the panel.
- **Panel**: shows `number  time  event_name`, newest at the bottom. It shrinks back to the bubble
  after 6 seconds without touch (it waits while the one-time tip or the undo pill is showing).
    - **Logo**: tap to shrink it back to the bubble now; long-press to cycle the panel's
      transparency (100% → 85% → 70% → 60%). The first time the panel opens, a one-time tip
      explains these gestures (tap it to dismiss).
    - **Bottom handle**: drag up or down to resize the panel; double-tap it to go back to the
      default height. While you drag, its bar widens and lights up. Until you have resized once,
      it briefly lights up each time the panel opens.
    - **Warning icon**: tap to smoothly expand/collapse Firebase limit warnings.
    - **Clock** (optional, hidden by default): shows or hides the `+1.2s` time since the previous
      event on every row. Your choice is remembered. See [Show or hide parts](#show-or-hide-parts-of-the-box-optional).
    - **Pin**: stops it from auto-shrinking.
    - **Clear** (trash icon): empties the log. A `Cleared N events · UNDO` pill stays for 4
      seconds; tap it to bring the entries back (events logged in the meantime are kept too).
    - **Search** 🔍: opens the search bar with the keyboard ready. Query syntax is
      case-insensitive and space means AND: `login` (name, source, key or value), `screen:home`
      (that parameter's value), `!term` (exclude), and keywords for the kind of entry:
      `is:warning` (broke a Firebase limit), `is:user_property`, `is:event` and `is:repeated`
      (fired back to back). Combine them: `is:warning !purchase`.
    - **Pause**: freeze the list while events keep recording (`▶ Resume (+N)`). Closing search
      keeps it paused; Clear resumes it.
    - **Share**: share the visible events (after any search) as JSON.
    - **Rows**: tap to see parameters and warnings, long-press to copy the event as JSON.
      `↓ N new` appears when you have scrolled up and new events arrive.
- **Close**: drag the box toward the bottom of the screen and drop it on the close target to end
  the session.

The header reads `[Logo] [Count] … [Clock] [Search] [Pause] [Share] [Pin] [Clear]`, mirrored when
the bubble is on the right edge. The clock is hidden unless you turn it on. On narrow phones the
`EVENTS` title hides itself first, and the buttons scroll sideways if there is still not enough
room, so none of them is ever pushed off the edge.

The open panel stays between the system bars: in landscape it is not drawn under the navigation
bar, whichever side the bubble is on.

The panel height and transparency you pick are remembered on the device, across app restarts and
debug-mode sessions. A transparency picked by long-press overrides `setPanelAlpha(...)`.

## Customize the bubble (optional)

Call these before `install()`:

```kotlin
OGEventBoxLog.setBubbleStyle {
    iconRes(R.mipmap.my_logo)
    iconScaleType(ImageView.ScaleType.CENTER_CROP)
    backgroundColor(Color.parseColor("#0D9379"))
    borderColor(Color.parseColor("#CCE6E1"))
    borderWidthDp(1.5f)
    fullyRound()
}
    .setAvailability(OGEventBoxAvailability.DEBUG_ONLY)
    .setDebugModeDurationHours(0.5)
    .install(this)
```

## Show or hide parts of the box (optional)

Everything is shown by default, except the clock button. Change it before `install()`, or live:

```kotlin
OGEventBoxLog
    .setStartState(expanded = true, pinned = true, deltaTimeShown = true)
    .setDeltaTimeButtonVisible(true)                 // show the clock button
    .hideComponents(OGEventBoxComponent.SHARE_BUTTON, OGEventBoxComponent.TIPS)
    .install(this)
```

| Call | What it does |
|---|---|
| `setComponentVisible(component, visible)` | Show or hide one part |
| `hideComponents(...)` / `showComponents(...)` | Hide or show several parts |
| `showOnly(...)` | Show only the parts you list |
| `setStartState(expanded, pinned, deltaTimeShown)` | How the box starts. Anything you leave out keeps its value |
| `setDeltaTimeButtonVisible(true)` | Show the clock button (hidden by default) |
| `setDeltaTimeShown(true)` | Start with the `+1.2s` times visible (off by default) |

Parts you can hide: `LOGO`, `TITLE`, `COUNT`, `CLEAR_BUTTON`, `PIN_BUTTON`, `SHARE_BUTTON`,
`PAUSE_BUTTON`, `SEARCH_BUTTON`, `DELTA_TIME_BUTTON`, `RESIZE_HANDLE`, `UNREAD_BADGE`, `TIPS`.

A tester can change the pin or the time display while its button is shown. Hide the button and
your value is final.

## Keep it visible in the background (optional)

By default the box shows only while one of your Activities is in front. To keep it visible in the
background and in recents, pick a mode and ask the user for "Display over other apps":

```kotlin
OGEventBoxLog.setBackgroundMode(OGEventBoxBackgroundMode.AUTO)

if (!OGEventBoxLog.hasOverlayPermission(context)) {
    OGEventBoxLog.requestOverlayPermission(context) // opens the system settings screen
}
```

| Mode | Behaviour |
|---|---|
| `OFF` (default) | Activity window only. No permission needed. |
| `AUTO` | Activity window in front. Moves to a system overlay in the background if the permission is granted, otherwise hides. |
| `OVERLAY_ONLY` | Always the system overlay; hidden while the permission is missing. |

The permission is checked live, so granting or revoking it takes effect without restarting. The
library never opens the settings screen by itself. The overlay lasts only while your app's process
is alive; Android may stop a backgrounded process.

The library does not declare `SYSTEM_ALERT_WINDOW`. Add it to your own app's manifest, ideally in
`src/debug/AndroidManifest.xml` so it stays out of release builds:

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

Without it, `hasOverlayPermission()` returns `false` and the box behaves as with `OFF`.

## Other controls

### Session and status

| Call | What it does |
|---|---|
| `OGEventBoxLog.isActive` | Is the box on? |
| `OGEventBoxLog.isActiveFlow` | Same, as a `StateFlow` |
| `OGEventBoxLog.remainingMinutes(context)` | Minutes left (0 when off) |
| `OGEventBoxLog.updateDebugModeDurationHours(context, 1.0)` | Changes the session length |
| `OGEventBoxLog.disable()` | Ends the session now |

### Logging

| Call | What it does |
|---|---|
| `OGEventBoxLog.log(name, params, source)` | Logs with a source tag, e.g. `"firebase"` |
| `OGEventBoxLog.setUserProperty(name, value)` | Records a user property |
| `OGEventBoxLog.events` | The log as a `StateFlow` |
| `OGEventBoxLog.clear()` | Empties the log |
| `OGEventBoxLog.setMaxEvents(n)` | Log size, 10 to 1000 (default 200) |
| `OGEventBoxLog.setDetectDuplicates(enabled)` | Merges repeated events into one `×N` row |
| `OGEventBoxLog.setDuplicateWindowMs(ms)` | How close duplicates must be (default 500 ms) |

### What shows and how it starts

| Call | What it does |
|---|---|
| `OGEventBoxLog.setStartState(expanded, pinned, deltaTimeShown)` | How the box starts |
| `OGEventBoxLog.setComponentVisible(component, visible)` | Shows or hides one part |
| `OGEventBoxLog.setDeltaTimeButtonVisible(visible)` | Shows the clock button (hidden by default) |
| `OGEventBoxLog.setDeltaTimeShown(shown)` | Starts with `+1.2s` times on (off by default) |

### Panel

| Call | What it does |
|---|---|
| `OGEventBoxLog.setPanelAlpha(0.6f..1f)` | Panel opacity, 0.6 to 1 |
| `OGEventBoxLog.setPanelResizingEnabled(enabled)` | Turns height resizing on or off |

### Background

| Call | What it does |
|---|---|
| `OGEventBoxLog.setBackgroundMode(mode)` | Show in background: `OFF`, `AUTO`, `OVERLAY_ONLY` |
| `OGEventBoxLog.hasOverlayPermission(context)` | Is "Display over other apps" granted? |
| `OGEventBoxLog.requestOverlayPermission(context)` | Opens the screen to grant it |

### Firebase checks

| Call | What it does |
|---|---|
| `OGEventBoxLog.setFirebaseChecksEnabled(enabled)` | Turns Firebase limit checks on or off |

## Release builds

Nothing extra is needed. The library ships its own ProGuard/R8 rules and keeps only the public API
(`OGEventBoxLog`, `OGEventBoxAnalytics`, `OGEventBoxDebugEvent`, `OGEventBoxBackgroundMode`,
`OGEventBoxAvailability`, `OGEventBoxComponent`, `OGEventBoxBubbleStyle`, `OGEventBoxValidator`). Your app's own R8 obfuscates the rest.

The box shows event names and parameters on screen, which may include user data. Always gate it with
`setAvailability(OGEventBoxAvailability.DEBUG_ONLY)` and never enable it for end users.

## Known limits

- The background overlay needs `SYSTEM_ALERT_WINDOW` in your app and only lasts while Android keeps
  your process alive.
- The bubble sits on the left or right edge; its window is as wide as the bubble while collapsed,
  so it never blocks touches elsewhere.
- The box stays visible while a system dialog (such as a runtime permission prompt) is open over
  your Activity; it only hides when the Activity stops or finishes.
- A dialog opened later briefly covers the box (about 150 ms) before it lifts back on top.
- The box re-adds itself when the notification shade or keyboard takes focus, which restarts the
  auto-shrink timer.

## License

MIT, see [`LICENSE`](LICENSE). Third-party notices (Material Icons, Apache-2.0) are in
[`NOTICE`](NOTICE). Not affiliated with or endorsed by Google; the Firebase limits checked are
best-effort and may change.

## More

How the code is organized: [`eventBox/ARCHITECTURE.md`](eventBox/ARCHITECTURE.md)
