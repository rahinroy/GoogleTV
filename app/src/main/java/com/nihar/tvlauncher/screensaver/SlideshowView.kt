package com.nihar.tvlauncher.screensaver

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A crossfading photo slideshow backed by two [ImageView]s.
 *
 * Images are supplied as Coil "models" — either bundled asset URIs
 * (`file:///android_asset/...`) or remote `http(s)` URLs. Coil downsamples each to
 * the view size and disk-caches remote images, so we never hold full-resolution
 * multi-megapixel bitmaps and remote photos survive offline restarts.
 */
class SlideshowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val back = imageView()
    private val front = imageView()
    private val imageLoader = ImageLoader.Builder(context).build()

    private var scope: CoroutineScope? = null
    private var frontVisible = false // back is the initially-visible layer

    private val holdMillis = 8_000L
    private val fadeMillis = 1_200L

    init {
        setBackgroundColor(Color.BLACK)
        addView(back)
        addView(front)
        front.alpha = 0f
    }

    private fun imageView() = ImageView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    /** Begin (or restart) the slideshow over [models]. Safe to call repeatedly. */
    fun start(models: List<String>) {
        stop()
        if (models.isEmpty()) return
        val s = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = s
        s.launch { runSlideshow(models.shuffled()) }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        back.setImageDrawable(null)
        front.setImageDrawable(null)
    }

    private suspend fun runSlideshow(order: List<String>) {
        val s = scope ?: return
        var index = 0

        // Prime the visible (back) layer with the first image, no fade.
        loadInto(back, order[index])

        while (s.isActive) {
            delay(holdMillis)
            if (order.size == 1) continue

            index = (index + 1) % order.size
            val incoming = if (frontVisible) back else front
            val outgoing = if (frontVisible) front else back

            if (!loadInto(incoming, order[index])) continue

            incoming.alpha = 0f
            incoming.animate().alpha(1f).setDuration(fadeMillis).start()
            outgoing.animate().alpha(0f).setDuration(fadeMillis).start()
            frontVisible = !frontVisible
        }
    }

    /** Load [model] into [view] downsampled to the view size. Returns success. */
    private suspend fun loadInto(view: ImageView, model: String): Boolean {
        val targetW = width.takeIf { it > 0 } ?: 1920
        val targetH = height.takeIf { it > 0 } ?: 1080
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(targetW, targetH)
            .build()
        val result = imageLoader.execute(request)
        val drawable = (result as? SuccessResult)?.drawable ?: return false
        view.setImageDrawable(drawable)
        return true
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
