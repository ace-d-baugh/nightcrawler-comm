package com.digitalelegance.autocomm.render

import java.io.ByteArrayOutputStream

/** Minimal looping GIF89a encoder for small indexed frames (LZW). */
class GifEncoder(private val width: Int, private val height: Int, palette: IntArray) {

    private val out = ByteArrayOutputStream()
    private val tableBits: Int
    private val minCodeSize: Int

    init {
        var bits = 1
        while ((1 shl bits) < palette.size) bits++
        tableBits = bits
        minCodeSize = maxOf(2, bits)

        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        out.write(0x80 or ((tableBits - 1) shl 4) or (tableBits - 1)) // global table, color res, size
        out.write(0) // background index
        out.write(0) // aspect
        for (i in 0 until (1 shl tableBits)) {
            val c = if (i < palette.size) palette[i] else 0
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }
        // NETSCAPE2.0 infinite loop
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
    }

    /** indices: width*height palette indices. delayCs: hundredths of a second. */
    fun addFrame(indices: ByteArray, delayCs: Int) {
        require(indices.size == width * height)
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x04)) // GCE, disposal = keep
        writeShort(delayCs)
        out.write(0) // transparent index (unused)
        out.write(0)
        out.write(0x2C)
        writeShort(0); writeShort(0); writeShort(width); writeShort(height)
        out.write(0) // no local table, not interlaced
        out.write(minCodeSize)
        val data = lzw(indices)
        var i = 0
        while (i < data.size) {
            val n = minOf(255, data.size - i)
            out.write(n)
            out.write(data, i, n)
            i += n
        }
        out.write(0)
    }

    fun finish(): ByteArray {
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun writeShort(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun lzw(input: ByteArray): ByteArray {
        val buf = ByteArrayOutputStream()
        var bitBuf = 0
        var bitCount = 0
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        var codeSize = minCodeSize + 1
        var nextCode = eoi + 1
        val table = HashMap<Int, Int>()

        fun emit(code: Int) {
            bitBuf = bitBuf or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                buf.write(bitBuf and 0xFF)
                bitBuf = bitBuf ushr 8
                bitCount -= 8
            }
        }

        emit(clear)
        var cur = input[0].toInt() and 0xFF
        for (i in 1 until input.size) {
            val k = input[i].toInt() and 0xFF
            val key = (cur shl 8) or k
            val hit = table[key]
            if (hit != null) {
                cur = hit
                continue
            }
            emit(cur)
            if (nextCode == 4096) {
                emit(clear)
                table.clear()
                codeSize = minCodeSize + 1
                nextCode = eoi + 1
            } else {
                if (nextCode >= (1 shl codeSize)) codeSize++
                table[key] = nextCode++
            }
            cur = k
        }
        emit(cur)
        emit(eoi)
        if (bitCount > 0) buf.write(bitBuf and 0xFF)
        return buf.toByteArray()
    }
}
