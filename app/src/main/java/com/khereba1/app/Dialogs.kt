package com.khereba1.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

private val PALETTE: IntArray = intArrayOf(
    0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFE53935.toInt(), 0xFFFF7043.toInt(),
    0xFFFFB300.toInt(), 0xFFFFEE58.toInt(), 0xFF7CB342.toInt(), 0xFF00897B.toInt(),
    0xFF29B6F6.toInt(), 0xFF1E5BD8.toInt(), 0xFF5E35B1.toInt(), 0xFFD81B60.toInt(),
    0xFF6D4C41.toInt(), 0xFF9E9E9E.toInt(), 0xFF37474F.toInt(), 0xFFF5E6C8.toInt(),
)

/** A card-style dialog container used by the custom dialogs below. */
@Composable
internal fun CardDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            content()
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

/** Provided by the editor: starts "tap the canvas to pick a color". Null where picking from the canvas makes no sense. */
val LocalEyedropper = androidx.compose.runtime.compositionLocalOf<(((Int) -> Unit) -> Unit)?> { null }

fun hexOf(argb: Int): String {
    val rgb = String.format("%06X", argb and 0xFFFFFF)
    val a = (argb ushr 24) and 0xFF
    return if (a == 255) "#" + rgb else "#" + String.format("%02X", a) + rgb
}

/** Parses "RRGGBB", "AARRGGBB" or "RGB" (with or without #). Returns null if it is not a valid color. */
fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    return try {
        when (t.length) {
            3 -> {
                val r = t.substring(0, 1).repeat(2)
                val g = t.substring(1, 2).repeat(2)
                val b = t.substring(2, 3).repeat(2)
                (0xFF000000L or (r + g + b).toLong(16)).toInt()
            }
            6 -> (0xFF000000L or t.toLong(16)).toInt()
            8 -> t.toLong(16).toInt()
            else -> null
        }
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * Shows the current color with its hex code (tap to edit), an eyedropper that picks a color
 * from anywhere on the canvas, a row of swatches and a "custom" button that opens the full picker.
 */
@Composable
fun ColorRow(selected: Int, onPick: (Int) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val eyedropper = LocalEyedropper.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showPicker = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFBDBDBD.toInt()))
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Color(selected))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Text(
                    text = hexOf(selected),
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
            if (eyedropper != null) {
                Spacer(Modifier.width(8.dp))
                IconAction(
                    res = R.drawable.ic_pipette,
                    desc = stringResource(R.string.eyedropper_label),
                    onClick = { eyedropper(onPick) },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (c in PALETTE) {
                val isSelected = c == selected
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onPick(c) },
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759),
                                Color(0xFF00C7BE), Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF3B30),
                            ),
                        ),
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { showPicker = true },
                contentAlignment = Alignment.Center,
            ) {
                HIcon(R.drawable.ic_plus, stringResource(R.string.custom_color), tint = Color.White, size = 18.dp)
            }
        }
    }
    if (showPicker) {
        ColorPickerDialog(
            initial = selected,
            onDismiss = { showPicker = false },
            onConfirm = { c ->
                showPicker = false
                onPick(c)
            },
        )
    }
}

@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv0[0]) }
    var sat by remember { mutableFloatStateOf(hsv0[1]) }
    var value by remember { mutableFloatStateOf(hsv0[2]) }
    var alpha by remember { mutableFloatStateOf(android.graphics.Color.alpha(initial) / 255f) }
    var hex by remember { mutableStateOf(hexOf(initial).removePrefix("#")) }
    var exact by remember { mutableStateOf<Int?>(initial) } // the typed color, so hex round-trips without HSV rounding
    val argb = exact ?: android.graphics.Color.HSVToColor(
        (alpha * 255f).roundToInt().coerceIn(0, 255),
        floatArrayOf(hue, sat, value),
    )
    fun fromSliders() {
        exact = null
        hex = hexOf(
            android.graphics.Color.HSVToColor(
                (alpha * 255f).roundToInt().coerceIn(0, 255),
                floatArrayOf(hue, sat, value),
            ),
        ).removePrefix("#")
    }
    val hexValid = parseHex(hex) != null
    CardDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.pick_color), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFBDBDBD.toInt()))
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(argb)),
            )
            OutlinedTextField(
                value = hex,
                onValueChange = { raw ->
                    val cleaned = raw.removePrefix("#").filter { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' }.take(8).uppercase()
                    hex = cleaned
                    val parsed = parseHex(cleaned)
                    if (parsed != null) {
                        exact = parsed
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(parsed, hsv)
                        hue = hsv[0]
                        sat = hsv[1]
                        value = hsv[2]
                        alpha = android.graphics.Color.alpha(parsed) / 255f
                    }
                },
                label = { Text(stringResource(R.string.hex_label)) },
                prefix = { Text("#") },
                singleLine = true,
                isError = !hexValid,
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledSlider(stringResource(R.string.hue), hue, 0f..360f, hue.roundToInt().toString()) { hue = it; fromSliders() }
            LabeledSlider(stringResource(R.string.saturation), sat, 0f..1f, (sat * 100f).roundToInt().toString() + "%") { sat = it; fromSliders() }
            LabeledSlider(stringResource(R.string.brightness), value, 0f..1f, (value * 100f).roundToInt().toString() + "%") { value = it; fromSliders() }
            LabeledSlider(stringResource(R.string.alpha_label), alpha, 0f..1f, (alpha * 100f).roundToInt().toString() + "%") { alpha = it; fromSliders() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onConfirm(argb) }, enabled = hexValid) { Text(stringResource(R.string.ok)) }
            }
        }
    }
}

