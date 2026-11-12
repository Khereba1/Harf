package com.khereba1.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private fun pct(v: Float): String = (v * 100f).roundToInt().toString() + "%"

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

/** Draws "حرف Abc" in the given font, using the same Typeface the renderer uses. */
@Composable
private fun FontSample(fontKey: String, color: Int, modifier: Modifier) {
    Canvas(modifier = modifier) {
        drawIntoCanvas { c ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.typeface = FontStore.typeface(fontKey, false, false)
            paint.textSize = size.height * 0.46f
            paint.color = color
            c.nativeCanvas.drawText("حرف Abc", 12.dp.toPx(), size.height * 0.62f, paint)
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun FontPanel(vm: HarfViewModel, item: TextItem) {
    val fonts = remember(vm.fontsVersion) { FontStore.allFonts() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFont(uri)
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = item.bold,
                onClick = { vm.editItem("bold", true) { it.copy(bold = !it.bold) } },
                label = { Text(stringResource(R.string.bold)) },
            )
            FilterChip(
                selected = item.italic,
                onClick = { vm.editItem("italic", true) { it.copy(italic = !it.italic) } },
                label = { Text(stringResource(R.string.italic)) },
            )
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                Text(stringResource(R.string.import_font))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(fonts, key = { it.key }) { f ->
                val selected = f.key == item.fontKey
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { vm.editItem("font", true) { it.copy(fontKey = f.key) } },
                ) {
                    FontSample(f.key, textColor, Modifier.fillMaxSize())
                    Text(
                        text = f.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ColorPanel(vm: HarfViewModel, item: TextItem) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.text_color), style = MaterialTheme.typography.titleSmall)
        ColorRow(selected = item.color, onPick = { c -> vm.editItem("color", false) { it.copy(color = c) } })
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun LayoutPanel(vm: HarfViewModel, item: TextItem, docW: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        LabeledSlider(
            label = stringResource(R.string.size_label),
            value = item.size / docW,
            range = 0.02f..0.4f,
            valueText = pct(item.size / docW),
        ) { v -> vm.editItem("size", false) { it.copy(size = v * docW) } }
        LabeledSlider(
            label = stringResource(R.string.rotation_label),
            value = item.rotation,
            range = -180f..180f,
            valueText = item.rotation.roundToInt().toString() + "°",
        ) { v -> vm.editItem("rotation", false) { it.copy(rotation = v) } }
        LabeledSlider(
            label = stringResource(R.string.opacity_label),
            value = item.opacity,
            range = 0f..1f,
            valueText = pct(item.opacity),
        ) { v -> vm.editItem("opacity", false) { it.copy(opacity = v) } }
        LabeledSlider(
            label = stringResource(R.string.line_spacing),
            value = item.lineSpacing,
            range = 0.7f..2f,
            valueText = pct(item.lineSpacing),
        ) { v -> vm.editItem("lineSpacing", false) { it.copy(lineSpacing = v) } }
        Text(stringResource(R.string.alignment), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = item.align == ALIGN_START,
                onClick = { vm.editItem("align", true) { it.copy(align = ALIGN_START) } },
                label = { Text(stringResource(R.string.align_start)) },
            )
            FilterChip(
                selected = item.align == ALIGN_CENTER,
                onClick = { vm.editItem("align", true) { it.copy(align = ALIGN_CENTER) } },
                label = { Text(stringResource(R.string.align_center)) },
            )
            FilterChip(
                selected = item.align == ALIGN_END,
                onClick = { vm.editItem("align", true) { it.copy(align = ALIGN_END) } },
                label = { Text(stringResource(R.string.align_end)) },
            )
        }
    }
}

@Composable
fun OutlinePanel(vm: HarfViewModel, item: TextItem) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        SwitchRow(stringResource(R.string.tab_outline), item.strokeOn) { on ->
            vm.editItem("strokeOn", true) { it.copy(strokeOn = on) }
        }
        if (item.strokeOn) {
            LabeledSlider(
                label = stringResource(R.string.width_label),
                value = item.strokeRel,
                range = 0.005f..0.25f,
                valueText = pct(item.strokeRel),
            ) { v -> vm.editItem("strokeRel", false) { it.copy(strokeRel = v) } }
            Text(stringResource(R.string.color_label), style = MaterialTheme.typography.bodyMedium)
            ColorRow(selected = item.strokeColor, onPick = { c -> vm.editItem("strokeColor", false) { it.copy(strokeColor = c) } })
        }
    }
}

@Composable
fun ShadowPanel(vm: HarfViewModel, item: TextItem) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        SwitchRow(stringResource(R.string.tab_shadow), item.shadowOn) { on ->
            vm.editItem("shadowOn", true) { it.copy(shadowOn = on) }
        }
        if (item.shadowOn) {
            LabeledSlider(
                label = stringResource(R.string.blur_label),
                value = item.shadowBlur,
                range = 0f..0.3f,
                valueText = pct(item.shadowBlur),
            ) { v -> vm.editItem("shadowBlur", false) { it.copy(shadowBlur = v) } }
            LabeledSlider(
                label = stringResource(R.string.offset_x),
                value = item.shadowDx,
                range = -0.25f..0.25f,
                valueText = pct(item.shadowDx),
            ) { v -> vm.editItem("shadowDx", false) { it.copy(shadowDx = v) } }
            LabeledSlider(
                label = stringResource(R.string.offset_y),
                value = item.shadowDy,
                range = -0.25f..0.25f,
                valueText = pct(item.shadowDy),
            ) { v -> vm.editItem("shadowDy", false) { it.copy(shadowDy = v) } }
            Text(stringResource(R.string.color_label), style = MaterialTheme.typography.bodyMedium)
            ColorRow(selected = item.shadowColor, onPick = { c -> vm.editItem("shadowColor", false) { it.copy(shadowColor = c) } })
        }
    }
}

@Composable
fun BoxPanel(vm: HarfViewModel, item: TextItem) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        SwitchRow(stringResource(R.string.tab_box), item.bgOn) { on ->
            vm.editItem("bgOn", true) { it.copy(bgOn = on) }
        }
        if (item.bgOn) {
            LabeledSlider(
                label = stringResource(R.string.padding_label),
                value = item.bgPad,
                range = 0f..1f,
                valueText = pct(item.bgPad),
            ) { v -> vm.editItem("bgPad", false) { it.copy(bgPad = v) } }
            LabeledSlider(
                label = stringResource(R.string.corner_radius),
                value = item.bgRadius,
                range = 0f..1f,
                valueText = pct(item.bgRadius),
            ) { v -> vm.editItem("bgRadius", false) { it.copy(bgRadius = v) } }
            Text(stringResource(R.string.color_label), style = MaterialTheme.typography.bodyMedium)
            ColorRow(selected = item.bgColor, onPick = { c -> vm.editItem("bgColor", false) { it.copy(bgColor = c) } })
        }
    }
}
