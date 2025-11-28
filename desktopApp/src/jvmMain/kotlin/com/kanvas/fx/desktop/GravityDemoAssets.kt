package com.kanvas.fx.desktop

import androidx.compose.ui.graphics.Color
import com.kanvas.fx.render.AssetRegistry
import com.kanvas.fx.render.ShaderAsset
import com.kanvas.fx.render.ShaderSource
import com.kanvas.fx.render.enableDesktopRuntimeShaders

fun buildPlanetAssets(): AssetRegistry = AssetRegistry().apply {
    enableDesktopTextureAutoResolve()
    enableDesktopRuntimeShaders()
    registerShader(
        ShaderAsset(
            id = "planet-atmosphere",
            source = ShaderSource.SkiaRuntime(
                code = """
                    uniform float2 iResolution;

                    half4 main(float2 p) {
                        float2 uv = (p / max(iResolution, float2(1.0))) * 2.0 - 1.0;
                        float d = length(uv);
                        float ring = smoothstep(1.08, 0.38, d) * (1.0 - smoothstep(0.95, 0.0, d));
                        float haze = exp(-d * 3.3) * 0.55;
                        float alpha = clamp(ring + haze * 0.35, 0.0, 0.9);
                        float3 c = mix(float3(0.22, 0.48, 0.95), float3(0.58, 0.89, 1.0), clamp(1.0 - d, 0.0, 1.0));
                        return half4(c, alpha);
                    }
                """.trimIndent(),
                uniformOrder = listOf("iResolution"),
            ),
        ),
    )
    registerShader(
        ShaderAsset(
            id = "star-corona",
            source = ShaderSource.SkiaRuntime(
                code = """
                    uniform float2 iResolution;
                    uniform float4 uColor;

                    half4 main(float2 p) {
                        float2 uv = (p - iResolution * 0.5) / max(iResolution.x, iResolution.y);
                        float d = length(uv) * 2.0;
                        float core = smoothstep(0.55, 0.0, d);
                        float corona = exp(-d * 2.9);
                        float rays = pow(max(0.0, 1.0 - d), 2.2) * (0.72 + 0.28 * sin(atan(uv.y, uv.x) * 11.0));
                        float alpha = max(core * 0.58, corona * 0.28 + rays * 0.18);
                        float3 color = uColor.rgb * (0.65 + core * 0.7 + rays * 0.35);
                        return half4(color, alpha * uColor.a);
                    }
                """.trimIndent(),
                uniformOrder = listOf("iResolution", "uColor"),
            ),
        ),
    )
    registerShader(
        ShaderAsset(
            id = "black-hole-accretion",
            source = ShaderSource.SkiaRuntime(
                code = """
                    uniform float2 iResolution;
                    uniform float uTime;

                    half4 main(float2 p) {
                        float2 uv = (p / max(iResolution, float2(1.0))) * 2.0 - 1.0;
                        float d = max(length(uv), 0.0001);
                        float angle = atan(uv.y, uv.x);
                        float swirl = 0.5 + 0.5 * sin(angle * 8.0 - uTime * 2.0 - d * 20.0);
                        float disk = smoothstep(1.2, 0.42, d) * (1.0 - smoothstep(0.62, 0.18, d));
                        float glow = exp(-d * 2.4) * 0.55;
                        float alpha = clamp(disk * (0.45 + swirl * 0.5) + glow * 0.25, 0.0, 0.85);
                        float3 color = mix(float3(0.24, 0.15, 0.55), float3(0.72, 0.46, 1.0), swirl);
                        return half4(color, alpha);
                    }
                """.trimIndent(),
                uniformOrder = listOf("iResolution", "uTime"),
            ),
        ),
    )

    for (i in 0..9) {
        registerTexture("planet-$i", "/textures/planets/planet${i.toString().padStart(2, '0')}.png")
    }
    mapOf(
        "pucci-jupiter" to "Jupiter_Sprite.png",
        "pucci-mars" to "Mars_Sprite.png",
        "pucci-venus" to "Venus_Sprite.png",
        "pucci-uranus" to "Uranus_Sprite.png",
        "pucci-bluemoon" to "Bluemoon_Sprite.png",
        "pucci-moon" to "Moon_Sprite.png",
        "pucci-mercury" to "Mercury_Sprite.png",
        "pucci-moon2" to "Moon2_Sprite.png",
        "pucci-brahe" to "Brahe_Sprite.png",
        "pucci-lippershey" to "Lippershey_Sprite.png",
        "pucci-gliese876e" to "Gliese876e_Sprite.png",
        "pucci-quijote" to "Quijote_Sprite.png",
        "pucci-keplerf" to "Keplerf_Sprite.png",
        "pucci-harriot" to "Harriot_Sprite.png",
        "pucci-galileo" to "Galileo_Sprite.png",
        "pucci-planet" to "Planet_Sprite.png",
        "pucci-sancho" to "Sancho_Sprite.png",
        "pucci-gliese876c" to "Gliese876c_Sprite.png",
        "pucci-moon3" to "Moon3_Sprite.png",
    ).forEach { (id, fileName) ->
        registerTexture(id, "/textures/planets/pucci/$fileName")
    }
    listOf("noise24", "noise25", "noise26", "noise27", "sphere2").forEachIndexed { index, fileName ->
        registerTexture("asteroid-$index", "/textures/asteroids/$fileName.png")
    }
    registerTexture("star-alpha", "/textures/stars/star.png")
    registerTexture("star-shiny", "/textures/stars/sun_shiny.png")
}