/** Add / edit text. [initial] is empty when adding. */
@Composable
fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Throwable) {
            // the keyboard just will not open automatically
        }
    }
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                label = { Text(stringResource(R.string.text_hint)) },
                minLines = 1,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(field.text) },
                enabled = field.text.isNotBlank(),
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ExportDialog(
    docW: Int,
    docH: Int,
    onDismiss: () -> Unit,
    onSave: (png: Boolean, share: Boolean) -> Unit,
) {
    var png by remember { mutableStateOf(true) }
    CardDialog(onDismiss = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.output_size, docW, docH),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { png = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = png, onClick = { png = true })
                Text(stringResource(R.string.format_png))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { png = false },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = !png, onClick = { png = false })
                Text(stringResource(R.string.format_jpg))
            }
            Button(onClick = { onSave(png, false) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_gallery))
            }
            OutlinedButton(onClick = { onSave(png, true) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_share))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

private class CanvasSize(val labelRes: Int, val w: Int, val h: Int)

private val CANVAS_SIZES = listOf(
    CanvasSize(R.string.size_square, 1080, 1080),
    CanvasSize(R.string.size_portrait, 1080, 1350),
    CanvasSize(R.string.size_story, 1080, 1920),
    CanvasSize(R.string.size_landscape, 1920, 1080),
)

@Composable
fun BlankCanvasDialog(onDismiss: () -> Unit, onCreate: (Int, Int, Int) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var color by remember { mutableIntStateOf(0xFFFFFFFF.toInt()) }
    CardDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.canvas_title), style = MaterialTheme.typography.titleMedium)
            for (i in CANVAS_SIZES.indices) {
                val s = CANVAS_SIZES[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { index = i },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = index == i, onClick = { index = i })
                    Text(stringResource(s.labelRes))
                }
            }
            Text(
                stringResource(R.string.background_color),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            ColorRow(selected = color, onPick = { color = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = {
                    val s = CANVAS_SIZES[index]
                    onCreate(s.w, s.h, color)
                }) { Text(stringResource(R.string.create)) }
            }
        }
    }
}

@Composable
fun SettingsDialog(context: Context, onDismiss: () -> Unit, onLanguage: (String) -> Unit) {
    var logText by remember { mutableStateOf(AppLog.read()) }
    var showLog by remember { mutableStateOf(false) }
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Throwable) {
            ""
        }
    }
    val emptyLog = stringResource(R.string.log_empty)
    CardDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.language_label), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onLanguage("system") }) { Text(stringResource(R.string.lang_system)) }
                OutlinedButton(onClick = { onLanguage("ar") }) { Text(stringResource(R.string.lang_arabic)) }
                OutlinedButton(onClick = { onLanguage("en") }) { Text(stringResource(R.string.lang_english)) }
            }
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.version_label, version) + "  ·  " +
                    stringResource(R.string.developer_label) + ": " + stringResource(R.string.developer_name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    showLog = !showLog
                    logText = AppLog.read()
                }) { Text(stringResource(R.string.error_log)) }
                if (showLog) {
                    TextButton(onClick = {
                        AppLog.clear()
                        logText = ""
                    }) { Text(stringResource(R.string.clear_log)) }
                }
            }
            if (showLog) {
                Text(
                    text = if (logText.isEmpty()) emptyLog else logText.takeLast(6000),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.ok))
            }
        }
    }
}
