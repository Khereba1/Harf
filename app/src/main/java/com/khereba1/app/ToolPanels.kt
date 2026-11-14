package com.khereba1.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Sub-tabs on top, the selected tab's content below. */
@Composable
fun TabbedPanel(labels: List<String>, tab: Int, onTab: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    val t = if (tab in labels.indices) tab else 0
    Column(Modifier.fillMaxSize()) {
        SubTabs(labels, t, onTab)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxWidth().weight(1f)) { content(t) }
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Properties of the selected layer. The sub-tabs depend on what kind of layer it is. */
@Composable
fun EditPanel(vm: HarfViewModel, project: Project, selected: Layer?) {
    var textTab by remember { mutableIntStateOf(0) }
    var shapeTab by remember { mutableIntStateOf(0) }
    var imageTab by remember { mutableIntStateOf(0) }
    if (selected == null) {
        CenterHint(stringResource(R.string.hint_empty))
    } else if (selected.type == TYPE_TEXT) {
        val labels = listOf(
            stringResource(R.string.tab_font),
            stringResource(R.string.tab_color),
            stringResource(R.string.tab_layout),
            stringResource(R.string.tab_outline),
            stringResource(R.string.tab_shadow),
            stringResource(R.string.tab_box),
            stringResource(R.string.tab_gradient),
        )
        val colorLabel = stringResource(R.string.text_color)
        TabbedPanel(labels, textTab, { textTab = it }) { t ->
            when (t) {
                0 -> FontPanel(vm, selected)
                1 -> ColorPanel(vm, selected, colorLabel)
                2 -> LayoutPanel(vm, selected, project.docW)
                3 -> OutlinePanel(vm, selected)
                4 -> ShadowPanel(vm, selected)
                5 -> BoxPanel(vm, selected)
                else -> GradientPanel(vm, selected)
            }
        }
    } else if (selected.type == TYPE_SHAPE) {
        val labels = listOf(
            stringResource(R.string.tab_fill),
            stringResource(R.string.tab_layout),
            stringResource(R.string.tab_outline),
            stringResource(R.string.tab_shadow),
        )
        TabbedPanel(labels, shapeTab, { shapeTab = it }) { t ->
            when (t) {
                0 -> ShapeFillPanel(vm, selected)
                1 -> LayoutPanel(vm, selected, project.docW)
                2 -> OutlinePanel(vm, selected)
                else -> ShadowPanel(vm, selected)
            }
        }
    } else if (selected.type == TYPE_IMAGE) {
        val labels = listOf(
            stringResource(R.string.tab_layout),
            stringResource(R.string.flip_label),
        )
        TabbedPanel(labels, imageTab, { imageTab = it }) { t ->
            if (t == 0) LayoutPanel(vm, selected, project.docW) else ImageFlipPanel(vm, selected)
        }
    } else {
        CenterHint(stringResource(R.string.hint_draw_layer))
    }
}

/** Things to put on the canvas: text, shapes, emoji, pictures. */
@Composable
fun AddPanel(vm: HarfViewModel, onAddText: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val labels = listOf(
        stringResource(R.string.tool_text),
        stringResource(R.string.tool_shapes),
        stringResource(R.string.tool_emoji),
        stringResource(R.string.tool_image),
    )
    TabbedPanel(labels, tab, { tab = it }) { t ->
        when (t) {
            0 -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Button(onClick = onAddText, shape = RoundedCornerShape(14.dp)) {
                    HIcon(R.drawable.ic_text_add, null, tint = MaterialTheme.colorScheme.onPrimary, size = 20.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.add_text))
                }
            }
            1 -> ShapeAddPanel(vm)
            2 -> EmojiPanel(vm)
            else -> ImageAddPanel(vm)
        }
    }
}

/** Brush and eraser. Drawing happens on the canvas while this tool is active. */
@Composable
fun DrawPanel(vm: HarfViewModel) {
    val labels = listOf(stringResource(R.string.brush_label), stringResource(R.string.eraser_label))
    TabbedPanel(labels, if (vm.eraser) 1 else 0, { vm.eraser = it == 1 }) { t ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            if (t == 0) {
                LabeledSlider(
                    label = stringResource(R.string.brush_size),
                    value = vm.brushSize,
                    range = 0.004f..0.12f,
                    valueText = pct(vm.brushSize),
                ) { v -> vm.brushSize = v }
                Text(stringResource(R.string.color_label), style = MaterialTheme.typography.bodyMedium)
                ColorRow(selected = vm.brushColor, onPick = { c -> vm.brushColor = c })
            } else {
                LabeledSlider(
                    label = stringResource(R.string.eraser_size),
                    value = vm.eraserSize,
                    range = 0.01f..0.2f,
                    valueText = pct(vm.eraserSize),
                ) { v -> vm.eraserSize = v }
                SwitchRow(stringResource(R.string.erase_whole), vm.eraseWhole) { vm.eraseWhole = it }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.draw_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
