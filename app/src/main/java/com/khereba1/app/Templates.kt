package com.khereba1.app

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Collects the layers of a template; ids start at 1 and the first layer added is the bottom one. */
class TemplateBuilder {
    var next = 1L
    val layers = ArrayList<Layer>()

    fun text(
        t: String, cx: Float, cy: Float, size: Float, color: Int,
        bold: Boolean = true, extra: (Layer) -> Layer = { it },
    ) {
        layers.add(extra(Layer(id = next, text = t, cx = cx, cy = cy, size = size, color = color, bold = bold)))
        next++
    }

    fun shape(
        kind: Int, cx: Float, cy: Float, w: Float, aspect: Float, color: Int,
        extra: (Layer) -> Layer = { it },
    ) {
        layers.add(
            extra(
                Layer(
                    id = next, type = TYPE_SHAPE, cx = cx, cy = cy, size = w,
                    shapeKind = kind, aspect = aspect, color = color, corner = 0f,
                ),
            ),
        )
        next++
    }
}

/** A starter design: canvas size, background and a function that fills in the layers. */
class TemplateDef(
    val nameRes: Int,
    val w: Int,
    val h: Int,
    val bg: Int,
    val build: (Context, TemplateBuilder) -> Unit,
)

object Templates {
    private const val GOLD = 0xFFFFC107.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    val all: List<TemplateDef> = listOf(
        // quote card
        TemplateDef(R.string.tpl_name_quote, 1080, 1080, 0xFF1F2A44.toInt()) { c, b ->
            b.text("“", 540f, 290f, 360f, GOLD)
            b.text(c.getString(R.string.tpl_quote_text), 540f, 560f, 84f, WHITE, extra = { it.copy(lineSpacing = 1.15f) })
            b.shape(Shapes.K_RECT, 540f, 790f, 160f, 0.03f, GOLD)
            b.text(c.getString(R.string.tpl_quote_author), 540f, 880f, 46f, GOLD, bold = false)
        },
        // story
        TemplateDef(R.string.tpl_name_story, 1080, 1920, 0xFF000000.toInt()) { c, b ->
            b.shape(
                Shapes.K_RECT, 540f, 960f, 1080f, 1920f / 1080f, 0xFFFF512F.toInt(),
                extra = { it.copy(gradOn = true, gradColor = 0xFFDD2476.toInt(), gradAngle = 90f) },
            )
            b.shape(1, 860f, 360f, 520f, 1f, WHITE, extra = { it.copy(opacity = 0.15f) })
            b.shape(1, 200f, 1500f, 380f, 1f, WHITE, extra = { it.copy(opacity = 0.12f) })
            b.text(c.getString(R.string.tpl_story_title), 540f, 880f, 200f, WHITE, extra = { it.copy(lineSpacing = 1.05f) })
            b.text(c.getString(R.string.tpl_story_sub), 540f, 1500f, 64f, WHITE, bold = false)
        },
        // sale poster
        TemplateDef(R.string.tpl_name_sale, 1080, 1350, 0xFFD62828.toInt()) { c, b ->
            b.shape(1, 540f, 560f, 860f, 1f, 0xFFFFD166.toInt())
            b.text(c.getString(R.string.tpl_sale_big), 540f, 470f, 230f, 0xFFD62828.toInt())
            b.text(c.getString(R.string.tpl_sale_off), 540f, 700f, 120f, 0xFF1B1B1F.toInt())
            b.shape(
                Shapes.K_RECT, 540f, 1140f, 520f, 0.28f, WHITE,
                extra = { it.copy(corner = 0.5f) },
            )
            b.text(c.getString(R.string.tpl_sale_btn), 540f, 1140f, 66f, 0xFFD62828.toInt())
        },
        // invitation
        TemplateDef(R.string.tpl_name_invitation, 1080, 1350, 0xFFFFF8E7.toInt()) { c, b ->
            b.shape(
                Shapes.K_RECT, 540f, 675f, 940f, 1230f / 940f, GOLD,
                extra = { it.copy(fillOn = false, strokeOn = true, strokeColor = GOLD, strokeRel = 0.008f) },
            )
            b.text(c.getString(R.string.tpl_inv_title), 540f, 420f, 100f, 0xFF5A3E1B.toInt())
            b.shape(Shapes.K_RECT, 540f, 540f, 220f, 0.012f, GOLD)
            b.text(c.getString(R.string.tpl_inv_name), 540f, 700f, 130f, 0xFF1B1B1F.toInt())
            b.text(c.getString(R.string.tpl_inv_date), 540f, 930f, 60f, 0xFF5A3E1B.toInt(), bold = false)
        },
        // video thumbnail
        TemplateDef(R.string.tpl_name_thumb, 1280, 720, 0xFF101820.toInt()) { c, b ->
            b.shape(19, 420f, 360f, 840f, 0.62f, GOLD)
            b.text(
                c.getString(R.string.tpl_thumb_main), 420f, 360f, 120f, 0xFF101820.toInt(),
                extra = { it.copy(lineSpacing = 1.05f) },
            )
            b.shape(
                Shapes.K_RECT, 1040f, 620f, 300f, 0.3f, 0xFFE4572E.toInt(),
                extra = { it.copy(corner = 0.3f) },
            )
            b.text(c.getString(R.string.tpl_thumb_sub), 1040f, 620f, 46f, WHITE)
        },
    )

    fun build(t: TemplateDef, ctx: Context): Project {
        val b = TemplateBuilder()
        t.build(ctx, b)
        return Project(t.w, t.h, t.bg, false, b.layers, b.next)
    }
}

@Composable
fun TemplatesDialog(vm: HarfViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.templates_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
            ) {
                items(Templates.all) { t ->
                    val bmp = remember(t) {
                        Renderer.thumbnail(Templates.build(t, ctx), null, emptyMap(), 320)
                    }
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                vm.startTemplate(t)
                                onDismiss()
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                        Text(
                            stringResource(t.nameRes),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "${t.w} × ${t.h}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
