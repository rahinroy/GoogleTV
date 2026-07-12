package com.nihar.tvlauncher

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Elms Sans — a utilitarian geometric sans (SIL Open Font License), bundled as a variable
 * TTF at res/font/elms_sans.ttf. Used for the desktop overlays (clock, photo place/date).
 *
 * It's a *variable* font, and Compose caches a resource font's typeface by resource id —
 * so registering several `FontWeight`s that all point at the one .ttf collides in the
 * cache and every weight renders the same (thin) instance. To get a heavy weight reliably
 * we bake the `wght` axis into a separate font resource (res/font/elms_sans_black.xml,
 * weight 900) which has its own resource id. [ElmsSansBlack] is that heavy family; use it
 * wherever the overlay text needs to read boldly over a bright photo.
 */
val ElmsSansBlack = FontFamily(Font(R.font.elms_sans_black, weight = FontWeight.Black))
