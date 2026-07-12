package com.nihar.tvlauncher.screensaver

import android.service.dreams.DreamService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android's built-in screensaver ("Daydream") hook. The system starts this when
 * the device has been idle; we host a [SlideshowView] and feed it images.
 *
 * Startup is two-phase so there's never a blank wait:
 *   1. Paint the bundled asset photos immediately.
 *   2. If a remote manifest URL is configured, resolve it in the background and
 *      restart the slideshow with the remote list.
 */
class ScreensaverDreamService : DreamService() {

    private var slideshow: SlideshowView? = null
    private var scope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false // a D-pad press wakes the device
        isFullscreen = true   // hide system bars
        isScreenBright = true

        val view = SlideshowView(this)
        slideshow = view
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val s = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = s

        // Phase 1: instant paint from bundled assets.
        val assets = ImageManifestRepository.assetModels(this)
        slideshow?.start(assets)

        // Phase 2: upgrade to the remote manifest if one is configured.
        if (ScreensaverConfig.MANIFEST_URL.isNotBlank()) {
            s.launch {
                val models = ImageManifestRepository.resolveModels(this@ScreensaverDreamService)
                if (models.isNotEmpty() && models != assets) {
                    slideshow?.start(models)
                }
            }
        }
    }

    override fun onDreamingStopped() {
        scope?.cancel()
        scope = null
        slideshow?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        slideshow?.stop()
        slideshow = null
        super.onDetachedFromWindow()
    }
}
