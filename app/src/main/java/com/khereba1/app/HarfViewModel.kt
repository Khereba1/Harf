package com.khereba1.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Restored(val project: Project, val photo: Bitmap?)

/**
 * Holds the open project. It lives in a ViewModel so rotating the phone or switching the
 * app language (which recreates the activity) never loses the work.
 */
class HarfViewModel(app: Application) : AndroidViewModel(app) {

    var project: Project? by mutableStateOf(null)
        private set
    var photo: Bitmap? by mutableStateOf(null)
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
                    } else if (p.hasPhoto) {
                        val b = ImageIO.decode(Session.photoFile(ctx))
                        if (b == null) null else Restored(p, b)
                    } else {
                        Restored(p, null)
                    }
                } catch (e: Throwable) {
                    AppLog.log("SESSION", "Restore failed", e)
                    null
                }
            }
            if (restored != null && !userStarted && project == null) {
                project = restored.project
                photo = restored.photo
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
                        val maxSize = if (t.type == TYPE_SHAPE) p.docW * 2f else p.docW * 0.9f
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
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val uri: Uri? = withContext(Dispatchers.IO) {
                try {
                    val bmp = Renderer.exportBitmap(p, ph)
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
