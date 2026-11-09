package com.digitalelegance.autocomm

/**
 * Minimal GIF87a/89a decoder: every frame, real per-frame delays, transparency, interlace and
 * disposal (none / background / previous) composited onto a full-size canvas.
 * [onFrame] gets the composited ARGB canvas (reused buffer — copy it) and the frame's delay in ms.
 * Returns (width, height, frameCount) or null if not a GIF.
 */
object GifDecoder {

    fun decode(b: ByteArray, maxFrames: Int, onFrame: (IntArray, Int) -> Unit): Triple<Int, Int, Int>? {
        if (b.size < 13 || b[0] != 'G'.code.toByte() || b[1] != 'I'.code.toByte() || b[2] != 'F'.code.toByte()) return null
        var p = 6
        fun u8() = b[p++].toInt() and 0xFF
        fun u16(): Int { val v = (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8); p += 2; return v }
        fun table(n: Int) = IntArray(n) { val r = u8(); val g = u8(); val bl = u8(); (0xFF shl 24) or (r shl 16) or (g shl 8) or bl }
        fun skipBlocks() { while (p < b.size) { val n = u8(); if (n == 0) break; p += n } }

        val gw = u16(); val gh = u16()
        val packed = u8(); u8(); u8()
        if (gw <= 0 || gh <= 0) return null
        val global = if (packed and 0x80 != 0) table(1 shl ((packed and 7) + 1)) else null

        val canvas = IntArray(gw * gh)
        var saved: IntArray? = null
        var transIndex = -1
        var delayCs = 0
        var disposal = 0
        var prevDisposal = 0
        var prevRect = intArrayOf(0, 0, 0, 0)
        var count = 0

        while (p < b.size && count < maxFrames) {
            when (u8()) {
                0x21 -> {
                    val label = u8()
                    if (label == 0xF9 && p + 5 < b.size) {
                        u8() // block size (4)
                        val f = u8()
                        disposal = (f shr 2) and 7
                        delayCs = u16()
                        val ti = u8()
                        transIndex = if (f and 1 != 0) ti else -1
                        skipBlocks()
                    } else skipBlocks()
                }
                0x2C -> {
                    val fx = u16(); val fy = u16(); val fw = u16(); val fh = u16()
                    val fp = u8()
                    val local = if (fp and 0x80 != 0) table(1 shl ((fp and 7) + 1)) else null
                    val interlaced = fp and 0x40 != 0
                    val colors = local ?: global ?: IntArray(256) { (0xFF shl 24) or (it * 0x010101) }
                    val minCode = u8()
                    // gather data sub-blocks
                    val start = p
                    var total = 0
                    while (p < b.size) { val n = u8(); if (n == 0) break; total += n; p += n }
                    val data = ByteArray(total)
                    var q = start; var o = 0
                    while (o < total) { val n = b[q].toInt() and 0xFF; q++; System.arraycopy(b, q, data, o, n); q += n; o += n }
                    val idx = lzw(data, minCode, fw * fh)

                    // dispose previous frame
                    when (prevDisposal) {
                        2 -> for (y in prevRect[1] until minOf(gh, prevRect[1] + prevRect[3]))
                            for (x in prevRect[0] until minOf(gw, prevRect[0] + prevRect[2])) canvas[y * gw + x] = 0
                        3 -> saved?.copyInto(canvas)
                    }
                    if (disposal == 3) saved = canvas.copyOf()

                    // row order (interlace passes)
                    val rows = if (!interlaced) IntArray(fh) { it } else {
                        val r = ArrayList<Int>(fh)
                        for (y in 0 until fh step 8) r += y
                        for (y in 4 until fh step 8) r += y
                        for (y in 2 until fh step 4) r += y
                        for (y in 1 until fh step 2) r += y
                        r.toIntArray()
                    }
                    for (row in 0 until fh) {
                        val y = fy + rows[row]
                        if (y >= gh) continue
                        for (x in 0 until fw) {
                            val ci = idx[row * fw + x].toInt() and 0xFF
                            if (ci == transIndex || fx + x >= gw) continue
                            canvas[y * gw + fx + x] = colors.getOrElse(ci) { 0 }
                        }
                    }
                    onFrame(canvas, if (delayCs <= 1) 100 else delayCs * 10)
                    count++
                    prevDisposal = disposal
                    prevRect = intArrayOf(fx, fy, fw, fh)
                    // GCE applies to one frame only
                    transIndex = -1; delayCs = 0; disposal = 0
                }
                0x3B -> break
                else -> break
            }
        }
        return Triple(gw, gh, count)
    }

    private fun lzw(data: ByteArray, minCode: Int, n: Int): ByteArray {
        val out = ByteArray(n)
        if (minCode < 1 || minCode > 11) return out
        val clear = 1 shl minCode
        val eoi = clear + 1
        val prefix = IntArray(4096)
        val suffix = ByteArray(4096)
        val first = ByteArray(4096)
        val length = IntArray(4096)
        for (i in 0 until clear) { prefix[i] = -1; suffix[i] = i.toByte(); first[i] = i.toByte(); length[i] = 1 }
        var codeSize = minCode + 1
        var next = eoi + 1
        var prev = -1
        var op = 0
        var acc = 0
        var bits = 0
        var ip = 0

        fun emit(code: Int) {
            val len = length[code]
            var c = code
            var k = op + len - 1
            while (c >= 0) {
                if (k < n) out[k] = suffix[c]
                k--
                c = prefix[c]
            }
            op += len
        }

        while (op < n) {
            while (bits < codeSize && ip < data.size) { acc = acc or ((data[ip++].toInt() and 0xFF) shl bits); bits += 8 }
            if (bits < codeSize) break
            val code = acc and ((1 shl codeSize) - 1)
            acc = acc ushr codeSize
            bits -= codeSize

            if (code == clear) { codeSize = minCode + 1; next = eoi + 1; prev = -1; continue }
            if (code == eoi) break
            if (prev == -1) {
                if (code >= clear) break
                emit(code); prev = code; continue
            }
            val firstChar: Byte
            if (code < next) {
                firstChar = first[code]
                emit(code)
            } else {
                // KwKwK: string(prev) + first(prev)
                firstChar = first[prev]
                emit(prev)
                if (op < n) out[op] = firstChar
                op++
            }
            if (next < 4096) {
                prefix[next] = prev
                suffix[next] = firstChar
                first[next] = first[prev]
                length[next] = length[prev] + 1
                next++
                if (next == (1 shl codeSize) && codeSize < 12) codeSize++
            }
            prev = code
        }
        return out
    }
}
