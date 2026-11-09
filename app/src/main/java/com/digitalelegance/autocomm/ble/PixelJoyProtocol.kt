package com.digitalelegance.autocomm.ble

import kotlin.random.Random

/**
 * PixelJoy (Jieli AC695x / AC6951C) LED panel protocol.
 * Reference: github.com/sdylewski/openJoy docs/PROTOCOL.md
 *
 * Frame: A0 <CMD> <LEN> <payload> <CRC16-lo> <CRC16-hi>
 * LEN = payload + 3. CRC-16/MODBUS over A0..payload.
 */
object PixelJoyProtocol {

    const val CHUNK_PAYLOAD_MAX = 460

    fun crc16(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0xFFFF
        for (i in from until to) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }

    fun packet(cmd: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val frame = ByteArray(3 + payload.size + 2)
        frame[0] = 0xA0.toByte()
        frame[1] = cmd.toByte()
        frame[2] = (payload.size + 3).toByte()
        payload.copyInto(frame, 3)
        val crc = crc16(frame, 0, 3 + payload.size)
        frame[frame.size - 2] = (crc and 0xFF).toByte()
        frame[frame.size - 1] = (crc ushr 8).toByte()
        return frame
    }

    private val CCHIP = "CCHIP".toByteArray(Charsets.US_ASCII)

    fun handshake1() = packet(0x00, CCHIP)

    fun handshake2(b8: Byte, b9: Byte) = packet(
        0x01,
        CCHIP + byteArrayOf((b8.toInt() xor 0x24).toByte(), (b9.toInt() xor 0x4C).toByte())
    )

    fun queryStatus() = packet(0x10)
    fun displayOff() = packet(0x0C, byteArrayOf(0x01))
    fun setMode(mode: Int) = packet(0x0D, byteArrayOf(mode.toByte()))
    fun setPixel(r: Int, g: Int, b: Int, col: Int, row: Int) =
        packet(0x04, byteArrayOf(r.toByte(), g.toByte(), b.toByte(), col.toByte(), row.toByte()))

    /** Transfer names observed from the PixelJoy app: 3 digits + "g9". */
    fun newTransferName(): String = "%03d".format(Random.nextInt(1000)) + "g9"

    fun uploadStart(name: String): ByteArray {
        val n = name.toByteArray(Charsets.US_ASCII)
        return packet(0x09, byteArrayOf(n.size.toByte()) + n)
    }

    fun uploadEndImage(name: String): ByteArray {
        val n = name.toByteArray(Charsets.US_ASCII)
        return packet(
            0x0B,
            byteArrayOf(0x01, n.size.toByte()) + n + byteArrayOf(0x02, 0x01, 0x00, 0x03, 0x02, 0x00, 0x00)
        )
    }

    fun uploadEndGif(name: String): ByteArray {
        val n = name.toByteArray(Charsets.US_ASCII)
        return packet(
            0x0C,
            byteArrayOf(0x01, n.size.toByte()) + n + byteArrayOf(0x03, 0x02, 0xC2.toByte(), 0x01)
        )
    }

    /**
     * A2 <sid> <total BE16> <seq BE16> 00 <paylen LE16> <payload> <CRC16(payload) LE>
     * First chunk payload is prefixed: 07 <filesize+6 LE16> 00 00 <namelen> <name>
     */
    fun uploadChunks(name: String, file: ByteArray, sid: Int = Random.nextInt(0x10, 0xFF)): List<ByteArray> {
        val n = name.toByteArray(Charsets.US_ASCII)
        val size = file.size + 6
        val meta = byteArrayOf(0x07, (size and 0xFF).toByte(), (size ushr 8).toByte(), 0x00, 0x00, n.size.toByte()) + n
        val stream = meta + file
        val total = (stream.size + CHUNK_PAYLOAD_MAX - 1) / CHUNK_PAYLOAD_MAX
        return (0 until total).map { i ->
            val start = i * CHUNK_PAYLOAD_MAX
            val end = minOf(start + CHUNK_PAYLOAD_MAX, stream.size)
            val len = end - start
            val seq = i + 1
            val out = ByteArray(9 + len + 2)
            out[0] = 0xA2.toByte()
            out[1] = sid.toByte()
            out[2] = (total ushr 8).toByte()
            out[3] = (total and 0xFF).toByte()
            out[4] = (seq ushr 8).toByte()
            out[5] = (seq and 0xFF).toByte()
            out[6] = 0x00
            out[7] = (len and 0xFF).toByte()
            out[8] = (len ushr 8).toByte()
            stream.copyInto(out, 9, start, end)
            val crc = crc16(stream, start, end)
            out[out.size - 2] = (crc and 0xFF).toByte()
            out[out.size - 1] = (crc ushr 8).toByte()
            out
        }
    }

    fun isResponse(p: ByteArray, cmd: Int): Boolean =
        p.size >= 3 && (p[0].toInt() and 0xFF) == 0xA1 && (p[1].toInt() and 0xFF) == cmd
}
