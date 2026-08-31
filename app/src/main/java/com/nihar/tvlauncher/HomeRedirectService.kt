package com.nihar.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * Makes this launcher the effective home on Google TV boxes whose Home button ignores the
 * ROLE_HOME holder (this TCL: the Home button uses priority-based resolution and always
 * lands on the stock launcher, and disabling the stock launcher drops onto a dead
 * recovery-home black screen — see CLAUDE.md).
 *
 * We can't intercept the Home *key* (system keys aren't delivered to accessibility
 * services), so we instead watch for the stock launcher coming to the foreground and
 * immediately launch ourselves over it — the same approach Projectivy uses. The stock
 * launcher stays enabled; the user turns this on under Settings > Accessibility.
 *
 * ## Cold-start "curtain"
 * The launcher UI runs in a separate process that the low-memory killer reclaims while you
 * use heavy apps (e.g. a long YouTube video). When you then press Home, our UI has to
 * cold-start, and the stock launcher stays visible for that ~1–2 s — the annoying "flash".
 * This service (which lives in its own always-alive :home process) fixes that: the instant
 * it sees the stock launcher, it drops a full-screen opaque overlay to hide it, launches
 * our UI behind the overlay, and lifts the overlay only when [MainActivity] reports it has
 * drawn (via [ACTION_LAUNCHER_SHOWN]) — or after a hard timeout, so it can never get stuck.
 * Removal is driven by that explicit signal rather than window events, because the overlay
 * and the launcher share this package, so window events can't tell them apart. Result:
 * Home shows black → our launcher, never the stock launcher.
 */
class HomeRedirectService : AccessibilityService() {

    private var lastRedirectAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var curtain: View? = null
    private val removeCurtainRunnable = Runnable { removeCurtain() }

    // MainActivity broadcasts ACTION_LAUNCHER_SHOWN once its window has drawn -> lift.
    private val launcherShownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = removeCurtain()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ContextCompat.registerReceiver(
            this,
            launcherShownReceiver,
            IntentFilter(ACTION_LAUNCHER_SHOWN),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in STOCK_LAUNCHERS) return

        // A single Home press emits several window events; collapse them.
        val now = SystemClock.uptimeMillis()
        if (now - lastRedirectAt < REDIRECT_DEBOUNCE_MS) return
        lastRedirectAt = now

        // Cover the stock launcher instantly, THEN bring our launcher up behind it.
        showCurtain()

        // NO_ANIMATION: our window replaces the stock launcher without a launch cross-fade.
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    or Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
        runCatching { startActivity(intent) }
    }

    /** Add an opaque full-screen overlay above everything, hiding the stock launcher. */
    private fun showCurtain() {
        // Always (re)arm the safety timeout so the curtain can never linger.
        handler.removeCallbacks(removeCurtainRunnable)
        handler.postDelayed(removeCurtainRunnable, CURTAIN_TIMEOUT_MS)
        if (curtain != null) return

        val wm = getSystemService(WindowManager::class.java) ?: return
        val view = View(this).apply { setBackgroundColor(CURTAIN_COLOR) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        )
        // If the overlay can't be added, we still launched the activity above — no harm.
        runCatching { wm.addView(view, lp) }.onSuccess { curtain = view }
    }

    private fun removeCurtain() {
        handler.removeCallbacks(removeCurtainRunnable)
        val view = curtain ?: return
        curtain = null
        val wm = getSystemService(WindowManager::class.java) ?: return
        runCatching { wm.removeView(view) }
    }

    override fun onInterrupt() { removeCurtain() }

    override fun onUnbind(intent: Intent?): Boolean {
        removeCurtain()
        runCatching { unregisterReceiver(launcherShownReceiver) }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeCurtain()
        runCatching { unregisterReceiver(launcherShownReceiver) }
        super.onDestroy()
    }

    companion object {
        /** MainActivity sends this (package-internal) once its window is up, to lift the curtain. */
        const val ACTION_LAUNCHER_SHOWN = "com.nihar.tvlauncher.action.LAUNCHER_SHOWN"

        private const val REDIRECT_DEBOUNCE_MS = 400L

        /** Hard cap on how long the curtain can stay up, even if the signal never arrives. */
        private const val CURTAIN_TIMEOUT_MS = 3000L

        /** Opaque black — matches the launcher's window background, so there's no seam. */
        private const val CURTAIN_COLOR = 0xFF000000.toInt()

        /** Stock Google TV / Android TV home packages to cover. */
        private val STOCK_LAUNCHERS = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
        )
    }
}
