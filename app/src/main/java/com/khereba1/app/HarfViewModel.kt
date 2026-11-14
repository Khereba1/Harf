package com.khereba1.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val IMAGE_LAYER_SIDE = 1600

private class Restored(val project: Project, val photo: Bitmap?, val images: Map<String, Bitmap>)

/**
 * Holds the open project. It lives in a ViewModel so rotating the phone or switching the
 * app language (which recreates the activity) never loses the work.
 */
class HarfViewModel(app: Application) : AndroidViewModel(app) {

    var project: Project? by mutableStateOf(null)
        private set
    var photo: Bitmap? by mutableStateOf(null)
        private set
    var images: Map<String, Bitmap> by mutableStateOf(emptyMap())
        private set
    var selectedId: Long? by mutableStateOf(null)
        private set
    var undoDepth: Int by mutableIntStateOf(0)
        private set
    var redoDepth: Int by mutableIntStateOf(0)
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    var ready: Boolean by mutableStateOf(false)
        private set
    var fontsVersion: Int by mutableIntStateOf(0)
        private set

    // ----- brush state (UI-level, not part of the saved project) -----
    var brushColor: Int by mutableIntStateOf(0xFFFFFFFF.toInt())
    var brushSize: Float by mutableFloatStateOf(0.02f) // relative to document width
    var eraser: Boolean by mutableStateOf(false)
    var eraserSize: Float by mutableFloatStateOf(0.05f) // diameter, relative to document width
    var eraseWhole: Boolean by mutableStateOf(false) // true: the eraser removes whole strokes
    /** Changes whenever the user taps a layer on the canvas (the editor then opens the Edit tab). */
    var canvasPickTick: Int by mutableIntStateOf(0)
        private set
    /** Points of the stroke being drawn right now (x,y pairs); liveTick changes on every point so the canvas redraws. */
    private val livePoints = ArrayList<Float>()
    var liveTick: Int by mutableIntStateOf(0)
        private set

    /** String resource id of a message the UI should show once (then call clearToast). */
    var toastRes: Int? by mutableStateOf(null)
        private set

    // Plain fields used by the gesture code to detect a double tap.
    var lastTapId: Long? = null
    var lastTapAt: Long = 0L

    private val undoStack = ArrayList<Project>()
    private val redoStack = ArrayList<Project>()
    private var coalesceKey: String? = null
    private var coalesceAt: Long = 0L
    private var saveJob: Job? = null
    private var userStarted = false

