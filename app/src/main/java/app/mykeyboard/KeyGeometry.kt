package app.mykeyboard

/** Pure key-placement maths (no Android types) so it can be unit-tested on the JVM. */
object KeyGeometry {
    /** Side margin and key gap are proportional to the width, measured from the 1079 px reference design. */
    fun margin(w: Float) = w * 12f / 1079f
    fun gap(w: Float) = w * 14f / 1079f

    fun layout(rows: List<Row>, w: Float, top: Float, keyH: Float, rowGap: Float) {
        if (w <= 0f || rows.isEmpty()) return
        val margin = margin(w)
        val gap = gap(w)
        var y = top
        rows.forEachIndexed { ri, row ->
            val last = ri == rows.lastIndex
            val h = if (last) keyH * (125f / 121f) else keyH
            if (last) y += keyH * 0.03f
            val units = row.keys.sumOf { it.weight.toDouble() }.toFloat() + row.padStart + row.padEnd
            val gaps = (row.keys.size - 1) * gap + (if (row.padStart > 0f) gap else 0f) + (if (row.padEnd > 0f) gap else 0f)
            val unit = (w - 2 * margin - gaps) / units
            var x = margin + if (row.padStart > 0f) row.padStart * unit + gap else 0f
            row.keys.forEach { k ->
                k.x = x; k.y = y; k.w = k.weight * unit; k.h = h
                x += k.w + gap
            }
            y += h + rowGap
        }
    }
}
