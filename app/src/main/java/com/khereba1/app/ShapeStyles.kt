package com.khereba1.app

import android.graphics.Color

/** One ready-made look for a shape: a function that restyles a shape layer and keeps its geometry. */
class ShapeStyleDef(val transform: (Layer) -> Layer)

object ShapeStyles {
    private fun base(l: Layer): Layer = l.copy(
        gradOn = false, strokeOn = false, shadowOn = false, opacity = 1f, fillOn = true,
    )

    val all: List<ShapeStyleDef> = listOf(
        // solid coral
        ShapeStyleDef { base(it).copy(color = 0xFFE4572E.toInt()) },
        // sunset gradient
        ShapeStyleDef { base(it).copy(color = 0xFFFF512F.toInt(), gradOn = true, gradColor = 0xFFF09819.toInt(), gradAngle = 90f) },
        // ocean gradient
        ShapeStyleDef { base(it).copy(color = 0xFF2193B0.toInt(), gradOn = true, gradColor = 0xFF6DD5ED.toInt(), gradAngle = 45f) },
        // forest gradient
        ShapeStyleDef { base(it).copy(color = 0xFF11998E.toInt(), gradOn = true, gradColor = 0xFF38EF7D.toInt(), gradAngle = 90f) },
        // purple haze gradient
        ShapeStyleDef { base(it).copy(color = 0xFF8E2DE2.toInt(), gradOn = true, gradColor = 0xFF4A00E0.toInt(), gradAngle = 135f) },
        // white with soft shadow
        ShapeStyleDef { base(it).copy(color = Color.WHITE, shadowOn = true, shadowColor = 0x66000000, shadowBlur = 0.08f, shadowDx = 0.02f, shadowDy = 0.04f) },
        // dark with gold outline
        ShapeStyleDef { base(it).copy(color = 0xFF1B1B1F.toInt(), strokeOn = true, strokeColor = 0xFFFFC107.toInt(), strokeRel = 0.04f) },
        // outline only
        ShapeStyleDef { base(it).copy(fillOn = false, strokeOn = true, strokeColor = Color.WHITE, strokeRel = 0.05f) },
        // glass: translucent white with outline
        ShapeStyleDef { base(it).copy(color = Color.WHITE, opacity = 0.35f, strokeOn = true, strokeColor = Color.WHITE, strokeRel = 0.02f) },
        // flat shadow (hard offset)
        ShapeStyleDef { base(it).copy(color = 0xFFFFD166.toInt(), shadowOn = true, shadowColor = 0xFF000000.toInt(), shadowBlur = 0f, shadowDx = 0.05f, shadowDy = 0.05f) },
        // black
        ShapeStyleDef { base(it).copy(color = 0xFF111111.toInt()) },
        // pink to red
        ShapeStyleDef { base(it).copy(color = 0xFFFF9A9E.toInt(), gradOn = true, gradColor = 0xFFFE5196.toInt(), gradAngle = 90f) },
    )
}
