package com.nihar.tvlauncher.screensaver

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves the list of screensaver image "models" (strings Coil can load).
 *
 * Precedence:
 *   1. Remote manifest at [ScreensaverConfig.MANIFEST_URL], if set and reachable.
 *   2. Last-cached remote manifest (so it still works offline).
 *   3. Photos bundled in `assets/screensaver/`.
 *
 * Bundled assets are returned as `file:///android_asset/...` URIs; remote images
 * as their http(s) URLs. Coil loads and disk-caches both.
 */
object ImageManifestRepository {

    suspend fun resolveModels(context: Context): List<String> = withContext(Dispatchers.IO) {
        val assets = assetModels(context)
        val url = ScreensaverConfig.MANIFEST_URL
        if (url.isBlank()) return@withContext assets

        val remote = runCatching { fetchManifest(url) }.getOrNull()
        if (remote != null) {
            cacheManifest(context, remote)
            return@withContext remote.ifEmpty { assets }
        }
        // Network failed — fall back to the last good manifest, then to assets.
        readCachedManifest(context).ifEmpty { assets }
    }

    /** Fast, local-only list of the bundled photos. Used for instant first paint. */
    fun assetModels(context: Context): List<String> =
        context.assets.list(ScreensaverConfig.ASSET_DIR).orEmpty()
            .filter { it.isImageName() }
            .sorted()
            .map { "file:///android_asset/${ScreensaverConfig.ASSET_DIR}/$it" }

    private fun fetchManifest(url: String): List<String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode !in 200..299) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(json: String): List<String> {
        val trimmed = json.trim()
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed).optJSONArray("images") ?: JSONArray()
        }
        return buildList {
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun cacheFile(context: Context) = File(context.filesDir, ScreensaverConfig.CACHE_FILE)

    private fun cacheManifest(context: Context, models: List<String>) {
        runCatching {
            cacheFile(context).writeText(JSONArray(models).toString())
        }
    }

    private fun readCachedManifest(context: Context): List<String> {
        val file = cacheFile(context)
        if (!file.exists()) return emptyList()
        return runCatching { parse(file.readText()) }.getOrDefault(emptyList())
    }

    private fun String.isImageName(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
    }
}
