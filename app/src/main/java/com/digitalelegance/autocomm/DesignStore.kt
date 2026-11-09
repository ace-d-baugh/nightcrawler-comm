package com.digitalelegance.autocomm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min

class MessageStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "messages").apply { mkdirs() }
    private val legacyDir = File(ctx.filesDir, "designs")

    fun list(): List<Message> = (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
        .sortedBy { it.lastModified() }
        .mapNotNull { runCatching { decode(JSONObject(it.readText())) }.getOrNull() }

    /** Designs saved by the previous app version (image/animation only). */
    fun legacyDesigns(): List<Message> = (legacyDir.listFiles { f -> f.extension == "json" } ?: emptyArray())
        .sortedBy { it.lastModified() }
        .mapNotNull { f ->
            runCatching {
                val o = JSONObject(f.readText())
                val w = o.getInt("w"); val h = o.getInt("h")
                val a = o.getJSONArray("frames")
                val frames = (0 until a.length()).map { unpackFrame(a.getString(it), w * h) }
                val d = o.optInt("delay", 120)
                Message(
                    o.getString("id"), o.optString("name", "Design"),
                    if (o.optString("type") == "ANIMATION") MsgKind.ANIMATION else MsgKind.IMAGE,
                    Target.BOTH, style = TextStyle(effect = Effect.HOLD), w = w, h = h, frames = frames, delays = List(frames.size) { d },
                )
            }.getOrNull()
        }

    fun save(m: Message) {
        val o = JSONObject()
            .put("id", m.id).put("name", m.name).put("kind", m.kind.name).put("target", m.target.name)
            .put("text", m.text).put("style", m.style.toJson()).put("fxv", 1)
            .put("w", m.w).put("h", m.h)
            .put("delays", JSONArray().apply { m.delays.forEach { put(it) } })
            .put("frames", JSONArray().apply { m.frames.forEach { put(packFrame(it)) } })
        File(dir, "${m.id}.json").writeText(o.toString())
    }

    fun delete(id: String) { File(dir, "$id.json").delete() }

    private fun decode(o: JSONObject): Message {
        val w = o.optInt("w"); val h = o.optInt("h")
        val a = o.optJSONArray("frames") ?: JSONArray()
        val d = o.optJSONArray("delays") ?: JSONArray()
        val kind = enumOr(o.optString("kind"), MsgKind.TEXT)
        var style = TextStyle.fromJson(o.optJSONObject("style"))
        if (kind != MsgKind.TEXT && !o.has("fxv")) style = style.copy(effect = Effect.HOLD)
        return Message(
            o.getString("id"), o.optString("name", "Message"),
            kind, enumOr(o.optString("target"), Target.BOTH),
            o.optString("text", ""), style, w, h,
            (0 until a.length()).map { unpackFrame(a.getString(it), w * h) },
            (0 until d.length()).map { d.getInt(it) },
        )
    }

    // 4 bytes per pixel: on-flag + RGB
    private fun packFrame(px: IntArray): String {
        val b = ByteArray(px.size * 4)
        px.forEachIndexed { i, c ->
            b[i * 4] = if (c ushr 24 == 0) 0 else 1
            b[i * 4 + 1] = (c shr 16).toByte(); b[i * 4 + 2] = (c shr 8).toByte(); b[i * 4 + 3] = c.toByte()
        }
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    private fun unpackFrame(s: String, n: Int): IntArray {
        val b = Base64.decode(s, Base64.NO_WRAP)
        return IntArray(n) { i ->
            if (i * 4 + 3 >= b.size || b[i * 4].toInt() == 0) 0
            else (0xFF shl 24) or ((b[i * 4 + 1].toInt() and 0xFF) shl 16) or ((b[i * 4 + 2].toInt() and 0xFF) shl 8) or (b[i * 4 + 3].toInt() and 0xFF)
        }
    }
}

/** Source media picked for framing: one bitmap (image) or several (GIF) with per-frame delays (ms). */
class SourceMedia(val frames: List<Bitmap>, val delays: List<Int>, val truncated: Boolean = false)

object Media {
    private const val MAX_SRC = 1024      // still images: longest side
    private const val MAX_GIF_SIDE = 320  // GIF frames are kept small to save memory
    const val MAX_DECODE_FRAMES = 400

    fun load(ctx: Context, uri: Uri, animated: Boolean): SourceMedia? = runCatching {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val isGif = bytes.size > 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()
        if (isGif && animated) decodeGif(bytes)?.takeIf { it.frames.isNotEmpty() }?.let { return it }
        val src = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
        val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val s = min(1f, MAX_SRC / max(info.size.width, info.size.height).toFloat())
            if (s < 1f) dec.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
        }
        SourceMedia(listOf(bmp), listOf(1000))
    }.getOrNull()

    private fun decodeGif(bytes: ByteArray): SourceMedia? {
        if (bytes.size < 13) return null
        val gw = (bytes[6].toInt() and 0xFF) or ((bytes[7].toInt() and 0xFF) shl 8)
        val gh = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
        if (gw <= 0 || gh <= 0) return null
        val s = min(1f, MAX_GIF_SIDE / max(gw, gh).toFloat())
        val tw = max(1, (gw * s).toInt())
        val th = max(1, (gh * s).toInt())
        val full = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        val frames = ArrayList<Bitmap>()
        val delays = ArrayList<Int>()
        val info = GifDecoder.decode(bytes, MAX_DECODE_FRAMES + 1) { canvas, delay ->
            if (frames.size < MAX_DECODE_FRAMES) {
                full.setPixels(canvas, 0, gw, 0, 0, gw, gh)
                frames += if (s < 1f) Bitmap.createScaledBitmap(full, tw, th, true) else full.copy(Bitmap.Config.ARGB_8888, false)
                delays += delay
            }
        }
        full.recycle()
        if (info == null) return null
        return SourceMedia(frames, delays, truncated = info.third > MAX_DECODE_FRAMES)
    }

    /**
     * Draw [src] into a w×h panel: centered at (cx, cy) in panel px, scaled by [zoom]
     * (panel px per source px) and rotated [deg]. Only what lands inside the frame is kept;
     * near-black counts as off.
     */
    fun frame(src: Bitmap, w: Int, h: Int, cx: Float, cy: Float, zoom: Float, deg: Float): IntArray {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply {
            setTranslate(-src.width / 2f, -src.height / 2f)
            postScale(zoom, zoom)
            postRotate(deg)
            postTranslate(cx, cy)
        }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        out.recycle()
        for (i in px.indices) {
            val c = px[i]
            val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
            px[i] = if ((c ushr 24) < 100 || (r < 8 && g < 8 && b < 8)) 0 else c or (0xFF shl 24)
        }
        return px
    }

    fun thumbnail(px: IntArray, w: Int, h: Int): Bitmap {
        val o = IntArray(px.size) { if (px[it] ushr 24 == 0) 0xFF000000.toInt() else px[it] }
        return Bitmap.createBitmap(o, w, h, Bitmap.Config.ARGB_8888)
    }
}