    init {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val restored: Restored? = withContext(Dispatchers.IO) {
                try {
                    val p = Session.load(ctx)
                    if (p == null) {
                        null
                    } else {
                        val imgs = HashMap<String, Bitmap>()
                        for (l in p.items) {
                            if (l.type == TYPE_IMAGE && l.imageKey.isNotEmpty() && !imgs.containsKey(l.imageKey)) {
                                val ib = ImageIO.decode(Session.imageFileFor(ctx, l.imageKey), IMAGE_LAYER_SIDE)
                                if (ib != null) imgs[l.imageKey] = ib
                            }
                        }
                        if (p.hasPhoto) {
                            val b = ImageIO.decode(Session.photoFile(ctx))
                            if (b == null) null else Restored(p, b, imgs)
                        } else {
                            Restored(p, null, imgs)
                        }
                    }
                } catch (e: Throwable) {
                    AppLog.log("SESSION", "Restore failed", e)
                    null
                }
            }
            if (restored != null && !userStarted && project == null) {
                project = restored.project
                photo = restored.photo
                images = restored.images
            }
            ready = true
        }
    }

    fun clearToast() {
        toastRes = null
    }

    // ---------- starting / closing a project ----------

    fun startPhoto(uri: Uri) {
        if (busy) return
        userStarted = true
        busy = true
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val bmp: Bitmap? = withContext(Dispatchers.IO) {
                try {
                    if (Session.copyPhotoIn(ctx, uri)) ImageIO.decode(Session.photoFile(ctx)) else null
                } catch (e: Throwable) {
                    AppLog.log("IMAGE", "Opening photo failed", e)
                    null
                }
            }
            if (bmp == null) {
                toastRes = R.string.err_open_image
            } else {
                resetHistory()
                Session.clearImages(ctx)
                images = emptyMap()
                photo = bmp
                project = Project(bmp.width, bmp.height, Color.WHITE, true, emptyList(), 1L)
                selectedId = null
                saveNow()
            }
            busy = false
        }
    }

    fun startBlank(width: Int, height: Int, color: Int) {
        userStarted = true
        resetHistory()
        photo = null
        images = emptyMap()
        project = Project(width, height, color or 0xFF000000.toInt(), false, emptyList(), 1L)
        selectedId = null
        Session.clear(getApplication<Application>())
        saveNow()
    }

    fun closeProject() {
        saveJob?.cancel()
        resetHistory()
        project = null
        photo = null
        images = emptyMap()
        selectedId = null
        Session.clear(getApplication<Application>())
    }

    private fun resetHistory() {
        undoStack.clear()
        redoStack.clear()
        coalesceKey = null
        syncDepth()
    }

    // ---------- history ----------

    private fun syncDepth() {
        undoDepth = undoStack.size
        redoDepth = redoStack.size
    }

    private fun pushUndo(p: Project) {
        undoStack.add(p)
        if (undoStack.size > 60) undoStack.removeAt(0)
        redoStack.clear()
        syncDepth()
    }

    /**
     * Applies a change. Rapid changes with the same [key] (a slider being dragged, a pinch)
     * share one undo step; [fresh] forces a new undo step.
     */
    private fun edit(key: String, fresh: Boolean, change: (Project) -> Project) {
        val cur = project ?: return
        val now = System.currentTimeMillis()
        if (fresh || key != coalesceKey || now - coalesceAt > 900L) pushUndo(cur)
        coalesceKey = key
        coalesceAt = now
        project = change(cur)
        scheduleSave()
    }

    fun beginGesture() {
        coalesceKey = null
    }

    fun undo() {
        val cur = project ?: return
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(cur)
        project = prev
        coalesceKey = null
        fixSelection()
        syncDepth()
        scheduleSave()
    }

    fun redo() {
        val cur = project ?: return
        if (redoStack.isEmpty()) return
        val next = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(cur)
        project = next
        coalesceKey = null
        fixSelection()
        syncDepth()
        scheduleSave()
    }

    private fun fixSelection() {
        val p = project
        val id = selectedId
        if (p != null && id != null && p.items.none { it.id == id }) selectedId = null
    }

    // ---------- editing ----------

    fun select(id: Long?) {
        selectedId = id
    }

    fun notifyCanvasPick() {
        canvasPickTick = canvasPickTick + 1
    }

    fun addText(text: String) {
        val p = project ?: return
        val id = p.nextId
        // Readable by default: white on photos and dark backgrounds, black on light blank canvases.
        val luminance = (0.299f * Color.red(p.bgColor) + 0.587f * Color.green(p.bgColor) + 0.114f * Color.blue(p.bgColor)) / 255f
        val defaultColor = if (p.hasPhoto || luminance < 0.5f) Color.WHITE else Color.BLACK
        val item = Layer(
            id = id,
            text = text,
            cx = p.docW / 2f,
            cy = p.docH / 2f,
            size = p.docW * 0.09f,
            color = defaultColor,
        )
        pushUndo(p)
        coalesceKey = null
        project = p.copy(items = p.items + item, nextId = id + 1L)
        selectedId = id
        scheduleSave()
    }

    fun editItem(key: String, fresh: Boolean, change: (Layer) -> Layer) {
        val id = selectedId ?: return
        editById(id, key, fresh, change)
    }

    fun editById(id: Long, key: String, fresh: Boolean, change: (Layer) -> Layer) {
        edit(key, fresh) { p ->
            p.copy(items = p.items.map { t -> if (t.id == id) change(t) else t })
        }
    }

    fun transformSelected(dx: Float, dy: Float, zoom: Float, rotationDeg: Float) {
        val id = selectedId ?: return
        edit("gesture", false) { p ->
            p.copy(
                items = p.items.map { t ->
                    if (t.id == id && !t.locked) {
                        if (t.type == TYPE_DRAW) return@map t
                        val maxSize = if (t.type == TYPE_TEXT) p.docW * 0.9f else p.docW * 2f
                        t.copy(
                            cx = t.cx + dx,
                            cy = t.cy + dy,
                            size = (t.size * zoom).coerceIn(6f, maxSize),
                            rotation = normalizeAngle(t.rotation + rotationDeg),
                        )
                    } else {
                        t
                    }
                },
            )
        }
    }

    private fun normalizeAngle(a: Float): Float {
        var r = (a + 180f) % 360f
        if (r < 0f) r += 360f
        return r - 180f
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        edit("delete", true) { p -> p.copy(items = p.items.filter { it.id != id }) }
        selectedId = null
    }

    fun duplicateSelected() {
        val id = selectedId ?: return
        val p = project ?: return
        val src = p.items.firstOrNull { it.id == id } ?: return
        val newId = p.nextId
        val copy = src.copy(id = newId, cx = src.cx + p.docW * 0.04f, cy = src.cy + p.docW * 0.04f)
        pushUndo(p)
        coalesceKey = null
        project = p.copy(items = p.items + copy, nextId = newId + 1L)
        selectedId = newId
        scheduleSave()
    }

    fun moveSelectedForward(forward: Boolean) {
        val id = selectedId ?: return
        moveLayer(id, forward)
    }

    /** forward = one step toward the top of the stack. */
    fun moveLayer(id: Long, forward: Boolean) {
        val cur = project ?: return
        val i = cur.items.indexOfFirst { it.id == id }
        val j = if (forward) i + 1 else i - 1
        if (i < 0 || j < 0 || j >= cur.items.size) return
        edit("order", true) { p ->
            val list = p.items.toMutableList()
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
            p.copy(items = list)
        }
    }

    fun deleteLayer(id: Long) {
        val cur = project ?: return
        if (cur.items.none { it.id == id }) return
        edit("delete", true) { p -> p.copy(items = p.items.filter { it.id != id }) }
        if (selectedId == id) selectedId = null
    }

    fun toggleLock(id: Long) {
        editById(id, "lock", true) { it.copy(locked = !it.locked) }
    }

    fun toggleVisible(id: Long) {
        editById(id, "visible", true) { it.copy(visible = !it.visible) }
    }

    fun addShape(kind: Int) {
        val p = project ?: return
        val id = p.nextId
        val layer = Layer(
            id = id,
            type = TYPE_SHAPE,
            cx = p.docW / 2f,
            cy = p.docH / 2f,
            size = p.docW * 0.3f,
            shapeKind = kind,
            aspect = Shapes.defaultAspect(kind),
            color = 0xFFE4572E.toInt(),
        )
        pushUndo(p)
        coalesceKey = null
        project = p.copy(items = p.items + layer, nextId = id + 1L)
        selectedId = id
        scheduleSave()
    }

    /** Adds a picture from the gallery as a movable, resizable layer. */
    fun addImage(uri: Uri) {
        if (project == null || busy) return
        busy = true
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val key = "i" + System.currentTimeMillis().toString()
            val bmp: Bitmap? = withContext(Dispatchers.IO) {
                try {
                    if (Session.copyImageIn(ctx, uri, key)) ImageIO.decode(Session.imageFileFor(ctx, key), IMAGE_LAYER_SIDE) else null
                } catch (e: Throwable) {
                    AppLog.log("IMAGE", "Adding image failed", e)
                    null
                }
            }
            val p = project
            if (bmp == null || p == null) {
                if (bmp == null) toastRes = R.string.err_open_image
            } else {
                val id = p.nextId
                val w = p.docW * 0.5f
                val layer = Layer(
                    id = id,
                    type = TYPE_IMAGE,
                    cx = p.docW / 2f,
                    cy = p.docH / 2f,
                    size = w,
                    aspect = bmp.height.toFloat() / bmp.width.toFloat(),
                    corner = 0f,
                    imageKey = key,
                )
                images = images + (key to bmp)
                pushUndo(p)
                coalesceKey = null
                project = p.copy(items = p.items + layer, nextId = id + 1L)
                selectedId = id
                scheduleSave()
            }
            busy = false
        }
    }

    /** Generic project-level edit (filters, background, ...). */
    fun editProject(key: String, fresh: Boolean, change: (Project) -> Project) {
        edit(key, fresh, change)
    }

    // ---------- drawing ----------

    fun liveStroke(): Stroke? {
        if (livePoints.isEmpty() || eraser) return null
        val p = project ?: return null
        return Stroke(brushColor, max(1f, brushSize * p.docW), livePoints.toFloatArray())
    }

    /** Where the eraser is right now (x, y in document pixels) while it is being dragged, else null. */
    fun eraserCursor(): FloatArray? {
        if (!eraser || livePoints.size < 2) return null
        return floatArrayOf(livePoints[0], livePoints[1])
    }

    fun beginStroke(x: Float, y: Float) {
        livePoints.clear()
        if (eraser) {
            beginGesture()
            livePoints.add(x)
            livePoints.add(y)
            eraseAt(x, y)
        } else {
            livePoints.add(x)
            livePoints.add(y)
        }
        liveTick++
    }

    fun extendStroke(x: Float, y: Float, minDist: Float) {
        if (eraser) {
            livePoints.clear()
            livePoints.add(x)
            livePoints.add(y)
            eraseAt(x, y)
            liveTick++
            return
        }
        val n = livePoints.size
        if (n >= 2) {
            val dx = x - livePoints[n - 2]
            val dy = y - livePoints[n - 1]
            if (dx * dx + dy * dy < minDist * minDist) return
        }
        livePoints.add(x)
        livePoints.add(y)
        liveTick++
    }

    fun endStroke() {
        val p = project
        val pts = livePoints.toFloatArray()
        livePoints.clear()
        liveTick++
        if (eraser || p == null || pts.isEmpty()) return
        val stroke = Stroke(brushColor, max(1f, brushSize * p.docW), pts)
        // Strokes go into the selected drawing layer, else the top-most one, else a new layer.
        val target = p.items.firstOrNull { it.id == selectedId && it.type == TYPE_DRAW }
            ?: p.items.lastOrNull { it.type == TYPE_DRAW }
        pushUndo(p)
        coalesceKey = null
        if (target != null) {
            project = p.copy(items = p.items.map { if (it.id == target.id) it.copy(strokes = it.strokes + stroke) else it })
        } else {
            val id = p.nextId
            val layer = Layer(id = id, type = TYPE_DRAW, cx = 0f, cy = 0f, size = 1f, strokes = listOf(stroke))
            project = p.copy(items = p.items + layer, nextId = id + 1L)
        }
        scheduleSave()
    }

    private fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        var t = if (len2 == 0f) 0f else ((px - ax) * dx + (py - ay) * dy) / len2
        if (t < 0f) t = 0f
        if (t > 1f) t = 1f
        val cx = ax + t * dx
        val cy = ay + t * dy
        return kotlin.math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    private fun strokeHit(s: Stroke, x: Float, y: Float, radius: Float): Boolean {
        val n = s.pts.size / 2
        val reach = radius + s.width / 2f
        if (n == 1) return distToSegment(x, y, s.pts[0], s.pts[1], s.pts[0], s.pts[1]) <= reach
        for (i in 0 until n - 1) {
            if (distToSegment(x, y, s.pts[2 * i], s.pts[2 * i + 1], s.pts[2 * i + 2], s.pts[2 * i + 3]) <= reach) return true
        }
        return false
    }

    /** Cuts [s] where the eraser circle (center x,y; radius) touches it. Returns the surviving pieces. */
    private fun cutStroke(s: Stroke, x: Float, y: Float, radius: Float): List<Stroke> {
        val reach = radius + s.width / 2f
        val n = s.pts.size / 2
        val dense = ArrayList<Float>()
        dense.add(s.pts[0])
        dense.add(s.pts[1])
        for (i in 0 until n - 1) {
            val ax = s.pts[2 * i]
            val ay = s.pts[2 * i + 1]
            val bx = s.pts[2 * i + 2]
            val by = s.pts[2 * i + 3]
            val len = kotlin.math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
            if (len > 0f && distToSegment(x, y, ax, ay, bx, by) < reach) {
                val k = kotlin.math.ceil(len / (reach * 0.5f)).toInt().coerceIn(1, 64)
                for (j in 1..k) {
                    val t = j.toFloat() / k.toFloat()
                    dense.add(ax + (bx - ax) * t)
                    dense.add(ay + (by - ay) * t)
                }
            } else {
                dense.add(bx)
                dense.add(by)
            }
        }
        val out = ArrayList<Stroke>()
        var run = ArrayList<Float>()
        val m = dense.size / 2
        for (i in 0 until m) {
            val px = dense[2 * i]
            val py = dense[2 * i + 1]
            val dx = px - x
            val dy = py - y
            if (dx * dx + dy * dy < reach * reach) {
                if (run.isNotEmpty()) {
                    out.add(Stroke(s.color, s.width, run.toFloatArray()))
                    run = ArrayList()
                }
            } else {
                run.add(px)
                run.add(py)
            }
        }
        if (run.isNotEmpty()) out.add(Stroke(s.color, s.width, run.toFloatArray()))
        return out
    }

    /** Erases under the finger: either just that spot (default) or whole strokes. */
    private fun eraseAt(x: Float, y: Float) {
        val p = project ?: return
        val radius = max(4f, eraserSize * p.docW * 0.5f)
        val whole = eraseWhole
        var any = false
        for (l in p.items) {
            if (l.type == TYPE_DRAW && l.visible && !l.locked && l.strokes.any { strokeHit(it, x, y, radius) }) any = true
        }
        if (!any) return
        edit("erase", false) { cur ->
            cur.copy(
                items = cur.items.map { l ->
                    if (l.type == TYPE_DRAW && l.visible && !l.locked) {
                        val next = ArrayList<Stroke>()
                        for (s in l.strokes) {
                            if (!strokeHit(s, x, y, radius)) {
                                next.add(s)
                            } else if (!whole) {
                                next.addAll(cutStroke(s, x, y, radius))
                            }
                        }
                        l.copy(strokes = next)
                    } else {
                        l
                    }
                },
            )
        }
    }

    fun addEmoji(emoji: String) {
        val p = project ?: return
        val id = p.nextId
        val layer = Layer(
            id = id,
            cx = p.docW / 2f,
            cy = p.docH / 2f,
            size = p.docW * 0.2f,
            text = emoji,
        )
        pushUndo(p)
        coalesceKey = null
        project = p.copy(items = p.items + layer, nextId = id + 1L)
        selectedId = id
        scheduleSave()
    }

    fun importFont(uri: Uri) {
        viewModelScope.launch {
            val entry = withContext(Dispatchers.IO) { FontStore.importFont(uri) }
            if (entry == null) {
                toastRes = R.string.font_import_failed
            } else {
                fontsVersion = fontsVersion + 1
                toastRes = R.string.font_imported
                editItem("font", true) { it.copy(fontKey = entry.key) }
            }
        }
    }

    // ---------- saving ----------

    private fun scheduleSave() {
        val p = project ?: return
        val ctx = getApplication<Application>()
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(700L)
            Session.save(ctx, p)
        }
    }

    /** Writes the project to disk right away (called when the app goes to the background). */
    fun saveNow() {
        val p = project ?: return
        saveJob?.cancel()
        Session.save(getApplication<Application>(), p)
    }

    /** Renders the image and saves it to the gallery. [onShare] gets the saved uri when sharing was requested. */
    fun exportImage(png: Boolean, share: Boolean, onShare: (Uri) -> Unit) {
        val p = project ?: return
        if (busy) return
        busy = true
        val ph = photo
        val imgs = images
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val uri: Uri? = withContext(Dispatchers.IO) {
                try {
                    val bmp = Renderer.exportBitmap(p, ph, imgs)
                    if (bmp == null) {
                        null
                    } else {
                        try {
                            ImageIO.saveToGallery(ctx, bmp, png, 95)
                        } finally {
                            bmp.recycle()
                        }
                    }
                } catch (e: Throwable) {
                    AppLog.log("EXPORT", "Export failed", e)
                    null
                }
            }
            busy = false
            if (uri == null) {
                toastRes = R.string.err_save
            } else {
                toastRes = R.string.saved_ok
                if (share) onShare(uri)
            }
        }
    }
}
