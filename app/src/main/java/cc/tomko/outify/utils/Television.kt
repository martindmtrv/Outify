package cc.tomko.outify.utils

import android.content.Context
import android.content.pm.PackageManager

/**
 * Whether the app runs on an Android TV device, where it acts as a
 * Spotify Connect receiver controlled from another Spotify client.
 */
fun Context.isTelevision(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
