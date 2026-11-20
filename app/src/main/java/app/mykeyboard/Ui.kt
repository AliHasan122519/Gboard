package app.mykeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/** Tiny programmatic-UI helpers shared by the keyboard panels. */
object Ui {
    fun dp(c: Context, v: Float) = v * c.resources.displayMetrics.density
    fun dpi(c: Context, v: Float) = (v * c.resources.displayMetrics.density).toInt()

    fun round(color: Int, radius: Float, strokeColor: Int = 0, strokeW: Float = 0f) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (strokeW > 0f) setStroke(strokeW.toInt(), strokeColor)
    }

    /** Dim a view while pressed (works on any background, no ripple resources needed). */
    fun pressable(v: View, onClick: () -> Unit, onLong: (() -> Boolean)? = null) {
        v.isClickable = true
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.alpha = 0.7f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = 1f
            }
            false
        }
        v.setOnClickListener { onClick() }
        if (onLong != null) v.setOnLongClickListener { onLong() }
    }

    fun text(c: Context, s: CharSequence, sizeSp: Float, color: Int, gravity: Int = Gravity.CENTER) = TextView(c).apply {
        text = s; textSize = sizeSp; setTextColor(color); this.gravity = gravity
        includeFontPadding = false
    }

    fun pill(c: Context, s: CharSequence, bg: Int, fg: Int, sizeSp: Float = 14f): TextView = text(c, s, sizeSp, fg).apply {
        background = round(bg, dp(c, 20f))
        setPadding(dpi(c, 14f), dpi(c, 8f), dpi(c, 14f), dpi(c, 8f))
        minHeight = dpi(c, 36f)
    }

    fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}

class IconView(context: Context, var icon: Icon, var color: Int, var rtl: Boolean = false) : View(context) {
    override fun onDraw(c: Canvas) {
        val s = min(width, height) * 0.6f
        Icons.draw(c, icon, width / 2f, height / 2f, s, color, rtl)
    }
}
