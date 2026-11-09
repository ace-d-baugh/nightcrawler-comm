package com.digitalelegance.autocomm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Movie
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

/** Source media picked for framing: one bitmap (image) or several (GIF), with per-frame delay. */
class SourceMedia(val frames: List<Bitmap>, val delay: Int)

object Media {
    private const val MAX_SRC = 1024

    fun load(ctx: Context, uri: Uri, maxFrames: Int): SourceMedia? = runCatching {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val isGif = bytes.size > 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()
        if (isGif && maxFrames > 1) decodeGif(bytes, maxFrames)?.let { return it }
        val src = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
        val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val s = min(1f, MAX_SRC / max(info.size.width, info.size.height).toFloat())
            if (s < 1f) dec.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
        }
        SourceMedia(listOf(bmp), 100)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun decodeGif(bytes: ByteArray, maxFrames: Int): SourceMedia? {
        val movie = Movie.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val mw = movie.width(); val mh = movie.height()
        if (mw <= 0 || mh <= 0) return null
        val dur = movie.duration()
        if (dur <= 0) return null
        val n = (dur / 80).coerceIn(1, maxFrames)
        val step = max(20, dur / n)
        val frames = (0 until n).map { k ->
            movie.setTime(k * step)
            Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888).also { movie.draw(Canvas(it), 0f, 0f) }
        }
        return SourceMedia(frames, step)
    }

    /**
     * Draw [src] into a w×h panel at [scale] (panel px per source px) and offset (tx, ty) in panel px.
     * Only what lands inside the frame is kept.
     */
    fun frame(src: Bitmap, w: Int, h: Int, scale: Float, tx: Float, ty: Float): IntArray {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply { setScale(scale, scale); postTranslate(tx, ty) }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        out.recycle()
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            px[i] = if (a < 100 || (c and 0xFFFFFF) == 0) 0 else c or (0xFF shl 24)
        }
        return px
    }

    fun thumbnail(px: IntArray, w: Int, h: Int): Bitmap {
        val o = IntArray(px.size) { if (px[it] ushr 24 == 0) 0xFF000000.toInt() else px[it] }
        return Bitmap.createBitmap(o, w, h, Bitmap.Config.ARGB_8888)
    }
}
