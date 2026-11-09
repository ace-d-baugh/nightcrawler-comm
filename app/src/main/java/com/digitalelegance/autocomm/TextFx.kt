package com.digitalelegance.autocomm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.text.BreakIterator
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Renders styled text (with effects) into panel frames. Emoji keep their own colors. */
object TextFx {

    private const val MAX_FRAMES = 360
    private const val OPAQUE = 0xFF000000.toInt()

    fun typeface(f: Font): Typeface = when (f) {
        Font.SANS_BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        Font.SANS -> Typeface.SANS_SERIF
        Font.CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
        Font.SERIF -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
        Font.MONO -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        Font.CASUAL -> Typeface.create("casual", Typeface.NORMAL)
        Font.CURSIVE -> Typeface.create("cursive", Typeface.NORMAL)
    }

    private class Strip(val w: Int, val h: Int, val px: IntArray, val edges: IntArray) {
        fun at(x: Int, y: Int) = px[y * w + x]
    }

    private fun strip(text: String, style: TextStyle, h: Int): Strip {
        val paint = Paint().apply {
            isAntiAlias = false
            typeface = typeface(style.font)
            color = OPAQUE or style.color
        }
        val b = Rect()
        if (style.fontSize > 0) {
            paint.textSize = style.fontSize.toFloat()
        } else {
            paint.textSize = 100f
            paint.getTextBounds(text, 0, text.length, b)
            paint.textSize = (100f * h / max(1, b.height())).coerceAtLeast(4f)
        }
        paint.getTextBounds(text, 0, text.length, b)
        val tw = max(1, ceil(paint.measureText(text)).toInt())
        val bmp = Bitmap.createBitmap(tw, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, 0f, h / 2f - (b.top + b.bottom) / 2f, paint)
        val raw = IntArray(tw * h)
        bmp.getPixels(raw, 0, tw, 0, 0, tw, h)
        bmp.recycle()
        val px = IntArray(raw.size) { val c = raw[it]; if ((c ushr 24) > 110) c or OPAQUE else 0 }

        val edges = ArrayList<Int>()
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        var e = it.next()
        while (e != BreakIterator.DONE) {
            edges += min(tw, ceil(paint.measureText(text, 0, e)).toInt())
            e = it.next()
        }
        return Strip(tw, h, px, edges.toIntArray())
    }

