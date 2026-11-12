package com.khereba1.app

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Original vector shapes, built as android.graphics.Path inside a w x h box. */
object Shapes {
    const val COUNT = 20

    const val K_RECT = 0

    /** Height / width a newly added shape starts with. */
    fun defaultAspect(kind: Int): Float = when (kind) {
        10 -> 0.6f // arrow
        12 -> 0.85f // speech bubble
        13 -> 0.7f // cloud
        19 -> 0.65f // parallelogram
        K_RECT -> 0.7f
        else -> 1f
    }

    /** Only the rectangle uses the corner-radius setting. */
    fun usesCorner(kind: Int): Boolean = kind == K_RECT

    private fun poly(w: Float, h: Float, vararg p: Float): Path {
        val path = Path()
        path.moveTo(p[0] * w, p[1] * h)
        var i = 2
        while (i + 1 < p.size) {
            path.lineTo(p[i] * w, p[i + 1] * h)
            i += 2
        }
        path.close()
        return path
    }

    /** Regular polygon inscribed in the ellipse of the box. */
    private fun regular(n: Int, w: Float, h: Float, startDeg: Double): Path {
        val path = Path()
        for (i in 0 until n) {
            val a = Math.toRadians(startDeg + 360.0 * i / n)
            val x = w / 2f + (w / 2f) * cos(a).toFloat()
            val y = h / 2f + (h / 2f) * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun star(points: Int, inner: Float, w: Float, h: Float): Path {
        val path = Path()
        val total = points * 2
        for (i in 0 until total) {
            val a = Math.toRadians(-90.0 + 360.0 * i / total)
            val r = if (i % 2 == 0) 1f else inner
            val x = w / 2f + (w / 2f) * r * cos(a).toFloat()
            val y = h / 2f + (h / 2f) * r * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun oval(w: Float, h: Float, l: Float, t: Float, r: Float, b: Float): Path {
        val p = Path()
        p.addOval(RectF(l * w, t * h, r * w, b * h), Path.Direction.CW)
        return p
    }

    private fun heart(w: Float, h: Float): Path {
        val sx = w / 100f
        val sy = h / 100f
        val p = Path()
        p.moveTo(50f * sx, 90f * sy)
        p.cubicTo(20f * sx, 68f * sy, 4f * sx, 50f * sy, 4f * sx, 32f * sy)
        p.cubicTo(4f * sx, 16f * sy, 16f * sx, 6f * sy, 29f * sx, 6f * sy)
        p.cubicTo(38f * sx, 6f * sy, 46f * sx, 11f * sy, 50f * sx, 19f * sy)
        p.cubicTo(54f * sx, 11f * sy, 62f * sx, 6f * sy, 71f * sx, 6f * sy)
        p.cubicTo(84f * sx, 6f * sy, 96f * sx, 16f * sy, 96f * sx, 32f * sy)
        p.cubicTo(96f * sx, 50f * sy, 80f * sx, 68f * sy, 50f * sx, 90f * sy)
        p.close()
        return p
    }

    fun build(kind: Int, w: Float, h: Float, corner: Float): Path {
        return when (kind) {
            0 -> {
                val p = Path()
                val r = corner.coerceIn(0f, 0.5f) * min(w, h)
                p.addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)
                p
            }
            1 -> oval(w, h, 0f, 0f, 1f, 1f)
            2 -> poly(w, h, 0.5f, 0f, 1f, 1f, 0f, 1f)
            3 -> poly(w, h, 0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f)
            4 -> regular(5, w, h, -90.0)
            5 -> regular(6, w, h, 0.0)
            6 -> star(5, 0.42f, w, h)
            7 -> star(4, 0.24f, w, h)
            8 -> star(12, 0.82f, w, h)
            9 -> heart(w, h)
            10 -> poly(w, h, 0f, 0.3f, 0.6f, 0.3f, 0.6f, 0.05f, 1f, 0.5f, 0.6f, 0.95f, 0.6f, 0.7f, 0f, 0.7f)
            11 -> poly(w, h, 0.58f, 0f, 0.2f, 0.55f, 0.47f, 0.55f, 0.38f, 1f, 0.82f, 0.42f, 0.55f, 0.42f)
            12 -> {
                val body = Path()
                val bh = h * 0.78f
                val r = 0.18f * min(w, bh)
                body.addRoundRect(RectF(0f, 0f, w, bh), r, r, Path.Direction.CW)
                val tail = poly(w, h, 0.16f, 0.68f, 0.1f, 1f, 0.42f, 0.68f)
                body.op(tail, Path.Op.UNION)
                body
            }
            13 -> {
                val base = Path()
                val r = 0.18f * h
                base.addRoundRect(RectF(0.2f * w, 0.5f * h, 0.8f * w, 0.82f * h), r, r, Path.Direction.CW)
                base.op(oval(w, h, 0.1f, 0.38f, 0.45f, 0.82f), Path.Op.UNION)
                base.op(oval(w, h, 0.3f, 0.12f, 0.7f, 0.66f), Path.Op.UNION)
                base.op(oval(w, h, 0.52f, 0.32f, 0.92f, 0.82f), Path.Op.UNION)
                base
            }
            14 -> poly(w, h, 0.35f, 0f, 0.65f, 0f, 0.65f, 0.35f, 1f, 0.35f, 1f, 0.65f, 0.65f, 0.65f, 0.65f, 1f, 0.35f, 1f, 0.35f, 0.65f, 0f, 0.65f, 0f, 0.35f, 0.35f, 0.35f)
            15 -> poly(w, h, 0.04f, 0.54f, 0.17f, 0.41f, 0.38f, 0.62f, 0.83f, 0.12f, 0.96f, 0.25f, 0.38f, 0.9f)
            16 -> {
                val p = Path()
                p.fillType = Path.FillType.EVEN_ODD
                p.addOval(RectF(0f, 0f, w, h), Path.Direction.CW)
                p.addOval(RectF(0.28f * w, 0.28f * h, 0.72f * w, 0.72f * h), Path.Direction.CW)
                p
            }
            17 -> {
                val outer = oval(w, h, 0f, 0f, 1f, 1f)
                val cut = oval(w, h, 0.3f, 0.05f, 1.08f, 0.95f)
                outer.op(cut, Path.Op.DIFFERENCE)
                outer
            }
            18 -> {
                val p = Path()
                p.moveTo(0.5f * w, 0f)
                p.cubicTo(0.5f * w, 0f, 0.92f * w, 0.45f * h, 0.92f * w, 0.65f * h)
                p.cubicTo(0.92f * w, 0.88f * h, 0.73f * w, h, 0.5f * w, h)
                p.cubicTo(0.27f * w, h, 0.08f * w, 0.88f * h, 0.08f * w, 0.65f * h)
                p.cubicTo(0.08f * w, 0.45f * h, 0.5f * w, 0f, 0.5f * w, 0f)
                p.close()
                p
            }
            else -> poly(w, h, 0.22f, 0f, 1f, 0f, 0.78f, 1f, 0f, 1f)
        }
    }
}
