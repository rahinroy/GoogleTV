package com.nihar.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

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
 * The service only receives events from the launcher packages (restricted via
 * android:packageNames in res/xml/home_redirect_service.xml), so it stays cheap.
 */
class HomeRedirectService : AccessibilityService() {

    private var lastRedirectAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in STOCK_LAUNCHERS) return

        // A single Home press emits several window events; collapse them.
        val now = SystemClock.uptimeMillis()
        if (now - lastRedirectAt < REDIRECT_DEBOUNCE_MS) return
        lastRedirectAt = now

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() { /* nothing to clean up */ }

    companion object {
        private const val REDIRECT_DEBOUNCE_MS = 400L

        /** Stock Google TV / Android TV home packages to cover. */
        private val STOCK_LAUNCHERS = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
        )
    }
}
