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

/** The crop rectangle being edited, in document pixels. */
class CropRect(val l: Float, val t: Float, val r: Float, val b: Float)

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
    // ----- crop -----
    var crop: CropRect? by mutableStateOf(null)
        private set
    /** Width / height the crop is locked to, 0 = free. */
    var cropRatio: Float by mutableFloatStateOf(0f)
        private set

    fun beginCrop() {
        val p = project ?: return
        cropRatio = 0f
        crop = CropRect(0f, 0f, p.docW.toFloat(), p.docH.toFloat())
    }

    fun endCrop() {
        crop = null
    }

    /** Locks the crop to a ratio (0 = free) and fits the largest centered rectangle of that ratio. */
    fun setCropRatio(ratio: Float) {
        val p = project ?: return
        cropRatio = ratio
        if (ratio <= 0f) return
        val w = p.docW.toFloat()
        val h = p.docH.toFloat()
        var cw = w
        var ch = cw / ratio
        if (ch > h) {
            ch = h
            cw = ch * ratio
        }
        val l = (w - cw) / 2f
        val t = (h - ch) / 2f
        crop = CropRect(l, t, l + cw, t + ch)
    }

    /**
     * Drags part of the crop rectangle by (dx, dy) document pixels.
     * handle: 1 TL, 2 TR, 3 BL, 4 BR (corners), 5 L, 6 T, 7 R, 8 B (edges), 9 move the whole rectangle.
     */
    fun dragCrop(handle: Int, dx: Float, dy: Float) {
        val p = project ?: return
        val c = crop ?: return
        val maxW = p.docW.toFloat()
        val maxH = p.docH.toFloat()
        val minS = 24f
        var l = c.l
        var t = c.t
        var r = c.r
        var b = c.b
        if (handle == 9) {
            val w = r - l
            val h = b - t
            l = (l + dx).coerceIn(0f, max(0f, maxW - w))
            t = (t + dy).coerceIn(0f, max(0f, maxH - h))
            r = l + w
            b = t + h
        } else if (cropRatio > 0f && handle in 1..4) {
            val leftSide = handle == 1 || handle == 3
            val topSide = handle == 1 || handle == 2
            val ax = if (leftSide) r else l
            val ay = if (topSide) b else t
            val mx = ((if (leftSide) l else r) + dx).coerceIn(0f, maxW)
            val my = ((if (topSide) t else b) + dy).coerceIn(0f, maxH)
            var w = kotlin.math.abs(mx - ax)
            var h = kotlin.math.abs(my - ay)
            if (w / cropRatio > h) h = w / cropRatio else w = h * cropRatio
            val sx = if (mx >= ax) 1f else -1f
            val sy = if (my >= ay) 1f else -1f
            val limW = if (sx > 0f) maxW - ax else ax
            val limH = if (sy > 0f) maxH - ay else ay
            if (w <= 0f || h <= 0f) return
            val k = kotlin.math.min(1f, kotlin.math.min(limW / w, limH / h))
            w *= k
            h *= k
            if (w < minS || h < minS) return
            val nx = ax + sx * w
            val ny = ay + sy * h
            l = kotlin.math.min(ax, nx)
            r = kotlin.math.max(ax, nx)
            t = kotlin.math.min(ay, ny)
            b = kotlin.math.max(ay, ny)
        } else {
            if (handle == 1 || handle == 3 || handle == 5) l = (l + dx).coerceIn(0f, r - minS)
            if (handle == 2 || handle == 4 || handle == 7) r = (r + dx).coerceIn(l + minS, maxW)
            if (handle == 1 || handle == 2 || handle == 6) t = (t + dy).coerceIn(0f, b - minS)
            if (handle == 3 || handle == 4 || handle == 8) b = (b + dy).coerceIn(t + minS, maxH)
        }
        crop = CropRect(l, t, r, b)
    }

    /** Cuts the document down to the crop rectangle. Layers keep their place on the picture; one undo step. */
    fun applyCrop() {
        val c = crop ?: return
        val x0 = Math.round(c.l)
        val y0 = Math.round(c.t)
        val w = kotlin.math.max(16, Math.round(c.r) - x0)
        val h = kotlin.math.max(16, Math.round(c.b) - y0)
        edit("crop", true) { p ->
            val shifted = p.items.map { l ->
                if (l.type == TYPE_DRAW) {
                    l.copy(
                        strokes = l.strokes.map { s ->
                            val np = FloatArray(s.pts.size)
                            var i = 0
                            while (i + 1 < s.pts.size) {
                                np[i] = s.pts[i] - x0
                                np[i + 1] = s.pts[i + 1] - y0
                                i += 2
                            }
                            Stroke(s.color, s.width, np)
                        },
                    )
                } else {
                    l.copy(cx = l.cx - x0, cy = l.cy - y0)
                }
            }
            p.copy(
                docW = w,
                docH = h,
                items = shifted,
                offX = p.offX + x0,
                offY = p.offY + y0,
                fullW = if (p.fullW > 0) p.fullW else p.docW,
                fullH = if (p.fullH > 0) p.fullH else p.docH,
            )
        }
        beginCrop()
    }

    /** True while the editor waits for a tap on the canvas to pick a color. */
    var picking: Boolean by mutableStateOf(false)
        private set
    private var pickCallback: ((Int) -> Unit)? = null

    fun startPick(cb: (Int) -> Unit) {
        pickCallback = cb
        picking = true
    }

    fun cancelPick() {
        pickCallback = null
        picking = false
        pickActive = false
    }

    // ----- live loupe while the finger is down in pick mode -----
    var pickActive: Boolean by mutableStateOf(false)
        private set
    var pickColor: Int by mutableIntStateOf(0)
        private set
    var pickTick: Int by mutableIntStateOf(0)
        private set
    var pickViewX: Float = 0f
        private set
    var pickViewY: Float = 0f
        private set
    private var loupeBmp: Bitmap? = null
    fun loupe(): Bitmap? = loupeBmp

    /** Color of the finished picture (photo + all layers, no selection handles) at a document point. */
    private fun sampleDoc(p: Project, x: Float, y: Float): Int {
        val xi = x.toInt().coerceIn(0, p.docW - 1)
        val yi = y.toInt().coerceIn(0, p.docH - 1)
        var px = 0xFF000000.toInt()
        try {
            val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            c.translate(-xi.toFloat(), -yi.toFloat())
            Renderer.drawDocument(c, p, photo, images, null, 1f, 0)
            px = bmp.getPixel(0, 0)
            bmp.recycle()
        } catch (e: Throwable) {
            AppLog.log("PICK", "Color pick failed", e)
        }
        return px or 0xFF000000.toInt()
    }

    /**
     * Called while the finger moves in pick mode: updates the magnifier (a small zoomed view of
     * the area under the finger) and the color it is on. [scale] is view pixels per document pixel.
     */
    fun pickMove(docX: Float, docY: Float, viewX: Float, viewY: Float, scale: Float) {
        val p = project ?: return
        pickColor = sampleDoc(p, docX, docY)
        try {
            val n = 21
            val bmp = loupeBmp ?: Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888).also { loupeBmp = it }
            bmp.eraseColor(0xFF2A2E36.toInt())
            val c = android.graphics.Canvas(bmp)
            val region = n.toFloat() * max(1f, 2f / scale)
            c.scale(n.toFloat() / region, n.toFloat() / region)
            c.translate(-(docX - region / 2f), -(docY - region / 2f))
            Renderer.drawDocument(c, p, photo, images, null, 1f, 0)
        } catch (e: Throwable) {
            AppLog.log("PICK", "Loupe failed", e)
        }
        pickViewX = viewX
        pickViewY = viewY
        pickActive = true
        pickTick = pickTick + 1
    }

    /** Finger lifted: hands the color under it to the waiting callback and leaves pick mode. */
    fun pickAt(x: Float, y: Float) {
        val p = project
        val cb = pickCallback
        pickCallback = null
        picking = false
        pickActive = false
        if (p == null || cb == null) return
        cb(sampleDoc(p, x, y))
    }

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

    private fun loadSession(ctx: Application): Restored? {
        return try {
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

    init {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val restored: Restored? = withContext(Dispatchers.IO) { loadSession(ctx) }
            if (restored != null && !userStarted && project == null) {
                project = restored.project
                photo = restored.photo
                images = restored.images
                currentId = withContext(Dispatchers.IO) { Session.getCurrentId(ctx) }
            }
            ready = true
            refreshSaved()
        }
    }

    // ---------- saved projects ----------

    var saved: List<SavedInfo> by mutableStateOf(emptyList())
        private set
    /** Id of the saved project that the open document belongs to, if any. */
    var currentId: String? by mutableStateOf(null)
        private set

    fun refreshSaved() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            saved = withContext(Dispatchers.IO) { ProjectStore.list(ctx) }
        }
    }

    /** Saves the open document into "My projects" (first time: a new entry, afterwards the same one). */
    fun saveProject(onDone: (() -> Unit)? = null) {
        val p = project ?: return
        val ph = photo
        val imgs = images
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) {
                Session.save(ctx, p)
                val existing = ProjectStore.list(ctx)
                val id = currentId ?: ProjectStore.newId()
                val name = existing.firstOrNull { it.id == id }?.name
                    ?: ("Harf " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US).format(java.util.Date()))
                val done = ProjectStore.save(ctx, id, name, p, ph, imgs)
                if (done) Session.setCurrentId(ctx, id)
                if (done) id else null
            }
            if (ok != null) {
                currentId = ok
                toastRes = R.string.project_saved
                saved = withContext(Dispatchers.IO) { ProjectStore.list(ctx) }
                onDone?.invoke()
            } else {
                toastRes = R.string.err_save_project
            }
        }
    }

    fun openProject(id: String) {
        if (busy) return
        busy = true
        userStarted = true
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val restored: Restored? = withContext(Dispatchers.IO) {
                if (ProjectStore.openInto(ctx, id)) {
                    Session.setCurrentId(ctx, id)
                    loadSession(ctx)
                } else {
                    null
                }
            }
            if (restored != null) {
                resetHistory()
                images = restored.images
                photo = restored.photo
                project = restored.project
                selectedId = null
                currentId = id
            } else {
                toastRes = R.string.err_open_project
            }
            busy = false
        }
    }

    fun deleteSaved(id: String) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            withContext(Dispatchers.IO) { ProjectStore.delete(ctx, id) }
            if (currentId == id) {
                currentId = null
                withContext(Dispatchers.IO) { Session.setCurrentId(ctx, null) }
            }
            saved = withContext(Dispatchers.IO) { ProjectStore.list(ctx) }
        }
    }

    fun renameSaved(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            withContext(Dispatchers.IO) { ProjectStore.rename(ctx, id, clean) }
            saved = withContext(Dispatchers.IO) { ProjectStore.list(ctx) }
        }
    }

    /** Writes a .harf file into Downloads/Harf and hands its uri to [onShare]. */
    fun exportSaved(id: String, name: String, onShare: (Uri) -> Unit) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val uri = withContext(Dispatchers.IO) { ProjectStore.exportFile(ctx, id, name) }
            if (uri != null) {
                toastRes = R.string.project_exported
                onShare(uri)
            } else {
                toastRes = R.string.err_export_project
            }
        }
    }

    fun importProject(uri: Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val id = withContext(Dispatchers.IO) { ProjectStore.importFile(ctx, uri) }
            if (id != null) {
                toastRes = R.string.project_imported
                saved = withContext(Dispatchers.IO) { ProjectStore.list(ctx) }
            } else {
                toastRes = R.string.err_import_project
            }
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
                Session.setCurrentId(ctx, null)
                currentId = null
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
        currentId = null
        saveNow()
    }

    fun closeProject() {
        cancelPick()
        saveJob?.cancel()
        resetHistory()
        project = null
        photo = null
        images = emptyMap()
        selectedId = null
        Session.clear(getApplication<Application>())
        currentId = null
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
        if (crop != null) beginCrop()
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
        if (crop != null) beginCrop()
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
