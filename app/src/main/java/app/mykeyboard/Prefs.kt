package app.mykeyboard

import android.content.Context
import android.content.SharedPreferences

/** All user settings in one place. Backed by SharedPreferences, shared by the IME and the settings screen. */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var theme: String
        get() = sp.getString(K_THEME, "auto") ?: "auto"
        set(v) = sp.edit().putString(K_THEME, v).apply()

    /** Key height in percent of the default (Gboard-like) height. */
    var heightPercent: Int
        get() = sp.getInt(K_HEIGHT, 100).coerceIn(80, 125)
        set(v) = sp.edit().putInt(K_HEIGHT, v).apply()

    var haptic: Boolean
        get() = sp.getBoolean(K_HAPTIC, true)
        set(v) = sp.edit().putBoolean(K_HAPTIC, v).apply()

    var sound: Boolean
        get() = sp.getBoolean(K_SOUND, false)
        set(v) = sp.edit().putBoolean(K_SOUND, v).apply()

    var keyPopup: Boolean
        get() = sp.getBoolean(K_POPUP, true)
        set(v) = sp.edit().putBoolean(K_POPUP, v).apply()

    /** true = Arabic-Indic digits (٠١٢…) on the Arabic layout, false = Western digits. */
    var arabicDigits: Boolean
        get() = sp.getBoolean(K_DIGITS, true)
        set(v) = sp.edit().putBoolean(K_DIGITS, v).apply()

    var autoCap: Boolean
        get() = sp.getBoolean(K_AUTOCAP, true)
        set(v) = sp.edit().putBoolean(K_AUTOCAP, v).apply()

    /** "ar" or "en". */
    var lang: String
        get() = sp.getString(K_LANG, "ar") ?: "ar"
        set(v) = sp.edit().putString(K_LANG, v).apply()

    var fontId: String
        get() = sp.getString(K_FONT, FontRepository.DEFAULT_ID) ?: FontRepository.DEFAULT_ID
        set(v) = sp.edit().putString(K_FONT, v).apply()

    var fontWeight: Int
        get() = sp.getInt(K_WEIGHT, 400)
        set(v) = sp.edit().putInt(K_WEIGHT, v).apply()

    var clipboardHistory: Boolean
        get() = sp.getBoolean(K_CLIP, true)
        set(v) = sp.edit().putBoolean(K_CLIP, v).apply()

    var recentEmoji: List<String>
        get() = (sp.getString(K_RECENT, "") ?: "").split(' ').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString(K_RECENT, v.joinToString(" ")).apply()

    companion object {
        const val NAME = "settings"
        const val K_THEME = "theme"
        const val K_HEIGHT = "height"
        const val K_HAPTIC = "haptic"
        const val K_SOUND = "sound"
        const val K_POPUP = "popup"
        const val K_DIGITS = "digits"
        const val K_AUTOCAP = "autocap"
        const val K_LANG = "lang"
        const val K_FONT = "font"
        const val K_WEIGHT = "weight"
        const val K_CLIP = "clip"
        const val K_RECENT = "recent_emoji"
    }
}
