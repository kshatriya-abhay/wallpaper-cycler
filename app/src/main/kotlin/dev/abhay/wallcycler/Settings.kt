package dev.abhay.wallcycler

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

enum class Frequency(val hours: Long, val label: String) {
    TwelveHours(12, "12 hours"),
    OneDay(24, "1 day"),
    SevenDays(24 * 7, "7 days"),
}

/** The chosen folder, how often to change, and which image is the wallpaper now. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var folder: Uri?
        get() = prefs.getString(FOLDER, null)?.toUri()
        set(value) = prefs.edit { putString(FOLDER, value?.toString()) }

    var frequency: Frequency
        get() = prefs.getString(FREQUENCY, null)
            ?.let { name -> Frequency.entries.firstOrNull { it.name == name } }
            ?: Frequency.OneDay
        set(value) = prefs.edit { putString(FREQUENCY, value.name) }

    /** Whether scheduled changes run. "Change now" works either way. */
    var enabled: Boolean
        get() = prefs.getBoolean(ENABLED, true)
        set(value) = prefs.edit { putBoolean(ENABLED, value) }

    /** The image this app set last, so the next pick can skip it. */
    var currentImage: String?
        get() = prefs.getString(CURRENT_IMAGE, null)
        set(value) = prefs.edit { putString(CURRENT_IMAGE, value) }

    private companion object {
        const val FOLDER = "folder"
        const val FREQUENCY = "frequency"
        const val ENABLED = "enabled"
        const val CURRENT_IMAGE = "current_image"
    }
}
