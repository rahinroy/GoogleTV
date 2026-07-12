package com.nihar.tvlauncher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nihar.tvlauncher.screensaver.ImageManifestRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Wallpaper slideshow on/off (kept as a flag for quick perf isolation).
private const val WALLPAPER_ENABLED = true

/**
 * Desktop-style launcher: a full-screen photo slideshow "wallpaper" with a floating
 * rounded dock of apps along the bottom that scrolls horizontally like a belt.
 */
@Composable
fun LauncherScreen(
    loadApps: suspend () -> List<AppEntry>,
    photoIntervalSeconds: Int,
    onAppSelected: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = loadApps() }

    // Hoisted so the wallpaper can pause its crossfade while the dock is scrolling —
    // two heavy full-screen operations must never overlap.
    val dockScroll = rememberScrollState()

    // EXIF place/date for the currently-displayed wallpaper photo (shown top-right).
    val context = LocalContext.current
    var currentPhoto by remember { mutableStateOf<String?>(null) }
    var photoInfo by remember { mutableStateOf(PhotoInfo(null, null)) }
    LaunchedEffect(currentPhoto) {
        photoInfo = currentPhoto?.let { readPhotoInfo(context, it) } ?: PhotoInfo(null, null)
    }

    LauncherTheme {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)) {

            // TEMP diagnostic: flip to false to test whether the wallpaper is the
            // source of scroll jank. Solid background instead of the photo slideshow.
            if (WALLPAPER_ENABLED) {
                WallpaperSlideshow(
                    intervalMs = photoIntervalSeconds * 1_000L,
                    busy = { dockScroll.isScrollInProgress },
                    onCurrentModel = { currentPhoto = it },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF14171C)))
            }

            // Darken only the bottom band so the dock reads clearly over any photo —
            // a full-screen scrim would blend over the whole wallpaper (extra overdraw).
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.45f)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color(0xAA000000),
                        )
                    )
            )

            ClockOverlay(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp, top = 36.dp),
            )

            PhotoInfoOverlay(
                info = photoInfo,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 48.dp, top = 40.dp),
            )

            Dock(
                apps = apps,
                onAppSelected = onAppSelected,
                onOpenSettings = onOpenSettings,
                scrollState = dockScroll,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.20f),
            )
        }
    }
}

/**
 * Full-screen crossfading photo background — Compose-native (Coil) rather than an
 * embedded Android View, so the whole frame renders in a single pass and there's no
 * View↔Compose cross-compositing cost while the dock scrolls over it.
 */
@Composable
private fun WallpaperSlideshow(
    intervalMs: Long,
    busy: () -> Boolean,
    onCurrentModel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { models = ImageManifestRepository.resolveModels(context) }

    if (models.isEmpty()) {
        Box(modifier.background(Color(0xFF14171C)))
        return
    }

    var index by remember { mutableStateOf(0) }
    LaunchedEffect(index, models) { onCurrentModel(models[index % models.size]) }
    LaunchedEffect(models, intervalMs) {
        while (true) {
            delay(intervalMs)
            // Never crossfade (a heavy full-screen blend) while the dock is scrolling.
            while (busy()) delay(300)
            index = (index + 1) % models.size
        }
    }

    Crossfade(
        targetState = models[index % models.size],
        animationSpec = tween(durationMillis = 1_200),
        label = "wallpaper",
        modifier = modifier,
    ) { model ->
        AsyncImage(
            model = ImageRequest.Builder(context).data(model).crossfade(false).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Low-opacity clock — readable but recedes behind the photo. */
@Composable
private fun ClockOverlay(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(currentClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            text = currentClock()
            delay(10_000)
        }
    }
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.95f),
        fontFamily = ElmsSansBlack,
        fontSize = 52.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier,
    )
}

private fun currentClock(): String =
    java.time.LocalTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))

