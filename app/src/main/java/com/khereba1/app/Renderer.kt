package com.khereba1.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A text item measured and laid out, ready to draw. */
class BuiltText(
    val layout: StaticLayout,
    val paint: TextPaint,
    val pad: Float,
    val boxW: Float,
    val boxH: Float,
)

/** Width and height of a layer's (unrotated) box in document pixels. */
class LayerBox(val w: Float, val h: Float)

/** Maps document pixels to view pixels (uniform scale + centering offset). */
class Fit(val scale: Float, val ox: Float, val oy: Float)

fun fitDoc(viewW: Float, viewH: Float, docW: Int, docH: Int): Fit {
    val margin = 12f
    val s = min((viewW - 2f * margin) / docW, (viewH - 2f * margin) / docH)
    val scale = if (s > 0.01f) s else 0.01f
    return Fit(scale, (viewW - docW * scale) / 2f, (viewH - docH * scale) / 2f)
}

/**
 * Draws a [Project]. The same code paints the on-screen preview and the exported image,
 * so what you see is what you save.
 */
object Renderer {

    fun build(item: Layer, docW: Int): BuiltText {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = max(4f, item.size)
        paint.typeface = FontStore.typeface(item.fontKey, item.bold, item.italic)
        val text: CharSequence = if (item.text.isEmpty()) " " else item.text
        val pad = if (item.bgOn) item.bgPad * item.size else 0f
        val maxW = max(40, (docW * 0.94f - 2f * pad).toInt())
        val desired = Layout.getDesiredWidth(text, paint)
        val w = max(1, min(ceil(desired.toDouble()).toInt() + 2, maxW))
        val alignment = when (item.align) {
            ALIGN_START -> Layout.Alignment.ALIGN_NORMAL
            ALIGN_END -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
            .setAlignment(alignment)
            .setLineSpacing(0f, item.lineSpacing)
            .build()
        val boxW = layout.width.toFloat() + 2f * pad
        val boxH = layout.height.toFloat() + 2f * pad
        return BuiltText(layout, paint, pad, boxW, boxH)
    }

    fun boxOf(item: Layer, docW: Int): LayerBox {
        if (item.type != TYPE_TEXT) return LayerBox(item.size, item.size * item.aspect)
        val b = build(item, docW)
        return LayerBox(b.boxW, b.boxH)
    }

    private fun drawLayer(canvas: Canvas, item: Layer, docW: Int, images: Map<String, Bitmap>) {
        if (!item.visible) return
        when (item.type) {
            TYPE_SHAPE -> drawShape(canvas, item)
            TYPE_IMAGE -> drawImage(canvas, item, images[item.imageKey])
            else -> drawText(canvas, item, docW)
        }
    }

    private fun drawImage(canvas: Canvas, item: Layer, bmp: Bitmap?) {
        if (bmp == null || bmp.isRecycled) return
        val w = item.size
        val h = item.size * item.aspect
        val saved = canvas.save()
        canvas.translate(item.cx, item.cy)
        canvas.rotate(item.rotation)
        if (item.flipX) canvas.scale(-1f, 1f)
        val rect = RectF(-w / 2f, -h / 2f, w / 2f, h / 2f)
        val r = min(item.corner, 0.5f) * min(w, h)
        if (r > 0.5f) {
            val clip = android.graphics.Path()
            clip.addRoundRect(rect, r, r, android.graphics.Path.Direction.CW)
            canvas.clipPath(clip)
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        paint.alpha = (item.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        canvas.drawBitmap(bmp, null, rect, paint)
        canvas.restoreToCount(saved)
    }

    private fun drawShape(canvas: Canvas, item: Layer) {
        val w = item.size
        val h = item.size * item.aspect
        val path = Shapes.build(item.shapeKind, w, h, item.corner)
        val saved = canvas.save()
        canvas.translate(item.cx, item.cy)
        canvas.rotate(item.rotation)
        canvas.translate(-w / 2f, -h / 2f)

        val alpha = (item.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        var layerSaved = -1
        if (alpha < 255) {
            val bleed = max(w, h) * 0.4f
            layerSaved = canvas.saveLayerAlpha(-bleed, -bleed, w + bleed, h + bleed, alpha)
        }
        val shadowRadius = max(0.5f, item.shadowBlur * item.size)
        val shadowDx = item.shadowDx * item.size
        val shadowDy = item.shadowDy * item.size
        val hasStroke = item.strokeOn && item.strokeRel > 0f

        if (item.fillOn) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            fill.style = Paint.Style.FILL
            fill.color = item.color
            if (item.shadowOn) fill.setShadowLayer(shadowRadius, shadowDx, shadowDy, item.shadowColor)
            canvas.drawPath(path, fill)
        }
        if (hasStroke) {
            val line = Paint(Paint.ANTI_ALIAS_FLAG)
            line.style = Paint.Style.STROKE
            line.strokeJoin = Paint.Join.ROUND
            line.strokeWidth = item.strokeRel * item.size
            line.color = item.strokeColor
            if (item.shadowOn && !item.fillOn) line.setShadowLayer(shadowRadius, shadowDx, shadowDy, item.shadowColor)
            canvas.drawPath(path, line)
        }
        if (layerSaved >= 0) canvas.restoreToCount(layerSaved)
        canvas.restoreToCount(saved)
    }

    private fun drawText(canvas: Canvas, item: Layer, docW: Int) {
        val b = build(item, docW)
        val saved = canvas.save()
        canvas.translate(item.cx, item.cy)
        canvas.rotate(item.rotation)
        canvas.translate(-b.boxW / 2f, -b.boxH / 2f)

        val alpha = (item.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        var layerSaved = -1
        if (alpha < 255) {
            val bleed = item.size * 0.8f
            layerSaved = canvas.saveLayerAlpha(-bleed, -bleed, b.boxW + bleed, b.boxH + bleed, alpha)
        }

        if (item.bgOn) {
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            boxPaint.style = Paint.Style.FILL
            boxPaint.color = item.bgColor
            val r = min(item.bgRadius * item.size, min(b.boxW, b.boxH) / 2f)
            canvas.drawRoundRect(RectF(0f, 0f, b.boxW, b.boxH), r, r, boxPaint)
        }

        canvas.translate(b.pad, b.pad)
        val p = b.paint
        val shadowRadius = max(0.5f, item.shadowBlur * item.size)
        val shadowDx = item.shadowDx * item.size
        val shadowDy = item.shadowDy * item.size

        if (item.strokeOn && item.strokeRel > 0f) {
            p.style = Paint.Style.STROKE
            p.strokeJoin = Paint.Join.ROUND
            p.strokeWidth = item.strokeRel * item.size * 2f
            p.color = item.strokeColor
            if (item.shadowOn) p.setShadowLayer(shadowRadius, shadowDx, shadowDy, item.shadowColor) else p.clearShadowLayer()
            b.layout.draw(canvas)
            p.style = Paint.Style.FILL
            p.clearShadowLayer()
            p.color = item.color
            b.layout.draw(canvas)
        } else {
            p.style = Paint.Style.FILL
            p.color = item.color
            if (item.shadowOn) p.setShadowLayer(shadowRadius, shadowDx, shadowDy, item.shadowColor) else p.clearShadowLayer()
            b.layout.draw(canvas)
        }

        if (layerSaved >= 0) canvas.restoreToCount(layerSaved)
        canvas.restoreToCount(saved)
    }

    private fun drawSelection(canvas: Canvas, item: Layer, docW: Int, invScale: Float, color: Int) {
        val b = boxOf(item, docW)
        val saved = canvas.save()
        canvas.translate(item.cx, item.cy)
        canvas.rotate(item.rotation)
        val line = Paint(Paint.ANTI_ALIAS_FLAG)
        line.style = Paint.Style.STROKE
        line.strokeWidth = 2f * invScale
        line.color = color
        val l = -b.w / 2f
        val t = -b.h / 2f
        val r = b.w / 2f
        val bt = b.h / 2f
        canvas.drawRect(l, t, r, bt, line)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        dot.style = Paint.Style.FILL
        dot.color = 0xFFFFFFFF.toInt()
        val ring = Paint(Paint.ANTI_ALIAS_FLAG)
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 2f * invScale
        ring.color = color
        val rad = 5.5f * invScale
        val xs = floatArrayOf(l, r, l, r)
        val ys = floatArrayOf(t, t, bt, bt)
        for (i in 0 until 4) {
            canvas.drawCircle(xs[i], ys[i], rad, dot)
            canvas.drawCircle(xs[i], ys[i], rad, ring)
        }
        canvas.restoreToCount(saved)
    }

    /** Paints the whole document at 1 doc pixel = 1 canvas pixel (caller applies any view scale). */
    fun drawDocument(
        canvas: Canvas,
        p: Project,
        photo: Bitmap?,
        images: Map<String, Bitmap>,
        selectedId: Long?,
        invScale: Float,
        selectionColor: Int,
    ) {
        val saved = canvas.save()
        canvas.clipRect(0f, 0f, p.docW.toFloat(), p.docH.toFloat())
        canvas.drawColor(p.bgColor)
        if (photo != null && !photo.isRecycled) {
            val pp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(photo, null, RectF(0f, 0f, p.docW.toFloat(), p.docH.toFloat()), pp)
        }
        for (item in p.items) drawLayer(canvas, item, p.docW, images)
        canvas.restoreToCount(saved)
        if (selectedId != null) {
            for (item in p.items) {
                if (item.id == selectedId && item.visible) drawSelection(canvas, item, p.docW, invScale, selectionColor)
            }
        }
    }

    /** Returns the id of the top-most item under the document point (x, y), or null. */
    fun hitTest(p: Project, x: Float, y: Float, tolerance: Float): Long? {
        for (i in p.items.indices.reversed()) {
            val item = p.items[i]
            if (!item.visible || item.locked) continue
            val b = boxOf(item, p.docW)
            val dx = x - item.cx
            val dy = y - item.cy
            val a = Math.toRadians(-item.rotation.toDouble())
            val c = cos(a)
            val s = sin(a)
            val lx = (dx * c - dy * s).toFloat()
            val ly = (dx * s + dy * c).toFloat()
            if (abs(lx) <= b.w / 2f + tolerance && abs(ly) <= b.h / 2f + tolerance) return item.id
        }
        return null
    }

    /** Renders the final image. Returns null if there is not enough memory. */
    fun exportBitmap(p: Project, photo: Bitmap?, images: Map<String, Bitmap>): Bitmap? {
        return try {
            val out = Bitmap.createBitmap(p.docW, p.docH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            drawDocument(canvas, p, photo, images, null, 1f, 0)
            out
        } catch (e: OutOfMemoryError) {
            AppLog.log("EXPORT", "Out of memory while rendering " + p.docW + "x" + p.docH, null)
            null
        }
    }
}
