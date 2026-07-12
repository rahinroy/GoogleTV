package com.nihar.tvlauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private lateinit var repository: AppRepository
    private lateinit var prefs: LauncherPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = AppRepository(applicationContext)
        prefs = LauncherPrefs(applicationContext)

        setContent {
            // Simple two-screen navigation without a nav library.
            var showSettings by remember { mutableStateOf(false) }

            if (showSettings) {
                // The settings flow owns its own Back handling (sub-screen -> menu ->
                // launcher); onExit is Back from the menu, returning to the launcher.
                SettingsScreen(
                    loadAll = { repository.loadAllForSettings(prefs.hidden(), prefs.order()) },
                    initialIntervalSeconds = prefs.photoIntervalSeconds(),
                    onToggleHidden = { pkg, hidden -> prefs.setHidden(pkg, hidden) },
                    onReorder = { order -> prefs.setOrder(order) },
                    onSetIntervalSeconds = { s -> prefs.setPhotoIntervalSeconds(s) },
                    onSetupHomeRedirect = ::openAccessibilitySettings,
                    onExit = { showSettings = false },
                )
            } else {
                // We are the HOME app: swallow BACK so it never exits to a blank screen.
                BackHandler(enabled = true) { /* stay home */ }
                LauncherScreen(
                    loadApps = { repository.loadApps(prefs.hidden(), prefs.order()) },
                    photoIntervalSeconds = prefs.photoIntervalSeconds(),
                    onAppSelected = ::launchApp,
                    onOpenSettings = { showSettings = true },
                )
            }
        }
    }

    /**
     * Open the system Accessibility settings so the user can enable [HomeRedirectService].
     * On this TCL the default-home role doesn't capture the Home button, so that service
     * (watch the stock launcher, launch us over it) is how we become the effective home.
     */
    private fun openAccessibilitySettings() {
        val opened = runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.isSuccess
        if (opened) {
            Toast.makeText(this, "Enable \"Nihar TV — Home button\"", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                this,
                "Turn on \"Nihar TV — Home button\" under Settings → Accessibility",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun launchApp(app: AppEntry) {
        val intent = repository.launchIntentFor(app.packageName)
        if (intent == null) {
            Toast.makeText(this, "Can't launch ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "Failed to launch ${app.label}", Toast.LENGTH_SHORT).show() }
    }
}
