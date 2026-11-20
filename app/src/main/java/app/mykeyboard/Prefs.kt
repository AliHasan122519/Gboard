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

    /** Font library filter: "all", "fav", "mine" or a group id (modern, kufi, naskh, ...). */
    open var fontFilter: String
        get() = sp.getString(K_FILTER, "all") ?: "all"
        set(v) = sp.edit().putString(K_FILTER, v).apply()

    open var favoriteFonts: Set<String>
        get() = (sp.getString(K_FAVS, "") ?: "").split('\n').filter { it.isNotEmpty() }.toSet()
        set(v) = sp.edit().putString(K_FAVS, v.joinToString("\n")).apply()

    var autoUpdate: Boolean
        get() = sp.getBoolean(K_AUTO_UPDATE, true)
        set(v) = sp.edit().putBoolean(K_AUTO_UPDATE, v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong(K_LAST_CHECK, 0L)
        set(v) = sp.edit().putLong(K_LAST_CHECK, v).apply()

    /** JSON of the newest release that is newer than the installed version, or null. */
    var availableUpdate: String?
        get() = sp.getString(K_AVAILABLE, null)
        set(v) = sp.edit().putString(K_AVAILABLE, v).apply()

    var lastInstallError: String
        get() = sp.getString(K_INSTALL_ERR, "") ?: ""
        set(v) = sp.edit().putString(K_INSTALL_ERR, v).apply()

    var suggestions: Boolean
        get() = sp.getBoolean(K_SUGGEST, true)
        set(v) = sp.edit().putBoolean(K_SUGGEST, v).apply()

    var autocorrect: Boolean
        get() = sp.getBoolean(K_AUTOCORRECT, true)
        set(v) = sp.edit().putBoolean(K_AUTOCORRECT, v).apply()

    /** Active Unicode text style id ("none" = off). */
    open var textStyle: String
        get() = sp.getString(K_STYLE, "none") ?: "none"
        set(v) = sp.edit().putString(K_STYLE, v).apply()

    open var imagePreset: Int
        get() = sp.getInt(K_IMG_PRESET, 0).coerceIn(0, 3)
        set(v) = sp.edit().putInt(K_IMG_PRESET, v).apply()

    /** When on, Enter in an app that accepts images sends the message as a picture drawn in the chosen font. */
    open var imageSend: Boolean
        get() = sp.getBoolean(K_IMAGE_SEND, false)
        set(v) = sp.edit().putBoolean(K_IMAGE_SEND, v).apply()

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
        const val K_IMAGE_SEND = "image_send"
        const val K_VOICE_TEXT = "voice_text"
        const val K_VOICE_TS = "voice_ts"
        const val K_SUGGEST = "suggestions"
        const val K_AUTOCORRECT = "autocorrect"
        const val K_STYLE = "text_style"
        const val K_IMG_PRESET = "image_preset"
        const val K_MIC_GRANT = "mic_granted"
        const val K_LEARNED = "learned_words"
        const val K_AUTO_UPDATE = "auto_update"
        const val K_LAST_CHECK = "last_update_check"
        const val K_AVAILABLE = "available_update"
        const val K_INSTALL_ERR = "install_error"
        const val K_FILTER = "font_filter"
        const val K_FAVS = "font_favs"
        const val K_RECENT = "recent_emoji"
    }
}
