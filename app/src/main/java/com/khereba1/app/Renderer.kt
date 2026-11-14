package com.khereba1.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
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
    val curved: List<CurvedLine>? = null,
)

/** One line of curved text with the arc it follows (already positioned inside the box). */
class CurvedLine(val text: String, val path: Path)

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
        paint.letterSpacing = item.letterSpacing
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
        if (abs(item.curve) >= 0.01f && item.text.isNotEmpty()) return buildCurved(item, paint, pad, layout)
        val boxW = layout.width.toFloat() + 2f * pad
        val boxH = layout.height.toFloat() + 2f * pad
        return BuiltText(layout, paint, pad, boxW, boxH)
    }

    /** Lays each line of the text along a circular arc. Rainbow (curve > 0) or smile (curve < 0). */
    private fun buildCurved(item: Layer, paint: TextPaint, pad: Float, layout: StaticLayout): BuiltText {
        val lines = item.text.split("\n")
        val n = lines.size
        val widths = FloatArray(n)
        var maxW = 1f
        for (i in 0 until n) {
            widths[i] = paint.measureText(lines[i])
            if (widths[i] > maxW) maxW = widths[i]
        }
        val fm = paint.fontMetrics
        val asc = -fm.ascent
        val dsc = fm.descent
        val lineH = (asc + dsc) * item.lineSpacing
        val theta = abs(item.curve).coerceIn(0.01f, 1f) * Math.PI.toFloat()
        val r0 = maxW / theta
        val rainbow = item.curve > 0f
        val paths = ArrayList<Path>()
        val bounds = RectF()
        var first = true
        for (i in 0 until n) {
            val r = if (rainbow) r0 + (n - 1 - i) * lineH else r0 + i * lineH
            val sweep = Math.toDegrees((widths[i] / r).toDouble()).toFloat()
            val path = Path()
            val oval = RectF(-r, -r, r, r)
            if (rainbow) path.addArc(oval, -90f - sweep / 2f, sweep) else path.addArc(oval, 90f + sweep / 2f, -sweep)
            if (lines[i].isNotEmpty()) {
                val bb = RectF()
                path.computeBounds(bb, true)
                if (first) {
                    bounds.set(bb)
                    first = false
                } else {
                    bounds.union(bb)
                }
            }
            paths.add(path)
        }
        if (first) return BuiltText(layout, paint, pad, layout.width.toFloat() + 2f * pad, layout.height.toFloat() + 2f * pad)
        bounds.top -= asc
        bounds.bottom += dsc
        bounds.left -= asc * 0.6f
        bounds.right += asc * 0.6f
        val out = ArrayList<CurvedLine>()
        for (i in 0 until n) {
            paths[i].offset(-bounds.left, -bounds.top)
            out.add(CurvedLine(lines[i], paths[i]))
        }
        return BuiltText(layout, paint, pad, bounds.width() + 2f * pad, bounds.height() + 2f * pad, out)
    }

    private fun drawGlyphs(canvas: Canvas, b: BuiltText) {
        val c = b.curved
        if (c == null) {
            b.layout.draw(canvas)
        } else {
            for (l in c) if (l.text.isNotEmpty()) canvas.drawTextOnPath(l.text, l.path, 0f, 0f, b.paint)
        }
    }

    fun boxOf(item: Layer, docW: Int): LayerBox {
        if (item.type != TYPE_TEXT) return LayerBox(item.size, item.size * item.aspect)
        val b = build(item, docW)
        return LayerBox(b.boxW, b.boxH)
    }

    /** Draws one layer (used by previews such as the text style tiles). */
    fun drawItem(canvas: Canvas, item: Layer, docW: Int, docH: Int) {
        drawLayer(canvas, item, docW, docH, emptyMap())
    }

    private fun gradientShader(item: Layer, w: Float, h: Float): Shader {
        val a = Math.toRadians(item.gradAngle.toDouble())
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val half = (abs(w * dx) + abs(h * dy)) / 2f
        val cx = w / 2f
        val cy = h / 2f
        return LinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, item.color, item.gradColor, Shader.TileMode.CLAMP)
    }

    /** Smooth brush stroke (also used for the live preview while drawing). */
    fun drawStroke(canvas: Canvas, s: Stroke) {
        val n = s.pts.size / 2
        if (n == 0) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = s.color
        if (n == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(s.pts[0], s.pts[1], s.width / 2f, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = s.width
        val path = Path()
        path.moveTo(s.pts[0], s.pts[1])
        for (i in 1 until n - 1) {
            val x = s.pts[2 * i]
            val y = s.pts[2 * i + 1]
            val nx = s.pts[2 * i + 2]
            val ny = s.pts[2 * i + 3]
            path.quadTo(x, y, (x + nx) / 2f, (y + ny) / 2f)
        }
        path.lineTo(s.pts[2 * n - 2], s.pts[2 * n - 1])
        canvas.drawPath(path, paint)
    }

    private fun drawStrokes(canvas: Canvas, item: Layer, docW: Int, docH: Int) {
        val alpha = (item.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        var layerSaved = -1
        if (alpha < 255) layerSaved = canvas.saveLayerAlpha(0f, 0f, docW.toFloat(), docH.toFloat(), alpha)
        for (s in item.strokes) drawStroke(canvas, s)
        if (layerSaved >= 0) canvas.restoreToCount(layerSaved)
    }

    private fun colorMatrixOf(p: Project): ColorMatrix? {
        if (p.filter == 0 && p.bright == 0f && p.contrast == 1f && p.sat == 1f) return null
        val cm = ColorMatrix()
        cm.setSaturation(p.sat)
        when (p.filter) {
            1 -> cm.postConcat(ColorMatrix().also { it.setSaturation(0f) })
            2 -> cm.postConcat(ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f)))
            3 -> cm.postConcat(ColorMatrix().also { it.setSaturation(1.45f) })
            4 -> cm.postConcat(ColorMatrix(floatArrayOf(
                0.92f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1.1f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f)))
            5 -> cm.postConcat(ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 6f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0.9f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f)))
            6 -> cm.postConcat(ColorMatrix(floatArrayOf(
                0.85f, 0f, 0f, 0f, 28f,
                0f, 0.85f, 0f, 0f, 28f,
                0f, 0f, 0.85f, 0f, 28f,
                0f, 0f, 0f, 1f, 0f)))
        }
        val c = p.contrast
        val t = (1f - c) * 128f + p.bright * 100f
        cm.postConcat(ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f)))
        return cm
    }

    private fun drawLayer(canvas: Canvas, item: Layer, docW: Int, docH: Int, images: Map<String, Bitmap>) {
        if (!item.visible) return
        when (item.type) {
            TYPE_DRAW -> drawStrokes(canvas, item, docW, docH)
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
            drawGlyphs(canvas, b)
            p.style = Paint.Style.FILL
            p.clearShadowLayer()
            p.color = item.color
            if (item.gradOn) p.shader = gradientShader(item, b.boxW - 2f * b.pad, b.boxH - 2f * b.pad)
            drawGlyphs(canvas, b)
            p.shader = null
        } else {
            p.style = Paint.Style.FILL
            p.color = item.color
            if (item.gradOn) p.shader = gradientShader(item, b.boxW - 2f * b.pad, b.boxH - 2f * b.pad)
            if (item.shadowOn) p.setShadowLayer(shadowRadius, shadowDx, shadowDy, item.shadowColor) else p.clearShadowLayer()
            drawGlyphs(canvas, b)
            p.shader = null
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
            val cm = colorMatrixOf(p)
            if (cm != null) pp.colorFilter = ColorMatrixColorFilter(cm)
            val fw = if (p.fullW > 0) p.fullW.toFloat() else p.docW.toFloat()
            val fh = if (p.fullH > 0) p.fullH.toFloat() else p.docH.toFloat()
            canvas.drawBitmap(photo, null, RectF(-p.offX, -p.offY, -p.offX + fw, -p.offY + fh), pp)
        }
        for (item in p.items) drawLayer(canvas, item, p.docW, p.docH, images)
        canvas.restoreToCount(saved)
        if (selectedId != null) {
            for (item in p.items) {
                if (item.id == selectedId && item.visible && item.type != TYPE_DRAW) drawSelection(canvas, item, p.docW, invScale, selectionColor)
            }
        }
    }

    /** Returns the id of the top-most item under the document point (x, y), or null. */
    fun hitTest(p: Project, x: Float, y: Float, tolerance: Float): Long? {
        for (i in p.items.indices.reversed()) {
            val item = p.items[i]
            if (!item.visible || item.locked || item.type == TYPE_DRAW) continue
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

    /** A small copy of the finished picture, longest side [maxSide], for the project list. */
    fun thumbnail(p: Project, photo: Bitmap?, images: Map<String, Bitmap>, maxSide: Int): Bitmap? {
        return try {
            val s = maxSide.toFloat() / max(p.docW, p.docH).toFloat()
            val w = max(1, (p.docW * s).roundToInt())
            val h = max(1, (p.docH * s).roundToInt())
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.scale(w.toFloat() / p.docW, h.toFloat() / p.docH)
            drawDocument(canvas, p, photo, images, null, 1f, 0)
            out
        } catch (e: Throwable) {
            AppLog.log("EXPORT", "Thumbnail failed", e)
            null
        }
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
