package com.nihar.tvlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Settings, structured as a small menu with two sub-screens:
 *  - "Show / hide apps"  -> every installed app, OK toggles it on/off in the dock.
 *  - "Reorder apps"      -> only the *visible* apps, ◀ ▶ move the selected one.
 *  - "Time between slides" (inline) -> ◀ ▶ adjust the wallpaper interval.
 *  - "Set as default launcher" (inline action).
 *
 * All changes persist immediately. Back on a sub-screen returns to the menu; Back on the
 * menu ([onExit]) returns to the launcher.
 */
private enum class SettingsDest { Menu, ShowHide, Reorder }

@Composable
fun SettingsScreen(
    loadAll: suspend () -> List<SettingsAppRow>,
    initialIntervalSeconds: Int,
    onToggleHidden: (packageName: String, hidden: Boolean) -> Unit,
    onReorder: (order: List<String>) -> Unit,
    onSetIntervalSeconds: (seconds: Int) -> Unit,
    onSetupHomeRedirect: () -> Unit,
    onExit: () -> Unit,
) {
    var rows by remember { mutableStateOf<List<SettingsAppRow>>(emptyList()) }
    var interval by remember { mutableStateOf(initialIntervalSeconds) }
    var dest by remember { mutableStateOf(SettingsDest.Menu) }
    LaunchedEffect(Unit) { rows = loadAll() }

    fun toggle(pkg: String) {
        val row = rows.firstOrNull { it.packageName == pkg } ?: return
        val newHidden = !row.hidden
        onToggleHidden(pkg, newHidden)
        rows = rows.map { if (it.packageName == pkg) it.copy(hidden = newHidden) else it }
    }

    // Swap a visible app with its nearest *visible* neighbour (hidden apps keep their
    // slots), then persist the full package order.
    fun moveVisible(pkg: String, delta: Int) {
        val i = rows.indexOfFirst { it.packageName == pkg }
        if (i < 0) return
        var j = i + delta
        while (j in rows.indices && rows[j].hidden) j += delta   // skip over hidden apps
        if (j !in rows.indices) return
        val updated = rows.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
        rows = updated
        onReorder(updated.map { it.packageName })
    }

    LauncherTheme {
        when (dest) {
            SettingsDest.Menu -> {
                BackHandler(enabled = true) { onExit() }
                SettingsMenu(
                    interval = interval,
                    onIntervalChange = { v ->
                        val c = v.coerceIn(LauncherPrefs.MIN_INTERVAL, LauncherPrefs.MAX_INTERVAL)
                        interval = c
                        onSetIntervalSeconds(c)
                    },
                    onShowHide = { dest = SettingsDest.ShowHide },
                    onReorderApps = { dest = SettingsDest.Reorder },
                    onSetupHomeRedirect = onSetupHomeRedirect,
                )
            }
            SettingsDest.ShowHide -> {
                BackHandler(enabled = true) { dest = SettingsDest.Menu }
                ShowHideScreen(rows = rows, onToggle = ::toggle)
            }
            SettingsDest.Reorder -> {
                BackHandler(enabled = true) { dest = SettingsDest.Menu }
                ReorderScreen(rows = rows.filter { !it.hidden }, onMove = ::moveVisible)
            }
        }
    }
}

// ---- Menu -----------------------------------------------------------------------------

@Composable
private fun SettingsMenu(
    interval: Int,
    onIntervalChange: (Int) -> Unit,
    onShowHide: () -> Unit,
    onReorderApps: () -> Unit,
    onSetupHomeRedirect: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusWithRetry(firstFocus) }

    SettingsColumn(title = "Launcher settings", subtitle = "Back to return") {
        NavRow(
            title = "Show / hide apps",
            subtitle = "Choose which apps appear in the dock",
            onClick = onShowHide,
            modifier = Modifier.focusRequester(firstFocus),
        )
        NavRow(
            title = "Reorder apps",
            subtitle = "Arrange the order of your visible apps",
            onClick = onReorderApps,
        )
        IntervalRow(seconds = interval, onChange = onIntervalChange)
        NavRow(
            title = "Use as home screen",
            subtitle = "Turn on the Home-button redirect (Accessibility)",
            onClick = onSetupHomeRedirect,
        )
    }
}

// ---- Sub-screens ----------------------------------------------------------------------

