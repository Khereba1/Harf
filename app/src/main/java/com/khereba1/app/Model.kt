package com.khereba1.app

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

const val ALIGN_START = 0
const val ALIGN_CENTER = 1
const val ALIGN_END = 2

const val TYPE_TEXT = 0
const val TYPE_SHAPE = 1
const val TYPE_IMAGE = 2
const val TYPE_DRAW = 3

/** One brush stroke: points are x0,y0,x1,y1,... in document pixels. */
class Stroke(val color: Int, val width: Float, val pts: FloatArray)

/**
 * One layer on the canvas: a text (emoji are texts too) or a vector shape.
 * Coordinates (cx, cy) and [size] are in document pixels (the pixels of the photo / canvas).
 * For text, [size] is the font size. For a shape it is the width, and the height is size * aspect.
 * All "Rel" values are relative to [size], so pinch-resizing scales outline, shadow and box with it.
 */
data class Layer(
    val id: Long,
    val type: Int = TYPE_TEXT,
    val cx: Float,
    val cy: Float,
    val size: Float,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val locked: Boolean = false,
    val visible: Boolean = true,
    // --- text ---
    val text: String = "",
    val fontKey: String = "sys:sans-serif",
    val color: Int = Color.WHITE, // text color, or the fill color of a shape
    val bold: Boolean = false,
    val italic: Boolean = false,
    val align: Int = ALIGN_CENTER,
    val lineSpacing: Float = 1f,
    val letterSpacing: Float = 0f, // in em
    val curve: Float = 0f, // -1..1, text bent along an arc (+ rainbow, - smile)
    val gradOn: Boolean = false,
    val gradColor: Int = 0xFFFFC107.toInt(),
    val gradAngle: Float = 90f,
    // --- shape ---
    val shapeKind: Int = 0,
    val aspect: Float = 1f,
    val corner: Float = 0.2f,
    val fillOn: Boolean = true,
    // --- drawing (type = TYPE_DRAW) ---
    val strokes: List<Stroke> = emptyList(),
    // --- image (size = width, height = size * aspect; corner = rounding) ---
    val imageKey: String = "",
    val flipX: Boolean = false,
    // --- outline / shadow (text and shapes) ---
    val strokeOn: Boolean = false,
    val strokeColor: Int = Color.BLACK,
    val strokeRel: Float = 0.06f,
    val shadowOn: Boolean = false,
    val shadowColor: Int = 0xB3000000.toInt(),
    val shadowBlur: Float = 0.06f,
    val shadowDx: Float = 0.03f,
    val shadowDy: Float = 0.03f,
    // --- background box (text only) ---
    val bgOn: Boolean = false,
    val bgColor: Int = 0xCC000000.toInt(),
    val bgPad: Float = 0.3f,
    val bgRadius: Float = 0.25f,
)

/** The whole document. Immutable, so undo/redo is just a list of snapshots. Layers are bottom to top. */
data class Project(
    val docW: Int,
    val docH: Int,
    val bgColor: Int,
    val hasPhoto: Boolean,
    val items: List<Layer>,
    val nextId: Long,
    val bright: Float = 0f,
    val contrast: Float = 1f,
    val sat: Float = 1f,
    val filter: Int = 0,
    // crop: the photo is drawn at (-offX, -offY) with its original size (fullW x fullH; 0 = same as docW x docH)
    val offX: Float = 0f,
    val offY: Float = 0f,
    val fullW: Int = 0,
    val fullH: Int = 0,
)

private fun JSONObject.f(key: String, def: Float): Float = optDouble(key, def.toDouble()).toFloat()

fun layerToJson(t: Layer): JSONObject {
    val o = JSONObject()
    o.put("id", t.id)
    o.put("type", t.type)
    o.put("cx", t.cx.toDouble())
    o.put("cy", t.cy.toDouble())
    o.put("size", t.size.toDouble())
    o.put("rotation", t.rotation.toDouble())
    o.put("opacity", t.opacity.toDouble())
    o.put("locked", t.locked)
    o.put("visible", t.visible)
    o.put("text", t.text)
    o.put("fontKey", t.fontKey)
    o.put("color", t.color)
    o.put("bold", t.bold)
    o.put("italic", t.italic)
    o.put("align", t.align)
    o.put("lineSpacing", t.lineSpacing.toDouble())
    o.put("letterSpacing", t.letterSpacing.toDouble())
    o.put("curve", t.curve.toDouble())
    o.put("gradOn", t.gradOn)
    o.put("gradColor", t.gradColor)
    o.put("gradAngle", t.gradAngle.toDouble())
    if (t.strokes.isNotEmpty()) {
        val sa = JSONArray()
        for (s in t.strokes) {
            val so = JSONObject()
            so.put("c", s.color)
            so.put("w", s.width.toDouble())
            val pa = JSONArray()
            for (v in s.pts) pa.put(Math.round(v * 10f) / 10.0)
            so.put("p", pa)
            sa.put(so)
        }
        o.put("strokes", sa)
    }
    o.put("shapeKind", t.shapeKind)
    o.put("aspect", t.aspect.toDouble())
    o.put("corner", t.corner.toDouble())
    o.put("fillOn", t.fillOn)
    o.put("imageKey", t.imageKey)
    o.put("flipX", t.flipX)
    o.put("strokeOn", t.strokeOn)
    o.put("strokeColor", t.strokeColor)
    o.put("strokeRel", t.strokeRel.toDouble())
    o.put("shadowOn", t.shadowOn)
    o.put("shadowColor", t.shadowColor)
    o.put("shadowBlur", t.shadowBlur.toDouble())
    o.put("shadowDx", t.shadowDx.toDouble())
    o.put("shadowDy", t.shadowDy.toDouble())
    o.put("bgOn", t.bgOn)
    o.put("bgColor", t.bgColor)
    o.put("bgPad", t.bgPad.toDouble())
    o.put("bgRadius", t.bgRadius.toDouble())
    return o
}

