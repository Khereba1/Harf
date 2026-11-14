package com.khereba1.app

import android.graphics.Color

/** A ready-made look for text: sets color, outline, shadow, gradient and box in one tap. */
class TextStyleDef(val apply: (Layer) -> Layer)

object TextStyles {
    private fun Layer.plain(): Layer = copy(
        color = Color.WHITE,
        strokeOn = false,
        shadowOn = false,
        bgOn = false,
        gradOn = false,
    )

    val all: List<TextStyleDef> = listOf(
        // classic: white with a black outline
        TextStyleDef { it.plain().copy(strokeOn = true, strokeColor = Color.BLACK, strokeRel = 0.07f) },
        // neon glow
        TextStyleDef {
            it.plain().copy(
                strokeOn = true, strokeColor = 0xFFFF2BD6.toInt(), strokeRel = 0.02f,
                shadowOn = true, shadowColor = 0xFFFF2BD6.toInt(), shadowBlur = 0.18f, shadowDx = 0f, shadowDy = 0f,
            )
        },
        // soft shadow
        TextStyleDef {
            it.plain().copy(shadowOn = true, shadowColor = 0xB3000000.toInt(), shadowBlur = 0.08f, shadowDx = 0.03f, shadowDy = 0.04f)
        },
        // sticker: yellow, thick outline, hard shadow
        TextStyleDef {
            it.plain().copy(
                color = 0xFFFFD60A.toInt(), strokeOn = true, strokeColor = Color.BLACK, strokeRel = 0.09f,
                shadowOn = true, shadowColor = Color.BLACK, shadowBlur = 0.001f, shadowDx = 0.05f, shadowDy = 0.05f,
            )
        },
        // gold
        TextStyleDef {
            it.plain().copy(
                gradOn = true, color = 0xFFFFE259.toInt(), gradColor = 0xFFC47F00.toInt(), gradAngle = 90f,
                strokeOn = true, strokeColor = 0xFF5A3A00.toInt(), strokeRel = 0.03f,
            )
        },
        // fire
        TextStyleDef {
            it.plain().copy(
                gradOn = true, color = 0xFFFFEB3B.toInt(), gradColor = 0xFFE53935.toInt(), gradAngle = 90f,
                shadowOn = true, shadowColor = 0xB3000000.toInt(), shadowBlur = 0.06f, shadowDx = 0.02f, shadowDy = 0.03f,
            )
        },
        // ocean
        TextStyleDef {
            it.plain().copy(gradOn = true, color = 0xFF00E5FF.toInt(), gradColor = 0xFF2962FF.toInt(), gradAngle = 0f)
        },
        // label: white on a dark box
        TextStyleDef {
            it.plain().copy(bgOn = true, bgColor = 0xCC000000.toInt(), bgPad = 0.3f, bgRadius = 0.25f)
        },
        // highlight: black on a yellow box
        TextStyleDef {
            it.plain().copy(color = Color.BLACK, bgOn = true, bgColor = 0xFFFFEB3B.toInt(), bgPad = 0.25f, bgRadius = 0.15f)
        },
        // outline only
        TextStyleDef {
            it.plain().copy(color = 0x00FFFFFF, strokeOn = true, strokeColor = Color.WHITE, strokeRel = 0.04f)
        },
        // retro: warm yellow with a hard red shadow
        TextStyleDef {
            it.plain().copy(
                color = 0xFFFFC857.toInt(), shadowOn = true, shadowColor = 0xFFE4572E.toInt(),
                shadowBlur = 0.001f, shadowDx = 0.06f, shadowDy = 0.06f,
            )
        },
        // pastel
        TextStyleDef {
            it.plain().copy(
                color = 0xFFFFC2D4.toInt(), shadowOn = true, shadowColor = 0x99B03060.toInt(),
                shadowBlur = 0.08f, shadowDx = 0.02f, shadowDy = 0.03f,
            )
        },
    )
}
