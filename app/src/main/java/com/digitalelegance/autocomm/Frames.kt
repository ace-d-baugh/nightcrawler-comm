package com.digitalelegance.autocomm

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** One or more panel-sized frames. Pixels are ARGB; alpha 0 = LED off. */
class Frames(val w: Int, val h: Int, val pixels: List<IntArray>, val delays: List<Int>) {

    val count get() = pixels.size

    /** Nearest-neighbour scale to fit (centered) inside tw×th. */
    fun fitTo(tw: Int, th: Int): Frames {
        if (tw == w && th == h) return this
        val s = min(tw / w.toFloat(), th / h.toFloat())
        val dw = max(1, (w * s).toInt())
        val dh = max(1, (h * s).toInt())
        val ox = (tw - dw) / 2
        val oy = (th - dh) / 2
        val out = pixels.map { src ->
            val d = IntArray(tw * th)
            for (y in 0 until dh) for (x in 0 until dw) {
                val sx = min(w - 1, (x / s).toInt())
                val sy = min(h - 1, (y / s).toInt())
                d[(oy + y) * tw + ox + x] = src[sy * w + sx]
            }
            d
        }
        return Frames(tw, th, out, delays)
    }

    /** Left-right mirror (front panel is read through other drivers' rear-view mirrors). */
    fun mirrored(): Frames = Frames(w, h, pixels.map { src ->
        IntArray(src.size) { i -> val y = i / w; val x = i % w; src[y * w + (w - 1 - x)] }
    }, delays)

    fun brightness(pct: Int): Frames {
        if (pct >= 100) return this
        val f = pct.coerceIn(0, 100) / 100f
        return Frames(w, h, pixels.map { fr ->
            IntArray(fr.size) { i ->
                val c = fr[i]
                if (c ushr 24 == 0) 0
                else {
                    val r = ((c shr 16 and 0xFF) * f).toInt()
                    val g = ((c shr 8 and 0xFF) * f).toInt()
                    val b = ((c and 0xFF) * f).toInt()
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }, delays)
    }

    fun bitmap(index: Int = 0): Bitmap {
        val src = pixels[index.coerceIn(0, count - 1)]
        val opaque = IntArray(src.size) { if (src[it] ushr 24 == 0) 0xFF000000.toInt() else src[it] or 0xFF000000.toInt() }
        return Bitmap.createBitmap(opaque, w, h, Bitmap.Config.ARGB_8888)
    }

    /** PNG for a single frame, GIF otherwise. Returns (bytes, isGif). */
    fun encode(): Pair<ByteArray, Boolean> {
        if (count == 1) {
            val out = ByteArrayOutputStream()
            bitmap(0).compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray() to false
        }
        return GifWriter.encode(this) to true
    }
}

object GifWriter {

    fun encode(f: Frames): ByteArray {
        val rgbFrames = f.pixels.map { fr -> IntArray(fr.size) { if (fr[it] ushr 24 == 0) 0 else fr[it] and 0xFFFFFF } }

        // Palette: exact colors if they fit, else a 6x6x6 cube.
        val distinct = LinkedHashSet<Int>()
        for (fr in rgbFrames) { for (c in fr) { distinct.add(c); if (distinct.size > 256) break }; if (distinct.size > 256) break }
        val palette: IntArray
        val indexOf: (Int) -> Int
        if (distinct.size <= 256) {
            palette = distinct.toIntArray()
            val map = HashMap<Int, Int>(palette.size * 2)
            palette.forEachIndexed { i, c -> map[c] = i }
            indexOf = { map[it] ?: 0 }
        } else {
            palette = IntArray(216) { i ->
                val r = i / 36; val g = i / 6 % 6; val b = i % 6
                ((r * 51) shl 16) or ((g * 51) shl 8) or (b * 51)
            }
            indexOf = { c ->
                val r = ((c shr 16 and 0xFF) + 25) / 51
                val g = ((c shr 8 and 0xFF) + 25) / 51
                val b = ((c and 0xFF) + 25) / 51
                r * 36 + g * 6 + b
            }
        }
        var bits = 1
        while ((1 shl bits) < palette.size) bits++
        val tableSize = 1 shl bits

        val out = ByteArrayOutputStream()
        out.write("GIF89a".toByteArray())
        le16(out, f.w); le16(out, f.h)
        out.write(0x80 or (7 shl 4) or (bits - 1))
        out.write(0); out.write(0)
        for (i in 0 until tableSize) {
            val c = if (i < palette.size) palette[i] else 0
            out.write(c shr 16 and 0xFF); out.write(c shr 8 and 0xFF); out.write(c and 0xFF)
        }
        // Loop forever
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray())
        out.write(byteArrayOf(3, 1, 0, 0, 0))

        val minCode = max(2, bits)
        rgbFrames.forEachIndexed { k, fr ->
            val cs = max(2, (f.delays.getOrElse(k) { 100 } + 5) / 10)
            out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0x04))
            le16(out, min(cs, 65535)); out.write(0); out.write(0)
            out.write(0x2C)
            le16(out, 0); le16(out, 0); le16(out, f.w); le16(out, f.h)
            out.write(0)
            val idx = ByteArray(fr.size) { indexOf(fr[it]).toByte() }
            lzw(idx, minCode, out)
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun le16(o: ByteArrayOutputStream, v: Int) { o.write(v and 0xFF); o.write(v shr 8 and 0xFF) }

    private fun lzw(data: ByteArray, minCodeSize: Int, out: ByteArrayOutputStream) {
        out.write(minCodeSize)
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        var codeSize = minCodeSize + 1
        var next = eoi + 1
        val dict = HashMap<Int, Int>()
        val bw = BitWriter(out)
        bw.write(clear, codeSize)
        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val k = data[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val c = dict[key]
            if (c != null) {
                prefix = c
            } else {
                bw.write(prefix, codeSize)
                if (next == 4096) {
                    bw.write(clear, codeSize)
                    dict.clear()
                    codeSize = minCodeSize + 1
                    next = eoi + 1
                } else {
                    if (next >= (1 shl codeSize)) codeSize++
                    dict[key] = next++
                }
                prefix = k
            }
        }
        bw.write(prefix, codeSize)
        bw.write(eoi, codeSize)
        bw.flush()
    }

    private class BitWriter(val out: ByteArrayOutputStream) {
        private var cur = 0
        private var n = 0
        private val block = ByteArray(255)
        private var len = 0

        fun write(code: Int, size: Int) {
            cur = cur or (code shl n)
            n += size
            while (n >= 8) { byte(cur and 0xFF); cur = cur ushr 8; n -= 8 }
        }

        private fun byte(b: Int) {
            block[len++] = b.toByte()
            if (len == 255) { out.write(255); out.write(block, 0, 255); len = 0 }
        }

        fun flush() {
            if (n > 0) { byte(cur and 0xFF); cur = 0; n = 0 }
            if (len > 0) { out.write(len); out.write(block, 0, len); len = 0 }
            out.write(0)
        }
    }
}
