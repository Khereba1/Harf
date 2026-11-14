package com.khereba1.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val TOOL_EDIT = 0
private const val TOOL_ADD = 1
private const val TOOL_DRAW = 2
private const val TOOL_PHOTO = 3
private const val TOOL_LAYERS = 4

private const val DIALOG_NONE = 0
private const val DIALOG_ADD = 1
private const val DIALOG_EDIT = 2

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun EditorScreen(vm: HarfViewModel) {
    val project = vm.project ?: return
    val context = LocalContext.current
    var tool by remember { mutableIntStateOf(TOOL_ADD) }
    var panelOpen by remember { mutableStateOf(true) }
    var textDialog by remember { mutableIntStateOf(DIALOG_NONE) }
    var showExport by remember { mutableStateOf(false) }
    var showClose by remember { mutableStateOf(false) }
    val selectedId = vm.selectedId
    val selected: Layer? = project.items.firstOrNull { it.id == selectedId }

    // Tapping a layer on the canvas opens the Edit tab (unless drawing or browsing layers).
    LaunchedEffect(vm.canvasPickTick) {
        if (vm.canvasPickTick > 0 && tool != TOOL_LAYERS && tool != TOOL_DRAW) {
            tool = TOOL_EDIT
            panelOpen = true
        }
    }
    LaunchedEffect(selectedId) {
        val s = project.items.firstOrNull { it.id == selectedId }
        if (textDialog == DIALOG_EDIT && (s == null || s.type != TYPE_TEXT)) textDialog = DIALOG_NONE
    }

    LaunchedEffect(tool) {
        if (tool == TOOL_DRAW) vm.select(null)
    }
    val pickStarter: (((Int) -> Unit) -> Unit) = { cb -> vm.startPick(cb) }

    androidx.compose.runtime.CompositionLocalProvider(LocalEyedropper provides pickStarter) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ----- top bar -----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(R.drawable.ic_close, stringResource(R.string.close), { showClose = true })
            Spacer(Modifier.weight(1f))
            IconAction(R.drawable.ic_undo, stringResource(R.string.undo), { vm.undo() }, enabled = vm.undoDepth > 0)
            IconAction(R.drawable.ic_redo, stringResource(R.string.redo), { vm.redo() }, enabled = vm.redoDepth > 0)
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = { showExport = true },
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp),
            ) {
                HIcon(R.drawable.ic_download, null, tint = MaterialTheme.colorScheme.onPrimary, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(6.dp))
        }

        // ----- canvas (actions float over its bottom edge so the canvas never resizes) -----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(HarfCanvasBg),
        ) {
            EditorCanvas(
                vm = vm,
                onEditRequest = {
                    val sel = vm.project?.items?.firstOrNull { it.id == vm.selectedId }
                    if (sel != null && sel.type == TYPE_TEXT) textDialog = DIALOG_EDIT
                },
                drawMode = tool == TOOL_DRAW,
                modifier = Modifier.fillMaxSize(),
            )
            if (vm.busy) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            if (vm.picking) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp, start = 12.dp, end = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HIcon(R.drawable.ic_pipette, null, tint = MaterialTheme.colorScheme.primary, size = 20.dp)
                        Text(
                            text = stringResource(R.string.pick_hint),
                            modifier = Modifier.padding(horizontal = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconAction(R.drawable.ic_close, stringResource(R.string.cancel), { vm.cancelPick() })
                    }
                }
            }
        }

        // ----- action strip (fixed height, so the canvas never changes size) -----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(HarfBg)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
                    IconAction(
                        R.drawable.ic_text_add, stringResource(R.string.add_text),
                        { textDialog = DIALOG_ADD },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    if (selected != null) {
                        if (selected.type == TYPE_TEXT) {
                            IconAction(R.drawable.ic_edit, stringResource(R.string.edit_text), { textDialog = DIALOG_EDIT })
                        }
                        IconAction(R.drawable.ic_copy, stringResource(R.string.duplicate), { vm.duplicateSelected() })
                        IconAction(
                            if (selected.locked) R.drawable.ic_lock else R.drawable.ic_unlock,
                            stringResource(if (selected.locked) R.string.unlock else R.string.lock),
                            { vm.toggleLock(selected.id) },
                            tint = if (selected.locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        IconAction(R.drawable.ic_up, stringResource(R.string.bring_forward), { vm.moveSelectedForward(true) })
                        IconAction(R.drawable.ic_down, stringResource(R.string.send_backward), { vm.moveSelectedForward(false) })
                        IconAction(R.drawable.ic_trash, stringResource(R.string.delete), { vm.deleteSelected() })
                    }
        }

        // ----- bottom sheet: panel + tools -----
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (panelOpen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp, bottom = 4.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outline),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(214.dp)
                            .padding(top = 4.dp),
                    ) {
                        when (tool) {
                            TOOL_EDIT -> EditPanel(vm, project, selected)
                            TOOL_ADD -> AddPanel(vm) { textDialog = DIALOG_ADD }
                            TOOL_DRAW -> DrawPanel(vm)
                            TOOL_PHOTO -> AdjustPanel(vm, project)
                            else -> LayersPanel(vm, project)
                        }
                    }
                }
                // tools: tap the active one again to fold the panel away
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val toolLabels = listOf(
                        stringResource(R.string.tool_edit),
                        stringResource(R.string.tool_add),
                        stringResource(R.string.tool_draw),
                        stringResource(R.string.tool_adjust),
                        stringResource(R.string.tool_layers),
                    )
                    val toolIcons = listOf(R.drawable.ic_edit, R.drawable.ic_plus, R.drawable.ic_brush, R.drawable.ic_adjust, R.drawable.ic_layers)
                    for (i in toolLabels.indices) {
                        ToolItem(
                            res = toolIcons[i],
                            label = toolLabels[i],
                            selected = tool == i && panelOpen,
                            onClick = {
                                if (tool == i) {
                                    panelOpen = !panelOpen
                                } else {
                                    tool = i
                                    panelOpen = true
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    }

    // ----- dialogs -----
    if (textDialog == DIALOG_ADD) {
        TextDialog(
            title = stringResource(R.string.text_dialog_add),
            initial = "",
            onDismiss = { textDialog = DIALOG_NONE },
            onConfirm = { t ->
                textDialog = DIALOG_NONE
                tool = TOOL_EDIT
                panelOpen = true
                vm.addText(t)
            },
        )
    }
    if (textDialog == DIALOG_EDIT && selected != null && selected.type == TYPE_TEXT) {
        TextDialog(
            title = stringResource(R.string.text_dialog_edit),
            initial = selected.text,
            onDismiss = { textDialog = DIALOG_NONE },
            onConfirm = { t ->
                textDialog = DIALOG_NONE
                vm.editItem("text", true) { it.copy(text = t) }
            },
        )
    }
    if (showExport) {
        ExportDialog(
            docW = project.docW,
            docH = project.docH,
            onDismiss = { showExport = false },
            onSave = { png, share ->
                showExport = false
                vm.exportImage(png, share) { uri -> shareImage(context, uri) }
            },
        )
    }
    if (showClose) {
        ConfirmDialog(
            title = stringResource(R.string.close_confirm_title),
            message = stringResource(R.string.close_confirm_msg),
            confirmLabel = stringResource(R.string.discard),
            onDismiss = { showClose = false },
            onConfirm = {
                showClose = false
                vm.closeProject()
            },
        )
    }
}

private fun shareImage(context: Context, uri: Uri) {
    try {
        val send = Intent(Intent.ACTION_SEND)
        send.type = "image/*"
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser))
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    } catch (e: Throwable) {
        AppLog.log("SHARE", "Share failed", e)
    }
}