@Composable
private fun ShowHideScreen(rows: List<SettingsAppRow>, onToggle: (String) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(rows.isNotEmpty()) { if (rows.isNotEmpty()) focusWithRetry(firstFocus) }

    SettingsColumn(title = "Show / hide apps", subtitle = "OK toggles an app on or off  •  Back to return") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            itemsIndexed(rows, key = { _, r -> r.packageName }) { index, row ->
                AppRow(
                    row = row,
                    onClick = { onToggle(row.packageName) },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    trailing = { focused ->
                        Text(
                            text = if (row.hidden) "Hidden" else "Shown",
                            color = rowTitleColor(focused).copy(alpha = if (row.hidden) 0.6f else 1f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReorderScreen(rows: List<SettingsAppRow>, onMove: (String, Int) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(rows.isNotEmpty()) { if (rows.isNotEmpty()) focusWithRetry(firstFocus) }

    SettingsColumn(title = "Reorder apps", subtitle = "◀ ▶ move the selected app  •  Back to return") {
        if (rows.isEmpty()) {
            Text(
                text = "No visible apps to reorder — un-hide some under \"Show / hide apps\".",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@SettingsColumn
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            itemsIndexed(rows, key = { _, r -> r.packageName }) { index, row ->
                AppRow(
                    row = row,
                    onClick = {},
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onKey = { e ->
                        if (e.type == KeyEventType.KeyDown) when (e.key) {
                            Key.DirectionLeft -> { onMove(row.packageName, -1); true }
                            Key.DirectionRight -> { onMove(row.packageName, 1); true }
                            else -> false
                        } else false
                    },
                    trailing = { focused ->
                        Text(
                            text = "◀ ▶",
                            color = rowTitleColor(focused),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                )
            }
        }
    }
}

// ---- Shared pieces --------------------------------------------------------------------

/** Screen chrome: dark surface, title, subtitle, then the caller's content. */
@Composable
private fun SettingsColumn(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        content()
    }
}

/** A menu row that navigates / triggers an action (with a ▶ affordance). */
@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSettingRow(onClick = onClick, modifier = modifier) { focused ->
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = rowTitleColor(focused), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = rowSubColor(focused), fontSize = 12.sp)
        }
        Text("▶", color = rowTitleColor(focused), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Inline photo-interval stepper (◀ ▶). */
@Composable
private fun IntervalRow(seconds: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    FocusableSettingRow(
        onClick = {},
        modifier = modifier,
        onKey = { e ->
            if (e.type == KeyEventType.KeyDown) when (e.key) {
                Key.DirectionLeft -> { onChange(seconds - LauncherPrefs.INTERVAL_STEP); true }
                Key.DirectionRight -> { onChange(seconds + LauncherPrefs.INTERVAL_STEP); true }
                else -> false
            } else false
        },
    ) { focused ->
        Column(modifier = Modifier.weight(1f)) {
            Text("Time between slides", color = rowTitleColor(focused), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "How long each wallpaper photo shows  •  ◀ ▶ to adjust",
                color = rowSubColor(focused),
                fontSize = 12.sp,
            )
        }
        Text("${seconds}s", color = rowTitleColor(focused), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** An app row: icon + label + package + a caller-supplied trailing widget. */
@Composable
private fun AppRow(
    row: SettingsAppRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onKey: ((KeyEvent) -> Boolean)? = null,
    trailing: @Composable (focused: Boolean) -> Unit,
) {
    FocusableSettingRow(onClick = onClick, modifier = modifier, onKey = onKey) { focused ->
        Image(
            bitmap = row.icon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .alpha(if (row.hidden) 0.5f else 1f),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = row.label,
                color = rowTitleColor(focused).copy(alpha = if (row.hidden) 0.6f else 1f),
                fontSize = 16.sp,
                maxLines = 1,
            )
            Text(
                text = row.packageName + if (row.isTvApp) "  •  TV app" else "",
                color = rowSubColor(focused),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        trailing(focused)
    }
}

/**
 * A focusable settings row. When focused the container turns white; we therefore switch
 * the text to dark ([rowTitleColor]/[rowSubColor] key off [focused]) so it stays readable
 * — the previous version kept light text on the white highlight, which vanished.
 */
@Composable
private fun FocusableSettingRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onKey: ((KeyEvent) -> Boolean)? = null,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White,
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(if (onKey != null) Modifier.onKeyEvent(onKey) else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            content(focused)
        }
    }
}

@Composable
private fun rowTitleColor(focused: Boolean): Color =
    if (focused) Color(0xFF0E1013) else MaterialTheme.colorScheme.onSurface

@Composable
private fun rowSubColor(focused: Boolean): Color =
    if (focused) Color(0xFF3A3F45) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

private suspend fun focusWithRetry(fr: FocusRequester) {
    repeat(20) {
        if (runCatching { fr.requestFocus() }.isSuccess) return
        delay(40)
    }
}
