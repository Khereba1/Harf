package com.khereba1.app

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private const val TOOL_TEXT = 0
private const val TOOL_SHAPES = 1
private const val TOOL_EMOJI = 2
private const val TOOL_LAYERS = 3

private const val DIALOG_NONE = 0
private const val DIALOG_ADD = 1
private const val DIALOG_EDIT = 2

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun EditorScreen(vm: HarfViewModel) {
    val project = vm.project ?: return
    val context = LocalContext.current
    var tool by remember { mutableIntStateOf(TOOL_TEXT) }
    var panelOpen by remember { mutableStateOf(true) }
    var textTab by remember { mutableIntStateOf(0) }
    var shapeTab by remember { mutableIntStateOf(0) }
    var emojiTab by remember { mutableIntStateOf(0) }
    var textDialog by remember { mutableIntStateOf(DIALOG_NONE) }
    var showExport by remember { mutableStateOf(false) }
    var showClose by remember { mutableStateOf(false) }
    val selectedId = vm.selectedId
    val selected: Layer? = project.items.firstOrNull { it.id == selectedId }

    // Selecting a layer on the canvas opens the matching tool (the Layers tool stays as it is).
    LaunchedEffect(selectedId) {
        val s = project.items.firstOrNull { it.id == selectedId }
        if (textDialog == DIALOG_EDIT && (s == null || s.type != TYPE_TEXT)) textDialog = DIALOG_NONE
        if (s != null) {
            tool = when {
                tool == TOOL_LAYERS -> TOOL_LAYERS
                s.type == TYPE_SHAPE -> TOOL_SHAPES
                tool == TOOL_EMOJI -> TOOL_EMOJI
                else -> TOOL_TEXT
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ----- top bar -----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showClose = true }) { Text(stringResource(R.string.close)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.undo() }, enabled = vm.undoDepth > 0) { Text(stringResource(R.string.undo)) }
            TextButton(onClick = { vm.redo() }, enabled = vm.redoDepth > 0) { Text(stringResource(R.string.redo)) }
            Button(onClick = { showExport = true }) { Text(stringResource(R.string.save)) }
        }

        // ----- canvas -----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            EditorCanvas(
                vm = vm,
                onEditRequest = {
                    val sel = vm.project?.items?.firstOrNull { it.id == vm.selectedId }
                    if (sel != null && sel.type == TYPE_TEXT) textDialog = DIALOG_EDIT
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (vm.busy) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        // ----- bottom controls -----
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                // actions for the selected layer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(onClick = { textDialog = DIALOG_ADD }) { Text(stringResource(R.string.add_text)) }
                    if (selected != null) {
                        if (selected.type == TYPE_TEXT) {
                            OutlinedButton(onClick = { textDialog = DIALOG_EDIT }) { Text(stringResource(R.string.edit_text)) }
                        }
                        OutlinedButton(onClick = { vm.duplicateSelected() }) { Text(stringResource(R.string.duplicate)) }
                        OutlinedButton(onClick = { vm.toggleLock(selected.id) }) {
                            Text(stringResource(if (selected.locked) R.string.unlock else R.string.lock))
                        }
                        OutlinedButton(onClick = { vm.moveSelectedForward(true) }) { Text(stringResource(R.string.bring_forward)) }
                        OutlinedButton(onClick = { vm.moveSelectedForward(false) }) { Text(stringResource(R.string.send_backward)) }
                        OutlinedButton(onClick = { vm.deleteSelected() }) { Text(stringResource(R.string.delete)) }
                    }
                }
                // tools: tap the active one again to fold the panel away
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val toolLabels = listOf(
                        stringResource(R.string.tool_text),
                        stringResource(R.string.tool_shapes),
                        stringResource(R.string.tool_emoji),
                        stringResource(R.string.tool_layers),
                    )
                    for (i in toolLabels.indices) {
                        FilterChip(
                            selected = tool == i && panelOpen,
                            onClick = {
                                if (tool == i) {
                                    panelOpen = !panelOpen
                                } else {
                                    tool = i
                                    panelOpen = true
                                }
                            },
                            label = { Text(toolLabels[i]) },
                        )
                    }
                }
                if (panelOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(215.dp)
                            .padding(top = 6.dp),
                    ) {
                        when (tool) {
                            TOOL_TEXT -> {
                                if (selected != null && selected.type == TYPE_TEXT) {
                                    val labels = listOf(
                                        stringResource(R.string.tab_font),
                                        stringResource(R.string.tab_color),
                                        stringResource(R.string.tab_layout),
                                        stringResource(R.string.tab_outline),
                                        stringResource(R.string.tab_shadow),
                                        stringResource(R.string.tab_box),
                                    )
                                    Column(Modifier.fillMaxSize()) {
                                        SubTabs(labels, textTab) { textTab = it }
                                        Spacer(Modifier.height(6.dp))
                                        Box(Modifier.fillMaxWidth().weight(1f)) {
                                            when (textTab) {
                                                0 -> FontPanel(vm, selected)
                                                1 -> ColorPanel(vm, selected, stringResource(R.string.text_color))
                                                2 -> LayoutPanel(vm, selected, project.docW)
                                                3 -> OutlinePanel(vm, selected)
                                                4 -> ShadowPanel(vm, selected)
                                                else -> BoxPanel(vm, selected)
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.hint_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                            TOOL_SHAPES -> {
                                if (selected != null && selected.type == TYPE_SHAPE) {
                                    val labels = listOf(
                                        stringResource(R.string.tab_add),
                                        stringResource(R.string.tab_fill),
                                        stringResource(R.string.tab_layout),
                                        stringResource(R.string.tab_outline),
                                        stringResource(R.string.tab_shadow),
                                    )
                                    Column(Modifier.fillMaxSize()) {
                                        SubTabs(labels, shapeTab) { shapeTab = it }
                                        Spacer(Modifier.height(6.dp))
                                        Box(Modifier.fillMaxWidth().weight(1f)) {
                                            when (shapeTab) {
                                                0 -> ShapeAddPanel(vm)
                                                1 -> ShapeFillPanel(vm, selected)
                                                2 -> LayoutPanel(vm, selected, project.docW)
                                                3 -> OutlinePanel(vm, selected)
                                                else -> ShadowPanel(vm, selected)
                                            }
                                        }
                                    }
                                } else {
                                    ShapeAddPanel(vm)
                                }
                            }
                            TOOL_EMOJI -> {
                                if (selected != null && selected.type == TYPE_TEXT) {
                                    val labels = listOf(
                                        stringResource(R.string.tab_add),
                                        stringResource(R.string.tab_layout),
                                    )
                                    Column(Modifier.fillMaxSize()) {
                                        SubTabs(labels, emojiTab) { emojiTab = it }
                                        Spacer(Modifier.height(6.dp))
                                        Box(Modifier.fillMaxWidth().weight(1f)) {
                                            if (emojiTab == 0) EmojiPanel(vm) else LayoutPanel(vm, selected, project.docW)
                                        }
                                    }
                                } else {
                                    EmojiPanel(vm)
                                }
                            }
                            else -> LayersPanel(vm, project)
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
                tool = TOOL_TEXT
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
private fun EditorCanvas(vm: HarfViewModel, onEditRequest: () -> Unit, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val project = vm.project
    val photo = vm.photo
    val selectedId = vm.selectedId
    val editState = rememberUpdatedState(onEditRequest)

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val proj = vm.project
                if (proj != null) {
                    val f0 = fitDoc(size.width.toFloat(), size.height.toFloat(), proj.docW, proj.docH)
                    val docX = (down.position.x - f0.ox) / f0.scale
                    val docY = (down.position.y - f0.oy) / f0.scale
                    val hit = Renderer.hitTest(proj, docX, docY, 24f / f0.scale)
                    vm.select(hit)
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
                Renderer.drawDocument(nc, project, photo, selectedId, 1f / f.scale, accent)
                nc.restore()
            }
        }
    }
}
