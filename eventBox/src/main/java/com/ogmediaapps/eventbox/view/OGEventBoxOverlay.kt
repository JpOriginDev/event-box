package com.ogmediaapps.eventbox.view

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import com.ogmediaapps.eventbox.data.OGEventBoxRepository
import com.ogmediaapps.eventbox.model.OGEventBoxBackgroundMode
import com.ogmediaapps.eventbox.viewmodel.OGEventBoxViewModel

/**
 * Keeps exactly one [OGEventBoxView] over whichever Activity is in front, for as long as debug mode
 * is on. With [OGEventBoxBackgroundMode.AUTO] or [OGEventBoxBackgroundMode.OVERLAY_ONLY] and the
 * "Display over other apps" permission granted, it uses a system overlay window instead while the
 * app is in the background.
 *
 * The box gets a window of its own (`TYPE_APPLICATION`) on the Activity's token, allowing it to sit
 * above dialogs without requiring `SYSTEM_ALERT_WINDOW` permission.
 */
@SuppressLint("StaticFieldLeak")
internal object OGEventBoxOverlay : Application.ActivityLifecycleCallbacks {

    val viewModel = OGEventBoxViewModel()

    private var installed = false
    private var currentActivity: Activity? = null
    private var view: OGEventBoxView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var watchedDecor: View? = null
    private var hostWindowManager: WindowManager? = null
    private var attachedTarget = Target.NONE
    private var startedCount = 0
    private var appContext: Context? = null

    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (!hasFocus) view?.postDelayed(raiseRunnable, RAISE_DELAY_MS)
    }
    private val raiseRunnable = Runnable { raiseAboveNewWindows() }

    init {
        OGEventBoxRepository.onStateChangedListener = { sync() }
    }

    fun install(application: Application) {
        if (installed) return
        installed = true
        appContext = application
        application.registerActivityLifecycleCallbacks(this)
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun resetPosition() {
        viewModel.resetSessionState()
    }

    /** Attaches or removes the box so it matches [OGEventBoxRepository.isActive]. Safe from any thread. */
    fun sync() {
        runCatching {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                if (OGEventBoxRepository.isActive) attach() else detach()
            } else {
                mainHandler.post { if (OGEventBoxRepository.isActive) attach() else detach() }
            }
        }
    }

    /** Updates the resizing controls in the active view, if attached. Safe from any thread. */
    fun updateResizingState() {
        runCatching {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                view?.updateResizingState()
            } else {
                mainHandler.post { view?.updateResizingState() }
            }
        }
    }

    private enum class Target { NONE, ACTIVITY, OVERLAY }

    private fun desiredTarget(): Target {
        val mode = OGEventBoxRepository.backgroundMode
        val overlayOk = mode != OGEventBoxBackgroundMode.OFF && canDrawOverlays(appContext)
        return when {
            mode == OGEventBoxBackgroundMode.OVERLAY_ONLY -> if (overlayOk) Target.OVERLAY else Target.NONE
            currentActivity != null -> Target.ACTIVITY
            overlayOk && startedCount == 0 -> Target.OVERLAY
            else -> Target.NONE
        }
    }

    private fun attach() {
        val target = desiredTarget()
        val activity = currentActivity
        if (target == attachedTarget && (target != Target.ACTIVITY || view?.context === activity)) return
        detach()
        when (target) {
            Target.ACTIVITY -> attachToActivity(activity ?: return)
            Target.OVERLAY -> attachOverlay()
            Target.NONE -> Unit
        }
    }

    private fun attachToActivity(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val box = OGEventBoxView(activity, viewModel)
        val params = OGEventBoxView.windowParams(activity)
        val added = runCatching {
            box.bindWindow(activity.windowManager, params)
            activity.windowManager.addView(box, params)
        }.isSuccess
        if (added) {
            windowParams = params
            hostWindowManager = activity.windowManager
        } else {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            box.bindWindow(null, null)
            content.addView(
                box,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            windowParams = null
            hostWindowManager = null
        }
        view = box
        attachedTarget = Target.ACTIVITY
        watchWindowFocus(activity)
    }

    private fun attachOverlay() {
        val context = appContext ?: return
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val box = OGEventBoxView(context, viewModel)
        val params = OGEventBoxView.overlayWindowParams()
        val added = runCatching {
            box.bindWindow(manager, params)
            manager.addView(box, params)
        }.isSuccess
        if (!added) return
        view = box
        windowParams = params
        hostWindowManager = manager
        attachedTarget = Target.OVERLAY
    }

    private fun detach() {
        stopWatchingWindowFocus()
        view?.let { box ->
            box.onDetaching()
            box.removeCallbacks(raiseRunnable)
            runCatching {
                if (windowParams != null) {
                    hostWindowManager?.removeViewImmediate(box)
                } else {
                    (box.parent as? ViewGroup)?.removeView(box)
                }
            }
        }
        view = null
        windowParams = null
        hostWindowManager = null
        attachedTarget = Target.NONE
    }

    /** True if the app may draw over other apps. Never throws. */
    fun canDrawOverlays(context: Context?): Boolean {
        if (context == null) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
    }

    private fun raiseAboveNewWindows() {
        if (attachedTarget != Target.ACTIVITY) return
        val activity = currentActivity ?: return
        val box = view ?: return
        val params = windowParams ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        runCatching {
            activity.windowManager.removeViewImmediate(box)
            activity.windowManager.addView(box, params)
        }
    }

    private fun watchWindowFocus(activity: Activity) {
        stopWatchingWindowFocus()
        if (windowParams == null) return
        val decor = activity.window.decorView
        decor.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        watchedDecor = decor
    }

    private fun stopWatchingWindowFocus() {
        watchedDecor?.let { decor ->
            runCatching { decor.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener) }
        }
        watchedDecor = null
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        sync()
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivity !== activity) return
        currentActivity = null
        // Without background display this just detaches; with it, the box stays put until the
        // app really leaves the foreground (see onActivityStopped).
        if (OGEventBoxRepository.isActive) sync() else detach()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) {
        startedCount++
    }

    override fun onActivityStopped(activity: Activity) {
        if (startedCount > 0) startedCount--
        // Last Activity gone: the app is in the background, so hand over to the overlay if allowed.
        if (startedCount == 0) sync()
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
            if (OGEventBoxRepository.isActive) sync() else detach()
        }
    }

    private const val RAISE_DELAY_MS = 150L
}