private fun strokesFromJson(arr: JSONArray?): List<Stroke> {
    if (arr == null) return emptyList()
    val out = ArrayList<Stroke>()
    for (i in 0 until arr.length()) {
        val so = arr.optJSONObject(i) ?: continue
        val pa = so.optJSONArray("p") ?: continue
        val pts = FloatArray(pa.length())
        for (j in 0 until pa.length()) pts[j] = pa.optDouble(j, 0.0).toFloat()
        out.add(Stroke(so.optInt("c", 0xFF000000.toInt()), so.optDouble("w", 8.0).toFloat(), pts))
    }
    return out
}

fun layerFromJson(o: JSONObject): Layer {
    val d = Layer(
        id = o.optLong("id", 0L),
        cx = o.f("cx", 0f),
        cy = o.f("cy", 0f),
        size = o.f("size", 80f),
    )
    return d.copy(
        type = o.optInt("type", d.type),
        rotation = o.f("rotation", d.rotation),
        opacity = o.f("opacity", d.opacity),
        locked = o.optBoolean("locked", d.locked),
        visible = o.optBoolean("visible", d.visible),
        text = o.optString("text", d.text),
        fontKey = o.optString("fontKey", d.fontKey),
        color = o.optInt("color", d.color),
        bold = o.optBoolean("bold", d.bold),
        italic = o.optBoolean("italic", d.italic),
        align = o.optInt("align", d.align),
        lineSpacing = o.f("lineSpacing", d.lineSpacing),
        letterSpacing = o.f("letterSpacing", d.letterSpacing),
        curve = o.f("curve", d.curve),
        gradOn = o.optBoolean("gradOn", d.gradOn),
        gradColor = o.optInt("gradColor", d.gradColor),
        gradAngle = o.f("gradAngle", d.gradAngle),
        strokes = strokesFromJson(o.optJSONArray("strokes")),
        shapeKind = o.optInt("shapeKind", d.shapeKind),
        aspect = o.f("aspect", d.aspect),
        corner = o.f("corner", d.corner),
        fillOn = o.optBoolean("fillOn", d.fillOn),
        imageKey = o.optString("imageKey", d.imageKey),
        flipX = o.optBoolean("flipX", d.flipX),
        strokeOn = o.optBoolean("strokeOn", d.strokeOn),
        strokeColor = o.optInt("strokeColor", d.strokeColor),
        strokeRel = o.f("strokeRel", d.strokeRel),
        shadowOn = o.optBoolean("shadowOn", d.shadowOn),
        shadowColor = o.optInt("shadowColor", d.shadowColor),
        shadowBlur = o.f("shadowBlur", d.shadowBlur),
        shadowDx = o.f("shadowDx", d.shadowDx),
        shadowDy = o.f("shadowDy", d.shadowDy),
        bgOn = o.optBoolean("bgOn", d.bgOn),
        bgColor = o.optInt("bgColor", d.bgColor),
        bgPad = o.f("bgPad", d.bgPad),
        bgRadius = o.f("bgRadius", d.bgRadius),
    )
}

fun projectToJson(p: Project): JSONObject {
    val o = JSONObject()
    o.put("version", 2)
    o.put("docW", p.docW)
    o.put("docH", p.docH)
    o.put("bgColor", p.bgColor)
    o.put("hasPhoto", p.hasPhoto)
    o.put("nextId", p.nextId)
    o.put("bright", p.bright.toDouble())
    o.put("contrast", p.contrast.toDouble())
    o.put("sat", p.sat.toDouble())
    o.put("filter", p.filter)
    o.put("offX", p.offX.toDouble())
    o.put("offY", p.offY.toDouble())
    o.put("fullW", p.fullW)
    o.put("fullH", p.fullH)
    val arr = JSONArray()
    for (t in p.items) arr.put(layerToJson(t))
    o.put("items", arr)
    return o
}

fun projectFromJson(o: JSONObject): Project {
    val items = ArrayList<Layer>()
    val arr = o.optJSONArray("items")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val child = arr.optJSONObject(i)
            if (child != null) items.add(layerFromJson(child))
        }
    }
    var maxId = 0L
    for (t in items) if (t.id > maxId) maxId = t.id
    return Project(
        docW = o.optInt("docW", 1080),
        docH = o.optInt("docH", 1080),
        bgColor = o.optInt("bgColor", Color.WHITE),
        hasPhoto = o.optBoolean("hasPhoto", false),
        bright = o.f("bright", 0f),
        contrast = o.f("contrast", 1f),
        sat = o.f("sat", 1f),
        filter = o.optInt("filter", 0),
        offX = o.f("offX", 0f),
        offY = o.f("offY", 0f),
        fullW = o.optInt("fullW", 0),
        fullH = o.optInt("fullH", 0),
        items = items,
        nextId = maxOf(o.optLong("nextId", 1L), maxId + 1L),
    )
}