    private fun hsv(h: Float, v: Float = 1f) = android.graphics.Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, 1f, v))

    private fun scale(c: Int, f: Float): Int {
        val r = ((c shr 16 and 0xFF) * f).toInt()
        val g = ((c shr 8 and 0xFF) * f).toInt()
        val b = ((c and 0xFF) * f).toInt()
        return OPAQUE or (r shl 16) or (g shl 8) or b
    }

    private class Ctx(val w: Int, val h: Int, val style: TextStyle) {
        val inset = if (style.border == BorderStyle.NONE || w < 4 || h < 4) 0 else 1
        val iw = w - 2 * inset
        val ih = h - 2 * inset
        val bg = if (style.bg and 0xFFFFFF == 0) 0 else OPAQUE or style.bg
        val d = 230 - style.speed.coerceIn(1, 10) * 21
        val hold = 600 + (10 - style.speed.coerceIn(1, 10)) * 160
        val frames = ArrayList<IntArray>()
        val delays = ArrayList<Int>()
        private var t = 0

        private val perimeter: IntArray by lazy {
            val l = ArrayList<Int>()
            for (x in 0 until w) l += x
            for (y in 1 until h) l += y * w + w - 1
            for (x in w - 2 downTo 0) l += (h - 1) * w + x
            for (y in h - 2 downTo 1) l += y * w
            l.toIntArray()
        }

        fun blank() = IntArray(w * h) { bg }

        fun put(f: IntArray, x: Int, y: Int, c: Int) {
            if (c != 0 && x in 0 until iw && y in 0 until ih) f[(y + inset) * w + x + inset] = c
        }

        fun emit(f: IntArray, delay: Int) {
            drawBorder(f)
            frames += f
            delays += max(20, delay)
            t++
        }

        private fun drawBorder(f: IntArray) {
            if (inset == 0) return
            val bc = OPAQUE or style.borderColor
            val n = perimeter.size
            perimeter.forEachIndexed { i, idx ->
                val c = when (style.border) {
                    BorderStyle.SOLID -> bc
                    BorderStyle.DASHED -> if ((i / 2) % 2 == 0) bc else 0
                    BorderStyle.CHASE -> if ((i - t + n * 1000) % 6 < 3) bc else 0
                    BorderStyle.RAINBOW -> hsv(i * 360f / n + t * 12f)
                    BorderStyle.NONE -> 0
                }
                if (c != 0) f[idx] = c
            }
        }

        val animatedBorder get() = style.border == BorderStyle.CHASE || style.border == BorderStyle.RAINBOW
    }

    fun render(text: String, style: TextStyle, w: Int, h: Int): Frames {
        val c = Ctx(w, h, style)
        if (text.isBlank() || c.ih < 3 || c.iw < 3) {
            c.emit(c.blank(), 500)
            return Frames(w, h, c.frames, c.delays)
        }
        if (style.wrap && canWrap(style.fontSize, c.ih)) wrapped(c, text, style)?.let { return it }
        return animate(c, strip(text, style, c.ih), style, OPAQUE or style.color)
    }

    /** Line height used for wrapped text (tight: cap height + 1 LED gap). */
    fun lineHeight(fontSize: Int) = ceil(fontSize * 0.8f).toInt() + 1

    /** Word wrap is only offered when at least two lines fit on the panel. */
    fun canWrap(fontSize: Int, panelH: Int) = fontSize > 0 && lineHeight(fontSize) * 2 <= panelH

    /**
     * Multi-line text. If all lines fit, the block is treated like a word that fits (any effect works);
     * otherwise it scrolls upward. Returns null if wrapping gives only one line.
     */
    private fun wrapped(c: Ctx, text: String, style: TextStyle): Frames? {
        val paint = Paint().apply {
            isAntiAlias = false
            typeface = typeface(style.font)
            color = OPAQUE or style.color
            textSize = style.fontSize.toFloat()
        }
        val lines = ArrayList<String>()
        text.trim().split(Regex("\\s+")).forEach { word ->
            val cur = lines.lastOrNull()
            if (cur != null && paint.measureText("$cur $word") <= c.iw) lines[lines.lastIndex] = "$cur $word"
            else lines += word
        }
        if (lines.size < 2) return null
        val lh = lineHeight(style.fontSize)
        val blockH = lines.size * lh - 1
        val bh = maxOf(blockH, c.ih)
        val bmp = Bitmap.createBitmap(c.iw, bh, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val top = if (blockH <= c.ih) (c.ih - blockH) / 2 else 0
        val base = ceil(style.fontSize * 0.8f) - 1
        lines.forEachIndexed { i, l ->
            val x = ((c.iw - paint.measureText(l)) / 2f).coerceAtLeast(0f)
            cv.drawText(l, x, top + i * lh + base, paint)
        }
        val raw = IntArray(c.iw * bh)
        bmp.getPixels(raw, 0, c.iw, 0, 0, c.iw, bh)
        bmp.recycle()
        val px = IntArray(raw.size) { val v = raw[it]; if ((v ushr 24) > 110) v or OPAQUE else 0 }

        if (blockH <= c.ih) {
            val strip = Strip(c.iw, c.ih, px, IntArray((c.iw + 3) / 4) { min(c.iw, (it + 1) * 4) })
            return animate(c, strip, style, OPAQUE or style.color)
        }
        // Too many lines: scroll the block up, pausing on the first screen.
        val total = bh + c.ih
        var oy = 0
        while (oy <= total) {
            val f = c.blank()
            for (y in 0 until c.ih) {
                val sy = y + oy - 0
                if (sy !in 0 until bh) continue
                for (x in 0 until c.iw) { val v = px[sy * c.iw + x]; if (v != 0) c.put(f, x, y, v) }
            }
            c.emit(f, if (oy == 0) c.hold else c.d * 2)
            oy++
        }
        return Frames(c.w, c.h, c.frames, c.delays).brightness(style.brightness)
    }

    enum class Arrow { LEFT, RIGHT, UTURN }

    /**
     * Turn signals:
     * LEFT  – word, then 3 left chevrons appear one by one moving away from it, then all clear.
     * RIGHT – same, mirrored.
     * UTURN – word in the middle, then a U-turn arrow is drawn on each side (up, curve left, down to the point).
     */
    fun arrow(kind: Arrow, style: TextStyle, w: Int, h: Int): Frames {
        val col = OPAQUE or style.color
        val word = when (kind) { Arrow.LEFT -> "LEFT"; Arrow.RIGHT -> "RIGHT"; Arrow.UTURN -> "U-TURN" }
        val frames = ArrayList<IntArray>()
        val delays = ArrayList<Int>()
        fun put(f: IntArray, x: Int, y: Int) { if (x in 0 until w && y in 0 until h) f[y * w + x] = col }
        fun blit(f: IntArray, st: Strip, ox: Int, oy: Int) {
            for (y in 0 until st.h) for (x in 0 until st.w) { val p = st.at(x, y); if (p != 0 && x + ox in 0 until w && y + oy in 0 until h) f[(y + oy) * w + x + ox] = p }
        }
        val thick = if (h >= 20) 2 else 1

        /** Largest word strip whose width fits [maxW]. */
        fun fitWord(maxW: Int, maxH: Int): Strip {
            var th = maxH
            var st = strip(word, style.copy(fontSize = 0), th)
            while (st.w > maxW && th > 5) { th--; st = strip(word, style.copy(fontSize = 0), th) }
            return st
        }

        if (kind == Arrow.UTURN) {
            var st = fitWord(w, h)
            var th = st.h
            while ((w - st.w) / 2 - 1 < maxOf(6, h / 2) && th > 5) { th--; st = strip(word, style.copy(fontSize = 0), th) }
            val tx = (w - st.w) / 2
            val ty = (h - st.h) / 2
            val side = maxOf(4, tx - 1)
            // path for one arrow in a side-wide box starting at x = 0
            val headLen = maxOf(2, minOf(h / 5, (side - 3) / 2))
            val xl = headLen
            val xr = side - 1
            val rad = maxOf(1, (xr - xl) / 2)
            val cx = (xl + xr) / 2f
            val cy = rad + 1
            val bottom = h - 2
            val path = ArrayList<Pair<Int, Int>>()
            for (y in bottom downTo cy) path += xr to y
            val steps = maxOf(8, (Math.PI * rad * 2).toInt())
            for (i in 0..steps) {
                val t = Math.PI * i / steps
                val p = (cx + rad * kotlin.math.cos(t)).roundToInt() to (cy - rad * kotlin.math.sin(t)).roundToInt()
                if (path.lastOrNull() != p) path += p
            }
            for (y in cy..bottom) { val p = xl to y; if (path.lastOrNull() != p) path += p }
            val head = (1..headLen).flatMap { k -> listOf(xl - k to bottom - k, xl + k to bottom - k) }
            val offsets = listOf(0, w - side)

            fun frame(n: Int, withHead: Boolean): IntArray {
                val f = IntArray(w * h)
                blit(f, st, tx, ty)
                offsets.forEach { ox ->
                    for (i in 0 until minOf(n, path.size)) {
                        val (x, y) = path[i]
                        for (d in 0 until thick) put(f, ox + x + d, y)
                    }
                    if (withHead) head.forEach { (x, y) -> for (d in 0 until thick) put(f, ox + x + d, y) }
                }
                return f
            }
            frames += frame(0, false); delays += 400
            val per = maxOf(1, ceil(path.size / 14f).toInt())
            var n = per
            while (n < path.size) { frames += frame(n, false); delays += 60; n += per }
            frames += frame(path.size, false); delays += 60
            frames += frame(path.size, true); delays += 900
            frames += IntArray(w * h); delays += 300
            return Frames(w, h, frames, delays).brightness(style.brightness)
        }

        // LEFT / RIGHT
        val ct = 2  // chevron stroke width (LEDs per row)
        val half = maxOf(2, (h - 3) / 2)
        val cw = half + ct + 1          // chevron width
        val gap = maxOf(1, cw / 3)
        val chevW = 3 * cw + 2 * gap
        val st = fitWord(w - chevW - 2, h)
        val total = chevW + 2 + st.w
        val x0 = maxOf(0, (w - total) / 2)
        val cy = h / 2
        val left = kind == Arrow.LEFT
        val textX = if (left) x0 + chevW + 2 else x0
        val chevStart = if (left) x0 else x0 + st.w + 2
        // chevron i = 0 is nearest the word
        fun chevX(i: Int) = if (left) chevStart + (2 - i) * (cw + gap) else chevStart + i * (cw + gap)
        fun drawChev(f: IntArray, i: Int) {
            val bx = chevX(i)
            for (k in 0..half) {
                val dx = k * (cw - ct - 1) / half
                val x = if (left) bx + dx else bx + cw - 1 - dx
                for (d in 0 until ct) {
                    val xx = if (left) x + d else x - d
                    put(f, xx, cy - k); put(f, xx, cy + k)
                }
            }
        }
        fun frame(n: Int): IntArray {
            val f = IntArray(w * h)
            blit(f, st, textX, (h - st.h) / 2)
            for (i in 0 until n) drawChev(f, i)
            return f
        }
        frames += frame(0); delays += 350
        frames += frame(1); delays += 220
        frames += frame(2); delays += 220
        frames += frame(3); delays += 800
        frames += IntArray(w * h); delays += 300
        return Frames(w, h, frames, delays).brightness(style.brightness)
    }

    /** Effects that make sense for a picture. */
    val imageEffects = Effect.entries.filter { it != Effect.TYPEWRITER && it != Effect.RAINBOW }

    /** Apply an animation effect to a panel-sized image. */
    fun renderImage(px: IntArray, w: Int, h: Int, style: TextStyle): Frames {
        val st = style.copy(border = BorderStyle.NONE, effect = if (style.effect in imageEffects) style.effect else Effect.HOLD)
        val c = Ctx(w, h, st)
        val strip = Strip(w, h, IntArray(px.size) { if (px[it] ushr 24 == 0) 0 else px[it] or OPAQUE }, IntArray((w + 3) / 4) { min(w, (it + 1) * 4) })
        return animate(c, strip, st, 0)
    }

    private fun animate(c: Ctx, s: Strip, style: TextStyle, textColor: Int): Frames {
        val w = c.w
        val h = c.h
        val fits = s.w <= c.iw
        val left = style.effect != Effect.SCROLL_RIGHT
        val ox0 = if (fits) (c.iw - s.w) / 2 else 0

        fun draw(f: IntArray, ox: Int, oy: Int, mod: ((Int, Int, Int) -> Int)? = null) {
            for (sy in 0 until s.h) {
                val y = sy + oy
                if (y !in 0 until c.ih) continue
                for (sx in 0 until s.w) {
                    val px = s.at(sx, sy)
                    if (px == 0) continue
                    c.put(f, sx + ox, y, mod?.invoke(sx, sy, px) ?: px)
                }
            }
        }

        fun scroll(mod: ((Int, Int, Int, Int) -> Int)? = null) {
            val total = c.iw + s.w
            val step = max(1, ceil(total / MAX_FRAMES.toFloat()).toInt())
            var p = 0
            var k = 0
            while (p <= total) {
                val ox = if (left) c.iw - p else p - s.w
                val f = c.blank()
                val kk = k
                draw(f, ox, 0, mod?.let { m -> { sx: Int, sy: Int, px: Int -> m(kk, sx, sy, px) } })
                c.emit(f, c.d)
                p += step; k++
            }
        }

        fun finalFrame(mod: ((Int, Int, Int) -> Int)? = null) = c.blank().also { draw(it, ox0, 0, mod) }

        /** After a build effect: hold (or scroll the rest of wide text), then clear. */
        fun finish() {
            if (fits) {
                c.emit(finalFrame(), c.hold)
            } else {
                c.emit(finalFrame(), c.hold / 2)
                val end = c.iw - s.w
                val step = max(1, ceil(-end / (MAX_FRAMES / 2f)).toInt())
                var ox = -step
                while (ox > end) { c.emit(c.blank().also { draw(it, ox, 0) }, c.d); ox -= step }
                c.emit(c.blank().also { draw(it, end, 0) }, c.hold)
            }
            c.emit(c.blank(), 400)
        }

        /** Lit pixels of the visible starting window: (x, y, color). */
        fun window(): List<Triple<Int, Int, Int>> {
            val l = ArrayList<Triple<Int, Int, Int>>()
            for (sx in 0 until s.w) {
                val x = sx + ox0
                if (x !in 0 until c.iw) continue
                for (sy in 0 until s.h) { val px = s.at(sx, sy); if (px != 0) l += Triple(x, sy, px) }
            }
            return l
        }


        when (style.effect) {
            Effect.SCROLL_LEFT, Effect.SCROLL_RIGHT -> scroll()

            Effect.HOLD -> when {
                !fits -> scroll()
                c.animatedBorder -> repeat(12) { c.emit(finalFrame(), 120) }
                else -> c.emit(finalFrame(), 1000)
            }

            Effect.STATIC -> if (!fits) scroll() else {
                c.emit(finalFrame(), c.hold * 2)
                c.emit(c.blank(), 500)
            }

            Effect.BLINK -> if (!fits) scroll { k, _, _, px -> if ((k / 6) % 2 == 0) px else 0 } else {
                c.emit(finalFrame(), c.d * 3)
                c.emit(c.blank(), c.d * 2)
            }

            Effect.LASER -> {
                val lit = window().sortedWith(compareBy<Triple<Int, Int, Int>> { it.first }.thenByDescending { it.second })
                val per = max(1, ceil(lit.size / 40f).toInt())
                var k = per
                while (k < lit.size + per) {
                    val n = min(k, lit.size)
                    val f = c.blank()
                    for (i in 0 until n) c.put(f, lit[i].first, lit[i].second, lit[i].third)
                    if (n in 1 until lit.size) {
                        val (hx, hy) = lit[n - 1]
                        // beam from the top edge down to the head
                        for (y in 0 until hy) c.put(f, hx, y, scale(0xFF0000, 0.5f))
                        c.put(f, hx, hy, 0xFFFFFFFF.toInt())
                    }
                    c.emit(f, max(30, c.d / 2))
                    k += per
                }
                finish()
            }

            Effect.SNOWFALL -> {
                val rnd = Random(7)
                val lit = window().groupBy { it.second }.toSortedMap(compareByDescending<Int> { it })
                    .values.flatMap { it.shuffled(rnd) }
                val rate = max(1, ceil(lit.size / max(1f, 40f - c.ih)).toInt())
                val total = (lit.size - 1) / rate + c.ih + 1
                for (tt in 0..total) {
                    val f = c.blank()
                    lit.forEachIndexed { i, (x, ty, px) ->
                        val start = i / rate
                        if (tt < start) return@forEachIndexed
                        val pos = tt - start
                        if (pos >= ty) c.put(f, x, ty, px)
                        else {
                            if (pos - 2 >= 0) c.put(f, x, pos - 2, scale(px, 0.15f))
                            if (pos - 1 >= 0) c.put(f, x, pos - 1, scale(px, 0.35f))
                            c.put(f, x, pos, px)
                        }
                    }
                    c.emit(f, max(30, c.d / 2))
                }
                finish()
            }

            Effect.BUILD -> {
                val rows = window().groupBy { it.second }.toSortedMap(compareByDescending<Int> { it })
                val stacked = ArrayList<Triple<Int, Int, Int>>()
                val fall = max(1, c.ih / 12)
                rows.forEach { (ty, row) ->
                    var pos = 0
                    while (true) {
                        val y = min(pos, ty)
                        val f = c.blank()
                        stacked.forEach { c.put(f, it.first, it.second, it.third) }
                        row.forEach { c.put(f, it.first, y, it.third) }
                        c.emit(f, max(25, c.d / 3))
                        if (y == ty) break
                        pos += fall
                    }
                    stacked += row
                }
                finish()
            }

            Effect.INTERWEAVE -> {
                val steps = 20
                for (k in 0..steps) {
                    val shift = ((1f - k / steps.toFloat()) * c.iw).roundToInt()
                    val f = c.blank()
                    for (sy in 0 until s.h) for (sx in 0 until s.w) {
                        val px = s.at(sx, sy)
                        if (px == 0) continue
                        val x = sx + ox0 + if (sy % 2 == 0) -shift else shift
                        c.put(f, x, sy, px)
                    }
                    c.emit(f, c.d)
                }
                finish()
            }

            Effect.TYPEWRITER -> {
                s.edges.forEach { e ->
                    val ox = if (fits) ox0 else min(0, c.iw - e)
                    c.emit(c.blank().also { f -> draw(f, ox, 0) { sx, _, px -> if (sx < e) px else 0 } }, max(60, c.d * 3))
                }
                c.emit(c.blank().also { draw(it, if (fits) ox0 else c.iw - s.w, 0) }, c.hold)
                c.emit(c.blank(), 400)
            }

            Effect.FADE -> if (!fits) scroll() else {
                val levels = listOf(0.1f, 0.25f, 0.45f, 0.7f, 1f)
                levels.forEach { l -> c.emit(finalFrame { _, _, px -> scale(px, l) }, 70) }
                c.emit(finalFrame(), c.hold)
                levels.reversed().drop(1).forEach { l -> c.emit(finalFrame { _, _, px -> scale(px, l) }, 70) }
                c.emit(c.blank(), 400)
            }

            Effect.RAINBOW -> {
                val mod = { k: Int, sx: Int, _: Int, px: Int -> if (px == textColor) hsv(sx * 10f + k * 15f) else px }
                if (!fits) scroll(mod) else repeat(24) { k -> c.emit(finalFrame { sx, sy, px -> mod(k, sx, sy, px) }, max(40, c.d)) }
            }

            Effect.WAVE -> {
                val amp = max(1, c.ih / 8)
                fun waveFrame(k: Int, ox: Int): IntArray {
                    val f = c.blank()
                    for (sx in 0 until s.w) {
                        val dy = (sin((sx + k) * 2 * PI / 16) * amp).roundToInt()
                        for (sy in 0 until s.h) { val px = s.at(sx, sy); if (px != 0) c.put(f, sx + ox, sy + dy, px) }
                    }
                    return f
                }
                if (fits) repeat(16) { k -> c.emit(waveFrame(k, ox0), max(40, c.d)) }
                else {
                    val total = c.iw + s.w
                    val step = max(1, ceil(total / MAX_FRAMES.toFloat()).toInt())
                    var p = 0; var k = 0
                    while (p <= total) {
                        c.emit(waveFrame(k, if (left) c.iw - p else p - s.w), c.d)
                        p += step; k++
                    }
                }
            }

            Effect.SPARKLE -> {
                val rnd = Random(11)
                val mod = { _: Int, _: Int, _: Int, px: Int -> if (rnd.nextFloat() < 0.12f) 0xFFFFFFFF.toInt() else px }
                if (!fits) scroll(mod) else repeat(16) { k ->
                    val f = finalFrame { sx, sy, px -> mod(k, sx, sy, px) }
                    repeat(max(1, c.iw * c.ih / 60)) { c.put(f, rnd.nextInt(c.iw), rnd.nextInt(c.ih), if (rnd.nextBoolean()) scale(0xFFFFFF, 0.4f) else 0) }
                    c.emit(f, max(60, c.d))
                }
            }

            Effect.SCROLL_UP, Effect.SCROLL_DOWN -> {
                val up = style.effect == Effect.SCROLL_UP
                for (k in c.ih downTo 1) c.emit(c.blank().also { draw(it, ox0, if (up) k else -k) }, c.d)
                c.emit(finalFrame(), c.hold)
                for (k in 1..c.ih) c.emit(c.blank().also { draw(it, ox0, if (up) -k else k) }, c.d)
            }
        }
        return Frames(w, h, c.frames, c.delays).brightness(style.brightness)
    }
}