/** Where/when the current photo was taken (top-right, smaller than the clock). */
@Composable
private fun PhotoInfoOverlay(info: PhotoInfo, modifier: Modifier = Modifier) {
    if (!info.hasAny) return
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        info.place?.let {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.95f),
                fontFamily = ElmsSansBlack,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        info.date?.let {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = ElmsSansBlack,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

// Corner radius shared by the tiles and their focus ring (kept equal so there's no gap).
private val TileCorner = RoundedCornerShape(16.dp)

/** The floating app belt. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(
    apps: List<AppEntry>,
    onAppSelected: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val firstFocus = remember { FocusRequester() }
    val lastFocus = remember { FocusRequester() }

    // Draw every tile once (sweep the belt) so each has built its display list before the
    // first real scroll. With all tiles composed up front (plain Row) there's no
    // composition mid-scroll, so this only warms first-draw. Runs on load + each resume.
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            warmUpBelt(scrollState)
            requestFocusWithRetry(firstFocus)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, apps) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && apps.isNotEmpty()) {
                scope.launch {
                    focusManager.clearFocus(force = true)
                    warmUpBelt(scrollState)
                    requestFocusWithRetry(firstFocus)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Center the focused tile and glide the belt under it with a snappy spring, so the
    // scroll keeps up with rapid D-pad presses instead of trailing behind.
    val pivotSpec = remember {
        object : BringIntoViewSpec {
            // Soft spring: rapid presses interrupt it before it settles, so consecutive
            // one-tile scrolls chain into one continuous glide instead of snapping to
            // each tile. (Stiffer springs settle between presses -> stop-start jitter.)
            override val scrollAnimationSpec = spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 300f,
            )

            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val pivot = (containerSize - size) / 2f // center the focused tile
                return offset - pivot
            }
        }
    }

    // Wrap-around: DPAD_RIGHT off the last tile jumps to the first, DPAD_LEFT off the
    // first jumps to the last (settings). Instant reposition + focus the now-visible far
    // tile, so the selector never rides a tile off-screen.
    // Animated wrap: just move focus to the far tile — the pivot BringIntoViewSpec then
    // glides the belt to it (soft spring), so the wrap scrolls instead of jumping.
    val firstTileMod = Modifier
        .focusRequester(firstFocus)
        .onKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && apps.isNotEmpty()) {
                lastFocus.requestFocus()
                true
            } else false
        }
    val settingsTileMod = Modifier
        .focusRequester(lastFocus)
        .onKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight && apps.isNotEmpty()) {
                firstFocus.requestFocus()
                true
            } else false
        }

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = 56.dp, end = 56.dp, bottom = 28.dp)
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(28.dp), clip = false)
                // Fully opaque so the compositor occlusion-culls the wallpaper + scrim
                // behind the dock (translucency there would force per-frame blending —
                // the dominant GPU cost during scroll).
                .background(Color(0xFF141821), RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp)),
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
                // Plain Row with all (lightweight) tiles composed up front. Nothing
                // composes mid-scroll, so there are no composition spikes; the tiles are
                // cheap enough that laying them all out per frame stays well under budget.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp)
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 24.dp),
                ) {
                    apps.forEachIndexed { index, app ->
                        DockTile(
                            image = app.image,
                            isBanner = app.isBanner,
                            contentDescription = app.label,
                            onClick = { onAppSelected(app) },
                            modifier = if (index == 0) firstTileMod else Modifier,
                        )
                    }
                    SettingsTile(onClick = onOpenSettings, modifier = settingsTileMod)
                }
            }
        }
    }
}

/**
 * A minimal focusable tile. Deliberately NOT a tv-material3 `Card` — profiling showed
 * the Card's Surface/interaction/indication machinery was the dominant per-frame cost
 * during scroll. This is a plain Box: manual focus state, a `graphicsLayer` scale, and a
 * conditional border. Only the focused/unfocused tile recomposes on a focus change.
 */
@Composable
private fun DockTile(
    image: ImageBitmap,
    isBanner: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TileFrame(
        onClick = onClick,
        aspect = if (isBanner) 16f / 9f else 1f,
        modifier = modifier,
    ) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            contentScale = if (isBanner) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SettingsTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TileFrame(onClick = onClick, aspect = 1f, modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "⚙", color = Color(0x99FFFFFF), fontSize = 28.sp)
        }
    }
}

/** Shared lightweight tile chrome: focus scale + focus ring + rounded clip. */
@Composable
private fun TileFrame(
    onClick: () -> Unit,
    aspect: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.12f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "tileScale",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .aspectRatio(aspect)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(TileCorner)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = TileCorner,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private suspend fun requestFocusWithRetry(fr: FocusRequester) {
    repeat(12) {
        if (runCatching { fr.requestFocus() }.isSuccess) return
        delay(30)
    }
}

/**
 * Steps the belt through every item once so each tile composes + draws at least once —
 * building its display list before the user's first real scroll (no first-pass hitch).
 * We step item-by-item because LazyRow's animateScrollToItem would jump over (never
 * compose) the intermediate tiles for a distant target.
 */
private suspend fun warmUpBelt(scrollState: ScrollState) {
    // Wait until the belt has been laid out (maxValue reflects overflow width).
    var tries = 0
    while (scrollState.maxValue <= 0 && tries < 30) {
        withFrameNanos { }
        tries++
    }
    if (scrollState.maxValue <= 0) return
    scrollState.animateScrollTo(scrollState.maxValue, tween(durationMillis = 420))
    scrollState.animateScrollTo(0, tween(durationMillis = 320))
}
