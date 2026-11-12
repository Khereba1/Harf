package com.khereba1.app

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

const val ALIGN_START = 0
const val ALIGN_CENTER = 1
const val ALIGN_END = 2

/**
 * One text layer. All "Rel" values are relative to [size] so that pinch-resizing a text
 * scales its outline, shadow and background box with it.
 * Coordinates (cx, cy) and size are in document pixels (the pixels of the photo / canvas).
 */
data class TextItem(
    val id: Long,
    val text: String,
    val cx: Float,
    val cy: Float,
    val size: Float,
    val rotation: Float = 0f,
    val fontKey: String = "sys:sans-serif",
    val color: Int = Color.WHITE,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val align: Int = ALIGN_CENTER,
    val opacity: Float = 1f,
    val lineSpacing: Float = 1f,
    val strokeOn: Boolean = false,
    val strokeColor: Int = Color.BLACK,
    val strokeRel: Float = 0.06f,
    val shadowOn: Boolean = false,
    val shadowColor: Int = 0xB3000000.toInt(),
    val shadowBlur: Float = 0.06f,
    val shadowDx: Float = 0.03f,
    val shadowDy: Float = 0.03f,
    val bgOn: Boolean = false,
    val bgColor: Int = 0xCC000000.toInt(),
    val bgPad: Float = 0.3f,
    val bgRadius: Float = 0.25f,
)

/** The whole document. Immutable, so undo/redo is just a list of snapshots. */
data class Project(
    val docW: Int,
    val docH: Int,
    val bgColor: Int,
    val hasPhoto: Boolean,
    val items: List<TextItem>,
    val nextId: Long,
)

private fun JSONObject.f(key: String, def: Float): Float = optDouble(key, def.toDouble()).toFloat()

fun textItemToJson(t: TextItem): JSONObject {
    val o = JSONObject()
    o.put("id", t.id)
    o.put("text", t.text)
    o.put("cx", t.cx.toDouble())
    o.put("cy", t.cy.toDouble())
    o.put("size", t.size.toDouble())
    o.put("rotation", t.rotation.toDouble())
    o.put("fontKey", t.fontKey)
    o.put("color", t.color)
    o.put("bold", t.bold)
    o.put("italic", t.italic)
    o.put("align", t.align)
    o.put("opacity", t.opacity.toDouble())
    o.put("lineSpacing", t.lineSpacing.toDouble())
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

fun textItemFromJson(o: JSONObject): TextItem {
    val d = TextItem(
        id = o.optLong("id", 0L),
        text = o.optString("text", ""),
        cx = o.f("cx", 0f),
        cy = o.f("cy", 0f),
        size = o.f("size", 80f),
    )
    return d.copy(
        rotation = o.f("rotation", d.rotation),
        fontKey = o.optString("fontKey", d.fontKey),
        color = o.optInt("color", d.color),
        bold = o.optBoolean("bold", d.bold),
        italic = o.optBoolean("italic", d.italic),
        align = o.optInt("align", d.align),
        opacity = o.f("opacity", d.opacity),
        lineSpacing = o.f("lineSpacing", d.lineSpacing),
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
    o.put("version", 1)
    o.put("docW", p.docW)
    o.put("docH", p.docH)
    o.put("bgColor", p.bgColor)
    o.put("hasPhoto", p.hasPhoto)
    o.put("nextId", p.nextId)
    val arr = JSONArray()
    for (t in p.items) arr.put(textItemToJson(t))
    o.put("items", arr)
    return o
}

fun projectFromJson(o: JSONObject): Project {
    val items = ArrayList<TextItem>()
    val arr = o.optJSONArray("items")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val child = arr.optJSONObject(i)
            if (child != null) items.add(textItemFromJson(child))
        }
    }
    var maxId = 0L
    for (t in items) if (t.id > maxId) maxId = t.id
    return Project(
        docW = o.optInt("docW", 1080),
        docH = o.optInt("docH", 1080),
        bgColor = o.optInt("bgColor", Color.WHITE),
        hasPhoto = o.optBoolean("hasPhoto", false),
        items = items,
        nextId = maxOf(o.optLong("nextId", 1L), maxId + 1L),
    )
}
