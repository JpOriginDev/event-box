package com.ogmediaapps.eventbox.view

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import com.ogmediaapps.eventbox.data.OGEventBoxRepository
import com.ogmediaapps.eventbox.viewmodel.OGEventBoxViewModel

/**
 * Keeps exactly one [OGEventBoxView] over whichever Activity is in front, for as long as debug mode
 * is on.
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
        application.registerActivityLifecycleCallbacks(this)
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun resetPosition() {
        viewModel.resetSessionState()
    }

    /** Attaches or removes the box so it matches [OGEventBoxRepository.isActive]. Safe from any thread. */
    fun sync() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (OGEventBoxRepository.isActive) attach() else detach()
        } else {
            mainHandler.post { if (OGEventBoxRepository.isActive) attach() else detach() }
        }
    }

    private fun attach() {
        val activity = currentActivity ?: return
        if (view?.context === activity) return
        detach()
        if (activity.isFinishing || activity.isDestroyed) return
        val box = OGEventBoxView(activity, viewModel)
        val params = OGEventBoxView.windowParams(activity)
        val added = runCatching {
            box.bindWindow(activity.windowManager, params)
            activity.windowManager.addView(box, params)
        }.isSuccess
        if (added) {
            windowParams = params
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
        }
        view = box
        watchWindowFocus(activity)
    }

    private fun detach() {
        stopWatchingWindowFocus()
        view?.let { box ->
            box.onDetaching()
            box.removeCallbacks(raiseRunnable)
            runCatching {
                if (windowParams != null) {
                    currentActivity?.windowManager?.removeViewImmediate(box)
                } else {
                    (box.parent as? ViewGroup)?.removeView(box)
                }
            }
        }
        view = null
        windowParams = null
    }

    private fun raiseAboveNewWindows() {
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
        detach()
        currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            detach()
            currentActivity = null
        }
    }

    private const val RAISE_DELAY_MS = 150L
}
