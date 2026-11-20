package app.mykeyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

enum class Icon {
    MIC, GEAR, PALETTE, CLIPBOARD, STICKER, GRID, GLOBE, BACKSPACE, SHIFT, SHIFT_FILLED, SHIFT_LOCK,
    ENTER, SEARCH, SEND, CHECK, ARROW_NEXT, SMILEY, BACK, CLOSE, PLUS, PIN, KEYBOARD, MOON, TRASH, ARROW_BACK_LABEL
}

/** Vector icons drawn directly on a Canvas, original geometry in a unit square centred on (cx, cy). */
object Icons {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private fun stroke(sw: Float, color: Int) = paint.apply {
        style = Paint.Style.STROKE; strokeWidth = sw; color.let { this.color = it }
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private fun fill(color: Int) = paint.apply { style = Paint.Style.FILL; this.color = color }

    fun draw(c: Canvas, icon: Icon, cx: Float, cy: Float, size: Float, color: Int, rtl: Boolean = false) {
        val s = size
        val sw = s * 0.085f
        c.save()
        c.translate(cx, cy)
        when (icon) {
            Icon.MIC -> {
                rect.set(-0.17f * s, -0.42f * s, 0.17f * s, 0.12f * s)
                fill(color); c.drawRoundRect(rect, 0.17f * s, 0.17f * s, paint)
                stroke(sw, color)
                rect.set(-0.30f * s, -0.18f * s, 0.30f * s, 0.30f * s)
                c.drawArc(rect, 0f, 180f, false, paint)
                c.drawLine(0f, 0.30f * s, 0f, 0.44f * s, paint)
                c.drawLine(-0.13f * s, 0.44f * s, 0.13f * s, 0.44f * s, paint)
            }
            Icon.GEAR -> {
                path.reset()
                path.addCircle(0f, 0f, 0.33f * s, Path.Direction.CW)
                val tooth = Path()
                for (i in 0 until 8) {
                    tooth.reset()
                    rect.set(-0.095f * s, -0.47f * s, 0.095f * s, -0.22f * s)
                    tooth.addRoundRect(rect, 0.04f * s, 0.04f * s, Path.Direction.CW)
                    val m = android.graphics.Matrix(); m.postRotate(i * 45f)
                    tooth.transform(m)
                    path.op(tooth, Path.Op.UNION)
                }
                val hole = Path(); hole.addCircle(0f, 0f, 0.14f * s, Path.Direction.CW)
                path.op(hole, Path.Op.DIFFERENCE)
                fill(color); c.drawPath(path, paint)
            }
            Icon.PALETTE -> {
                stroke(sw, color)
                path.reset()
                path.moveTo(0.02f * s, -0.44f * s)
                path.cubicTo(-0.36f * s, -0.44f * s, -0.48f * s, -0.10f * s, -0.45f * s, 0.12f * s)
                path.cubicTo(-0.40f * s, 0.40f * s, -0.12f * s, 0.46f * s, 0.10f * s, 0.44f * s)
                path.cubicTo(0.22f * s, 0.43f * s, 0.20f * s, 0.30f * s, 0.14f * s, 0.22f * s)
                path.cubicTo(0.08f * s, 0.12f * s, 0.14f * s, 0.06f * s, 0.24f * s, 0.06f * s)
                path.cubicTo(0.38f * s, 0.06f * s, 0.46f * s, 0.0f, 0.46f * s, -0.10f * s)
                path.cubicTo(0.46f * s, -0.30f * s, 0.30f * s, -0.44f * s, 0.02f * s, -0.44f * s)
                c.drawPath(path, paint)
                fill(color)
                for ((dx, dy) in listOf(-0.26f to -0.12f, -0.10f to -0.28f, 0.12f to -0.28f, -0.28f to 0.10f)) {
                    c.drawCircle(dx * s, dy * s, 0.06f * s, paint)
                }
            }
            Icon.CLIPBOARD -> {
                stroke(sw, color)
                rect.set(-0.30f * s, -0.36f * s, 0.30f * s, 0.46f * s)
                c.drawRoundRect(rect, 0.07f * s, 0.07f * s, paint)
                fill(color)
                rect.set(-0.14f * s, -0.46f * s, 0.14f * s, -0.26f * s)
                c.drawRoundRect(rect, 0.06f * s, 0.06f * s, paint)
                stroke(sw * 0.9f, color)
                c.drawLine(-0.16f * s, -0.04f * s, 0.16f * s, -0.04f * s, paint)
                c.drawLine(-0.16f * s, 0.12f * s, 0.16f * s, 0.12f * s, paint)
                c.drawLine(-0.16f * s, 0.28f * s, 0.16f * s, 0.28f * s, paint)
            }
            Icon.STICKER -> {
                stroke(sw, color)
                path.reset()
                path.moveTo(-0.34f * s, -0.42f * s)
                path.lineTo(0.34f * s, -0.42f * s)
                path.quadTo(0.44f * s, -0.42f * s, 0.44f * s, -0.32f * s)
                path.lineTo(0.44f * s, 0.10f * s)
                path.lineTo(0.10f * s, 0.44f * s)
                path.lineTo(-0.34f * s, 0.44f * s)
                path.quadTo(-0.44f * s, 0.44f * s, -0.44f * s, 0.34f * s)
                path.lineTo(-0.44f * s, -0.32f * s)
                path.quadTo(-0.44f * s, -0.42f * s, -0.34f * s, -0.42f * s)
                path.close()
                c.drawPath(path, paint)
                path.reset()
                path.moveTo(0.42f * s, 0.12f * s); path.lineTo(0.14f * s, 0.12f * s); path.quadTo(0.10f * s, 0.12f * s, 0.10f * s, 0.16f * s); path.lineTo(0.10f * s, 0.42f * s)
                c.drawPath(path, paint)
                fill(color)
                c.drawCircle(-0.16f * s, -0.12f * s, 0.045f * s, paint)
                c.drawCircle(0.10f * s, -0.12f * s, 0.045f * s, paint)
                stroke(sw * 0.9f, color)
                rect.set(-0.18f * s, -0.04f * s, 0.14f * s, 0.2f * s)
                c.drawArc(rect, 15f, 150f, false, paint)
            }
            Icon.GRID -> {
                fill(color)
                val q = 0.40f * s; val r = 0.11f * s
                rect.set(-0.46f * s, -0.46f * s, -0.46f * s + q, -0.46f * s + q); c.drawRoundRect(rect, r, r, paint)
                rect.set(0.06f * s, -0.46f * s, 0.06f * s + q, -0.46f * s + q); c.drawRoundRect(rect, r, r, paint)
                rect.set(-0.46f * s, 0.06f * s, -0.46f * s + q, 0.06f * s + q); c.drawRoundRect(rect, r, r, paint)
                rect.set(0.06f * s, 0.06f * s, 0.06f * s + q, 0.06f * s + q); c.drawRoundRect(rect, r, r, paint)
            }
            Icon.GLOBE -> {
                stroke(sw, color)
                c.drawCircle(0f, 0f, 0.44f * s, paint)
                rect.set(-0.20f * s, -0.44f * s, 0.20f * s, 0.44f * s); c.drawOval(rect, paint)
                c.drawLine(-0.44f * s, 0f, 0.44f * s, 0f, paint)
                c.drawLine(0f, -0.44f * s, 0f, 0.44f * s, paint)
                rect.set(-0.40f * s, -0.28f * s, 0.40f * s, 0.28f * s)
                c.drawArc(rect, 0f, 0f, false, paint)
                c.drawLine(-0.38f * s, -0.22f * s, 0.38f * s, -0.22f * s, paint)
                c.drawLine(-0.38f * s, 0.22f * s, 0.38f * s, 0.22f * s, paint)
            }
            Icon.BACKSPACE -> {
                stroke(sw, color)
                path.reset()
                path.moveTo(-0.50f * s, 0f)
                path.lineTo(-0.22f * s, -0.34f * s)
                path.lineTo(0.38f * s, -0.34f * s)
                path.quadTo(0.50f * s, -0.34f * s, 0.50f * s, -0.22f * s)
                path.lineTo(0.50f * s, 0.22f * s)
                path.quadTo(0.50f * s, 0.34f * s, 0.38f * s, 0.34f * s)
                path.lineTo(-0.22f * s, 0.34f * s)
                path.close()
                c.drawPath(path, paint)
                c.drawLine(-0.02f * s, -0.12f * s, 0.26f * s, 0.12f * s, paint)
                c.drawLine(0.26f * s, -0.12f * s, -0.02f * s, 0.12f * s, paint)
            }
            Icon.SHIFT, Icon.SHIFT_FILLED, Icon.SHIFT_LOCK -> {
                path.reset()
                path.moveTo(0f, -0.46f * s)
                path.lineTo(0.46f * s, 0.02f * s)
                path.lineTo(0.20f * s, 0.02f * s)
                path.lineTo(0.20f * s, 0.30f * s)
                path.lineTo(-0.20f * s, 0.30f * s)
                path.lineTo(-0.20f * s, 0.02f * s)
                path.lineTo(-0.46f * s, 0.02f * s)
                path.close()
                if (icon == Icon.SHIFT) { stroke(sw, color); c.drawPath(path, paint) } else { fill(color); c.drawPath(path, paint) }
                if (icon == Icon.SHIFT_LOCK) {
                    fill(color)
                    rect.set(-0.20f * s, 0.38f * s, 0.20f * s, 0.46f * s)
                    c.drawRoundRect(rect, 0.03f * s, 0.03f * s, paint)
                }
            }
            Icon.ENTER -> {
                // "return" arrow, mirrored for right-to-left
                if (rtl) c.scale(-1f, 1f)
                stroke(sw * 1.1f, color)
                path.reset()
                path.moveTo(0.46f * s, -0.30f * s)
                path.lineTo(0.46f * s, 0.10f * s)
                path.lineTo(-0.40f * s, 0.10f * s)
                c.drawPath(path, paint)
                path.reset()
                path.moveTo(-0.22f * s, -0.12f * s)
                path.lineTo(-0.44f * s, 0.10f * s)
                path.lineTo(-0.22f * s, 0.32f * s)
                c.drawPath(path, paint)
            }
            Icon.SEARCH -> {
                stroke(sw * 1.2f, color)
                c.drawCircle(-0.08f * s, -0.08f * s, 0.30f * s, paint)
                c.drawLine(0.14f * s, 0.14f * s, 0.44f * s, 0.44f * s, paint)
            }
            Icon.SEND -> {
                fill(color)
                path.reset()
                path.moveTo(-0.46f * s, -0.38f * s)
                path.lineTo(0.48f * s, 0f)
                path.lineTo(-0.46f * s, 0.38f * s)
                path.lineTo(-0.34f * s, 0.04f * s)
                path.lineTo(0.14f * s, 0f)
                path.lineTo(-0.34f * s, -0.04f * s)
                path.close()
                c.drawPath(path, paint)
            }
            Icon.CHECK -> {
                stroke(sw * 1.3f, color)
                path.reset(); path.moveTo(-0.38f * s, 0.02f * s); path.lineTo(-0.10f * s, 0.30f * s); path.lineTo(0.40f * s, -0.28f * s)
                c.drawPath(path, paint)
            }
            Icon.ARROW_NEXT -> {
                if (rtl) c.scale(-1f, 1f)
                stroke(sw * 1.2f, color)
                c.drawLine(-0.42f * s, 0f, 0.42f * s, 0f, paint)
                path.reset(); path.moveTo(0.14f * s, -0.28f * s); path.lineTo(0.42f * s, 0f); path.lineTo(0.14f * s, 0.28f * s)
                c.drawPath(path, paint)
            }
            Icon.SMILEY -> {
                stroke(sw, color)
                c.drawCircle(0f, 0f, 0.44f * s, paint)
                fill(color)
                c.drawCircle(-0.16f * s, -0.10f * s, 0.05f * s, paint)
                c.drawCircle(0.16f * s, -0.10f * s, 0.05f * s, paint)
                stroke(sw, color)
                rect.set(-0.24f * s, -0.14f * s, 0.24f * s, 0.26f * s)
                c.drawArc(rect, 25f, 130f, false, paint)
            }
            Icon.BACK -> {
                if (rtl) c.scale(-1f, 1f)
                stroke(sw * 1.2f, color)
                c.drawLine(0.42f * s, 0f, -0.42f * s, 0f, paint)
                path.reset(); path.moveTo(-0.14f * s, -0.28f * s); path.lineTo(-0.42f * s, 0f); path.lineTo(-0.14f * s, 0.28f * s)
                c.drawPath(path, paint)
            }
            Icon.ARROW_BACK_LABEL -> {}
            Icon.CLOSE -> {
                stroke(sw * 1.2f, color)
                c.drawLine(-0.30f * s, -0.30f * s, 0.30f * s, 0.30f * s, paint)
                c.drawLine(0.30f * s, -0.30f * s, -0.30f * s, 0.30f * s, paint)
            }
            Icon.PLUS -> {
                stroke(sw * 1.3f, color)
                c.drawLine(-0.36f * s, 0f, 0.36f * s, 0f, paint)
                c.drawLine(0f, -0.36f * s, 0f, 0.36f * s, paint)
            }
            Icon.PIN -> {
                stroke(sw, color)
                path.reset()
                path.moveTo(-0.14f * s, -0.44f * s); path.lineTo(0.14f * s, -0.44f * s)
                path.lineTo(0.12f * s, -0.08f * s); path.lineTo(0.28f * s, 0.10f * s); path.lineTo(-0.28f * s, 0.10f * s); path.lineTo(-0.12f * s, -0.08f * s)
                path.close()
                c.drawPath(path, paint)
                c.drawLine(0f, 0.10f * s, 0f, 0.46f * s, paint)
            }
            Icon.KEYBOARD -> {
                stroke(sw, color)
                rect.set(-0.46f * s, -0.28f * s, 0.46f * s, 0.28f * s)
                c.drawRoundRect(rect, 0.08f * s, 0.08f * s, paint)
                fill(color)
                for (i in -2..2) c.drawCircle(i * 0.17f * s, -0.10f * s, 0.035f * s, paint)
                for (i in -1..1) c.drawCircle(i * 0.17f * s + 0.0f, 0.04f * s, 0.035f * s, paint)
                rect.set(-0.22f * s, 0.12f * s, 0.22f * s, 0.18f * s)
                c.drawRoundRect(rect, 0.03f * s, 0.03f * s, paint)
            }
            Icon.MOON -> {
                fill(color)
                path.reset()
                path.addCircle(0f, 0f, 0.40f * s, Path.Direction.CW)
                val cut = Path(); cut.addCircle(0.22f * s, -0.14f * s, 0.34f * s, Path.Direction.CW)
                path.op(cut, Path.Op.DIFFERENCE)
                c.drawPath(path, paint)
            }
            Icon.TRASH -> {
                stroke(sw, color)
                c.drawLine(-0.36f * s, -0.26f * s, 0.36f * s, -0.26f * s, paint)
                c.drawLine(-0.12f * s, -0.26f * s, -0.12f * s, -0.40f * s, paint)
                c.drawLine(0.12f * s, -0.26f * s, 0.12f * s, -0.40f * s, paint)
                c.drawLine(-0.12f * s, -0.40f * s, 0.12f * s, -0.40f * s, paint)
                path.reset(); path.moveTo(-0.28f * s, -0.26f * s); path.lineTo(-0.22f * s, 0.42f * s); path.lineTo(0.22f * s, 0.42f * s); path.lineTo(0.28f * s, -0.26f * s)
                c.drawPath(path, paint)
            }
        }
        c.restore()
    }
}
