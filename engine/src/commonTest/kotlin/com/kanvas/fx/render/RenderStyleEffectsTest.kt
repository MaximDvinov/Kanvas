package com.kanvas.fx.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RenderStyleEffectsTest {
    @Test
    fun keepsLegacyLinearGradientAndGlowApi() {
        val style = RenderStyleBuilder().apply {
            linearGradient(Color.Red, Color.Blue, start = Offset.Zero, end = Offset(10f, 0f))
            glow(alpha = 0.4f, widthMultiplier = 2.5f)
        }.build()

        assertEquals(GradientKind.Linear, style.gradient?.kind)
        assertEquals(Offset.Zero, style.gradient?.start)
        assertEquals(Offset(10f, 0f), style.gradient?.end)
        assertNotNull(style.glow)
        assertEquals(0.4f, style.glow.alpha)
        assertEquals(2.5f, style.glow.widthMultiplier)
    }

    @Test
    fun buildsRadialAndSweepGradients() {
        val radial = RenderStyleBuilder().apply {
            radialGradient(Color.White, Color.Black, center = Offset(4f, 5f), radius = 12f)
        }.build()
        val sweep = RenderStyleBuilder().apply {
            sweepGradient(listOf(Color.Red, Color.Green, Color.Blue), center = Offset(1f, 2f))
        }.build()

        assertEquals(GradientKind.Radial, radial.gradient?.kind)
        assertEquals(Offset(4f, 5f), radial.gradient?.center)
        assertEquals(12f, radial.gradient?.radius)
        assertEquals(GradientKind.Sweep, sweep.gradient?.kind)
        assertEquals(Offset(1f, 2f), sweep.gradient?.center)
    }

    @Test
    fun buildsComposableEffectsList() {
        val style = RenderStyleBuilder().apply {
            blur(6f)
            shadow(color = Color.Black, radius = 8f)
            innerShadow(width = 3f)
            outline(color = Color.White, width = 2f)
            bloom(alpha = 0.2f)
            opacity(0.75f)
            colorFilter(Color.Red, alpha = 0.5f)
        }.build()

        assertEquals(
            listOf(
                BuiltInEffectKind.Blur,
                BuiltInEffectKind.Shadow,
                BuiltInEffectKind.InnerShadow,
                BuiltInEffectKind.Outline,
                BuiltInEffectKind.Bloom,
                BuiltInEffectKind.Opacity,
                BuiltInEffectKind.ColorFilter,
            ),
            style.effects.map { it.kind },
        )
    }
}
