package app.mykeyboard

import android.content.Context
import android.content.res.Configuration

/** Colours of the keyboard. The dark values are sampled from the reference design. */
class Palette(
    val dark: Boolean,
    val background: Int,
    val key: Int,
    val keyPressed: Int,
    val funcKey: Int,
    val funcKeyPressed: Int,
    val label: Int,
    val hint: Int,
    val funcLabel: Int,
    val accent: Int,
    val onAccent: Int,
    val popup: Int,
    val popupSelected: Int,
    val divider: Int,
) {
    companion object {
        val DARK = Palette(
            dark = true,
            background = 0xFF1C1E2A.toInt(),
            key = 0xFF363844.toInt(),
            keyPressed = 0xFF4A4C5C.toInt(),
            funcKey = 0xFF434365.toInt(),
            funcKeyPressed = 0xFF56567F.toInt(),
            label = 0xFFE1E3F2.toInt(),
            hint = 0xFFC3C6D7.toInt(),
            funcLabel = 0xFFE2E1FF.toInt(),
            accent = 0xFFBEC2FF.toInt(),
            onAccent = 0xFF1D1F4A.toInt(),
            popup = 0xFF454759.toInt(),
            popupSelected = 0xFFBEC2FF.toInt(),
            divider = 0x33C3C6D7,
        )
        val LIGHT = Palette(
            dark = false,
            background = 0xFFF1F0F7.toInt(),
            key = 0xFFFFFFFF.toInt(),
            keyPressed = 0xFFDDDCE6.toInt(),
            funcKey = 0xFFDCDDF5.toInt(),
            funcKeyPressed = 0xFFC8CAEC.toInt(),
            label = 0xFF1B1B21.toInt(),
            hint = 0xFF5B5D72.toInt(),
            funcLabel = 0xFF2F3167.toInt(),
            accent = 0xFF4A4DB0.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            popup = 0xFFFFFFFF.toInt(),
            popupSelected = 0xFF4A4DB0.toInt(),
            divider = 0x33454659,
        )

        fun resolve(context: Context, pref: String): Palette = when (pref) {
            "dark" -> DARK
            "light" -> LIGHT
            else -> {
                val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (night == Configuration.UI_MODE_NIGHT_NO) LIGHT else DARK
            }
        }
    }
}
