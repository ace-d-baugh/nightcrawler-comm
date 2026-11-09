package com.digitalelegance.autocomm.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/** Renders text to panel-sized PNG (fits) or scrolling GIF (too wide). */
object TextRenderer {

    class Rendered(val bytes: ByteArray, val isGif: Boolean)

    fun render(text: String, w: Int, h: Int, color: Int): Rendered {
        val paint = paintFor(h)
        val textW = paint.measureText(text)
        return if (textW <= w) Rendered(staticPng(text, w, h, color, paint), false)
        else Rendered(scrollingGif(text, w, h, color, paint), true)
    }

    private fun paintFor(h: Int): Paint {
        val p = Paint().apply {
            isAntiAlias = false // crisp LED pixels, 2-color GIF palette
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
        }
        val r = Rect()
        var size = h * 1.4f
        while (size > 6f) {
            p.textSize = size
            p.getTextBounds("HgyQ", 0, 4, r)
            if (r.height() <= h) break
            size -= 0.5f
        }
        return p
    }

    private fun baseline(p: Paint, h: Int): Float {
        val r = Rect()
        p.getTextBounds("HgyQ", 0, 4, r)
        return (h - r.height()) / 2f - r.top
    }

    private fun staticPng(text: String, w: Int, h: Int, color: Int, p: Paint): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setHasAlpha(false)
        val c = Canvas(bmp)
        c.drawColor(Color.BLACK)
        p.color = color
        c.drawText(text, (w - p.measureText(text)) / 2f, baseline(p, h), p)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun scrollingGif(text: String, w: Int, h: Int, color: Int, p: Paint): ByteArray {
        val textW = p.measureText(text).toInt() + 1
        val travel = textW + w
        var step = 2
        while (travel / step > MAX_FRAMES) step++

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val px = IntArray(w * h)
        val idx = ByteArray(w * h)
        val y = baseline(p, h)
        p.color = Color.WHITE
        val gif = GifEncoder(w, h, intArrayOf(0x000000, color and 0xFFFFFF))

        var x = w
        while (x > -textW) {
            c.drawColor(Color.BLACK)
            c.drawText(text, x.toFloat(), y, p)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            for (i in px.indices) idx[i] = if ((px[i] and 0xFF) > 0x7F) 1 else 0
            gif.addFrame(idx, FRAME_DELAY_CS)
            x -= step
        }
        bmp.recycle()
        return gif.finish()
    }

    private const val MAX_FRAMES = 200
    private const val FRAME_DELAY_CS = 4
}
