package com.kanvas.fx.render

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color

/** Object-level effect quality presets. */
enum class EffectQuality {
    Low,
    Medium,
    High,
}

/** Effect execution phase relative to base primitive draw. */
enum class EffectPhase {
    BeforeBase,
    AfterBase,
}

/** Supported built-in effect kinds. */
enum class EffectKind {
    Shader,
    Glow,
    BloomLite,
}

/** Object-level light model parameters. */
data class LightingConfig(
    val enabled: Boolean = false,
    val ambientColor: Color = Color(0x33202028),
    val ambientIntensity: Float = 0.2f,
    val maxLightsPerObject: Int = 4,
)

/** One effect pass in a material stack. */
data class EffectPass(
    val kind: EffectKind,
    val phase: EffectPhase = EffectPhase.AfterBase,
    val shaderId: String? = null,
    val uniforms: Map<String, ShaderUniform> = emptyMap(),
    val paddingPx: Float = 0f,
    val intensity: Float = 1f,
    val blendMode: BlendMode = BlendMode.Screen,
    val color: Color = Color.White,
)

/** Grouped passes for material pipeline stages. */
data class EffectStack(
    val basePass: List<EffectPass> = emptyList(),
    val emissionPass: List<EffectPass> = emptyList(),
    val lightingPass: List<EffectPass> = emptyList(),
    val postPass: List<EffectPass> = emptyList(),
)

/** Object-attached render material. */
data class RenderMaterial(
    val id: String? = null,
    val quality: EffectQuality = EffectQuality.Medium,
    val effects: EffectStack = EffectStack(),
    val lighting: LightingConfig = LightingConfig(),
)

/** Scene light source used by material lighting pass. */
data class Light2D(
    val id: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val intensity: Float = 1f,
)

/** Render context shared with shader/runtime passes for one draw call. */
data class RenderContext(
    val entityId: String,
    val frameIndex: Long,
    val elapsedSeconds: Float,
    val worldCenterX: Float,
    val worldCenterY: Float,
    val worldRadius: Float,
    val screenLeft: Float,
    val screenTop: Float,
    val screenWidth: Float,
    val screenHeight: Float,
    val cameraX: Float,
    val cameraY: Float,
    val cameraZoom: Float,
)