/**
 * The drawing surface. One finger drags the text under it (or the selected one), two fingers
 * pinch to resize and twist to rotate, tapping empty space deselects, double tap edits the text.
 */
@Composable
private fun EditorCanvas(vm: HarfViewModel, onEditRequest: () -> Unit, drawMode: Boolean, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val project = vm.project
    val photo = vm.photo
    val selectedId = vm.selectedId
    val editState = rememberUpdatedState(onEditRequest)
    val drawState = rememberUpdatedState(drawMode)
    val liveTick = vm.liveTick // read so the canvas redraws while a stroke grows
    val pickTick = vm.pickTick // same for the color-picker magnifier

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val proj = vm.project
                if (proj != null && vm.picking) {
                    val fp = fitDoc(size.width.toFloat(), size.height.toFloat(), proj.docW, proj.docH)
                    down.consume()
                    var last = down.position
                    vm.pickMove((last.x - fp.ox) / fp.scale, (last.y - fp.oy) / fp.scale, last.x, last.y, fp.scale)
                    var go = true
                    while (go) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                        if (ch.pressed) {
                            last = ch.position
                            vm.pickMove((last.x - fp.ox) / fp.scale, (last.y - fp.oy) / fp.scale, last.x, last.y, fp.scale)
                        }
                        ch.consume()
                        go = ev.changes.any { it.pressed }
                    }
                    vm.pickAt((last.x - fp.ox) / fp.scale, (last.y - fp.oy) / fp.scale)
                } else if (proj != null && drawState.value) {
                    val fd = fitDoc(size.width.toFloat(), size.height.toFloat(), proj.docW, proj.docH)
                    down.consume()
                    vm.beginStroke((down.position.x - fd.ox) / fd.scale, (down.position.y - fd.oy) / fd.scale)
                    var go = true
                    while (go) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                        if (ch.positionChanged()) {
                            vm.extendStroke((ch.position.x - fd.ox) / fd.scale, (ch.position.y - fd.oy) / fd.scale, 2f / fd.scale)
                            ch.consume()
                        }
                        go = ev.changes.any { it.pressed }
                    }
                    vm.endStroke()
                } else if (proj != null) {
                    val f0 = fitDoc(size.width.toFloat(), size.height.toFloat(), proj.docW, proj.docH)
                    val docX = (down.position.x - f0.ox) / f0.scale
                    val docY = (down.position.y - f0.oy) / f0.scale
                    val hit = Renderer.hitTest(proj, docX, docY, 24f / f0.scale)
                    vm.select(hit)
                    if (hit != null) vm.notifyCanvasPick()
                    vm.beginGesture()
                    val slop = viewConfiguration.touchSlop
                    var travelled = 0f
                    var multi = false
                    var go = true
                    while (go) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.size > 1) multi = true
                        val zoom = ev.calculateZoom()
                        val pan = ev.calculatePan()
                        val rot = ev.calculateRotation()
                        travelled += pan.getDistance()
                        val f = fitDoc(size.width.toFloat(), size.height.toFloat(), proj.docW, proj.docH)
                        if (hit != null && (multi || travelled > slop)) {
                            if (pan != Offset.Zero || zoom != 1f || rot != 0f) {
                                vm.transformSelected(pan.x / f.scale, pan.y / f.scale, zoom, rot)
                            }
                            for (change in ev.changes) {
                                if (change.positionChanged()) change.consume()
                            }
                        }
                        go = ev.changes.any { it.pressed }
                    }
                    if (!multi && travelled <= slop) {
                        val now = System.currentTimeMillis()
                        if (hit != null && hit == vm.lastTapId && now - vm.lastTapAt < 350L) {
                            vm.lastTapId = null
                            editState.value.invoke()
                        } else {
                            vm.lastTapId = hit
                            vm.lastTapAt = now
                        }
                    }
                }
            }
        },
    ) {
        if (project != null) {
            val f = fitDoc(size.width, size.height, project.docW, project.docH)
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save()
                nc.translate(f.ox, f.oy)
                nc.scale(f.scale, f.scale)
                val sh = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                sh.color = android.graphics.Color.BLACK
                sh.setShadowLayer(28f / f.scale, 0f, 8f / f.scale, 0xB0000000.toInt())
                nc.drawRect(0f, 0f, project.docW.toFloat(), project.docH.toFloat(), sh)
                Renderer.drawDocument(nc, project, photo, vm.images, selectedId, 1f / f.scale, accent)
                val cursor = vm.eraserCursor()
                if (cursor != null) {
                    val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    ring.style = android.graphics.Paint.Style.STROKE
                    ring.strokeWidth = 2f / f.scale
                    ring.color = 0xFFFFFFFF.toInt()
                    val er = kotlin.math.max(4f, vm.eraserSize * project.docW * 0.5f)
                    nc.drawCircle(cursor[0], cursor[1], er, ring)
                }
                val live = vm.liveStroke()
                if (live != null && liveTick >= 0) {
                    nc.save()
                    nc.clipRect(0f, 0f, project.docW.toFloat(), project.docH.toFloat())
                    Renderer.drawStroke(nc, live)
                    nc.restore()
                }
                nc.restore()
                val loupeBmp = vm.loupe()
                if (vm.pickActive && pickTick >= 0 && loupeBmp != null) {
                    val dp = density
                    val r = 52f * dp
                    val margin = 8f * dp
                    val cx = vm.pickViewX.coerceIn(r + margin, kotlin.math.max(r + margin, size.width - r - margin))
                    var cy = vm.pickViewY - 110f * dp
                    val above = cy - r >= margin
                    if (!above) cy = vm.pickViewY + 110f * dp
                    val clip = android.graphics.Path()
                    clip.addCircle(cx, cy, r, android.graphics.Path.Direction.CW)
                    nc.save()
                    nc.clipPath(clip)
                    val bp = android.graphics.Paint()
                    bp.isFilterBitmap = false
                    nc.drawBitmap(loupeBmp, null, android.graphics.RectF(cx - r, cy - r, cx + r, cy + r), bp)
                    val cell = 2f * r / loupeBmp.width
                    val box = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    box.style = android.graphics.Paint.Style.STROKE
                    box.strokeWidth = 2f * dp
                    box.color = 0xFF000000.toInt()
                    nc.drawRect(cx - cell / 2f, cy - cell / 2f, cx + cell / 2f, cy + cell / 2f, box)
                    box.strokeWidth = 1f * dp
                    box.color = 0xFFFFFFFF.toInt()
                    nc.drawRect(cx - cell / 2f, cy - cell / 2f, cx + cell / 2f, cy + cell / 2f, box)
                    nc.restore()
                    val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    ring.style = android.graphics.Paint.Style.STROKE
                    ring.strokeWidth = 4f * dp
                    ring.color = vm.pickColor
                    nc.drawCircle(cx, cy, r, ring)
                    ring.strokeWidth = 1.5f * dp
                    ring.color = 0xFFFFFFFF.toInt()
                    nc.drawCircle(cx, cy, r + 2.5f * dp, ring)
                    // label: swatch + hex code
                    val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    tp.textSize = 14f * dp
                    tp.color = 0xFFFFFFFF.toInt()
                    tp.typeface = android.graphics.Typeface.MONOSPACE
                    val label = hexOf(vm.pickColor)
                    val tw = tp.measureText(label)
                    val lw = tw + 44f * dp
                    val lh = 30f * dp
                    val ly = if (above) cy + r + 10f * dp else cy - r - 10f * dp - lh
                    val lx = (cx - lw / 2f).coerceIn(margin, kotlin.math.max(margin, size.width - lw - margin))
                    val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    bg.color = 0xE6101216.toInt()
                    nc.drawRoundRect(android.graphics.RectF(lx, ly, lx + lw, ly + lh), lh / 2f, lh / 2f, bg)
                    val sw = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    sw.color = vm.pickColor
                    nc.drawCircle(lx + 15f * dp, ly + lh / 2f, 8f * dp, sw)
                    val fm = tp.fontMetrics
                    nc.drawText(label, lx + 30f * dp, ly + lh / 2f - (fm.ascent + fm.descent) / 2f, tp)
                }
            }
        }
    }
}
