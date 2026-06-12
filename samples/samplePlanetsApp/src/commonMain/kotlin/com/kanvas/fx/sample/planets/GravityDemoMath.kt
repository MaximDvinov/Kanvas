package com.kanvas.fx.sample.planets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.kanvas.fx.render.AssetRegistry
import com.kanvas.fx.render.TextureSource
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

operator fun Offset.minus(other: Offset): Offset = Offset(x - other.x, y - other.y)

operator fun Offset.times(value: Float): Offset = Offset(x * value, y * value)

operator fun Offset.plus(other: Offset): Offset = Offset(x + other.x, y + other.y)

operator fun Offset.div(value: Float): Offset = Offset(x / value, y / value)

fun predictOrbitPoints(
    start: Offset,
    initialVelocity: Offset,
    planets: List<PlanetState>,
): List<Offset> {
    if (planets.isEmpty()) return listOf(start)
    var position = start
    var velocity = initialVelocity
    val points = ArrayList<Offset>(PREVIEW_ORBIT_POINTS + 1)
    points += position

    repeat(PREVIEW_ORBIT_POINTS) {
        var ax = 0f
        var ay = 0f
        planets.forEach { planet ->
            val dx = planet.body.position.x - position.x
            val dy = planet.body.position.y - position.y
            val distSq = (dx * dx + dy * dy).coerceAtLeast(PREVIEW_SOFTENING * PREVIEW_SOFTENING)
            val dist = sqrt(distSq)
            val accel = PREVIEW_GRAVITY_CONSTANT * planet.body.mass / distSq
            ax += accel * dx / dist
            ay += accel * dy / dist
        }
        velocity = Offset(
            x = velocity.x + ax * PREVIEW_ORBIT_DT_SECONDS,
            y = velocity.y + ay * PREVIEW_ORBIT_DT_SECONDS,
        )
        position = Offset(
            x = position.x + velocity.x * PREVIEW_ORBIT_DT_SECONDS,
            y = position.y + velocity.y * PREVIEW_ORBIT_DT_SECONDS,
        )
        points += position
    }
    return points
}

fun blendByMass(
    colorA: Color,
    massA: Float,
    colorB: Color,
    massB: Float,
): Color {
    val total = (massA + massB).coerceAtLeast(0.0001f)
    val wa = massA / total
    val wb = massB / total
    return Color(
        red = colorA.red * wa + colorB.red * wb,
        green = colorA.green * wa + colorB.green * wb,
        blue = colorA.blue * wa + colorB.blue * wb,
        alpha = 1f,
    )
}

fun adaptiveTrailSampleStep(bodiesCount: Int): Int = when {
    bodiesCount <= 64 -> 1
    bodiesCount <= 250 -> 2
    bodiesCount <= 700 -> 4
    else -> 8
}

fun sampledTrailForRender(
    points: List<Offset>,
    maxRenderPoints: Int = SELECTED_TRAIL_RENDER_MAX_POINTS,
): List<Offset> {
    if (points.size <= maxRenderPoints || maxRenderPoints < 2) return points
    val step = (points.size.toFloat() / maxRenderPoints.toFloat()).toInt().coerceAtLeast(1)
    val sampled = ArrayList<Offset>(maxRenderPoints + 1)
    var index = 0
    while (index < points.size) {
        sampled += points[index]
        index += step
    }
    val last = points.last()
    if (sampled.lastOrNull() != last) sampled += last
    return sampled
}

fun massToSlider(mass: Float): Float {
    val minLog = log10(MIN_SANDBOX_MASS)
    val maxLog = log10(MAX_SANDBOX_MASS)
    val valueLog = log10(mass.coerceIn(MIN_SANDBOX_MASS, MAX_SANDBOX_MASS))
    return ((valueLog - minLog) / (maxLog - minLog)).coerceIn(0f, 1f)
}

fun sliderToMass(value: Float): Float {
    val minLog = log10(MIN_SANDBOX_MASS)
    val maxLog = log10(MAX_SANDBOX_MASS)
    return 10f.pow(minLog + value.coerceIn(0f, 1f) * (maxLog - minLog))
}

fun displayMass(mass: Float): String = when {
    mass < 10f -> formatOneDecimal(mass)
    mass < 1_000f -> mass.toInt().toString()
    mass < 100_000f -> "${formatOneDecimal(mass / 1_000f)}k"
    else -> "${(mass / 1_000f).toInt()}k"
}

fun formatOneDecimal(value: Float): String {
    val rounded = kotlin.math.round(value * 10f) / 10f
    val whole = rounded.toInt()
    val decimal = kotlin.math.abs(((rounded - whole) * 10f).toInt())
    return "$whole.$decimal"
}

fun visualClassLabel(kind: BodyKind, mass: Float): String = when (visualClassForBody(kind, mass)) {
    BodyVisualClass.Asteroid -> "Asteroid"
    BodyVisualClass.RockySmall -> "Rocky"
    BodyVisualClass.Terrestrial -> "Terrestrial"
    BodyVisualClass.IceWorld -> "Ice"
    BodyVisualClass.GasGiant -> "Gas Giant"
    BodyVisualClass.Star -> "Star"
    BodyVisualClass.BlackHole -> "Black Hole"
}

fun visualClassImage(
    kind: BodyKind,
    mass: Float,
    assets: AssetRegistry,
): ImageBitmap? {
    val profile = visualProfileForBody(kind, mass)
    val textureId = profile.textureIds.firstOrNull() ?: return null
    val source = assets.texture(textureId)?.source ?: return null
    return (source as? TextureSource.Bitmap)?.value
}

fun visualClassForBody(kind: BodyKind, mass: Float): BodyVisualClass = when (kind) {
    BodyKind.BlackHole -> BodyVisualClass.BlackHole
    else -> visualProfileForBody(kind, mass).visualClass
}

fun kindForBody(
    mass: Float,
    eventHorizonRadius: Float = 0f,
): BodyKind = when {
    mass >= MIN_BLACK_HOLE_MASS -> BodyKind.BlackHole
    mass < ASTEROID_VISUAL_PROFILE.maxMass -> BodyKind.Asteroid
    mass >= MIN_STAR_MASS -> BodyKind.Star
    else -> BodyKind.Planet
}

fun visualRadiusForBody(
    kind: BodyKind,
    mass: Float,
    eventHorizonRadius: Float = 0f,
): Float = when (kind) {
    BodyKind.BlackHole -> (sqrt(mass.coerceAtLeast(1f)) * 0.25f).coerceIn(70f, 260f)
    else -> visualRadiusForProfile(visualProfileForBody(kind, mass), mass)
}

fun visualProfileForBody(kind: BodyKind, mass: Float): BodyVisualProfile = when (kind) {
    BodyKind.Asteroid -> ASTEROID_VISUAL_PROFILE
    BodyKind.Star -> STAR_VISUAL_PROFILE
    BodyKind.BlackHole -> BodyVisualProfile(
        visualClass = BodyVisualClass.BlackHole,
        minMass = mass,
        maxMass = mass,
        minRadius = 70f,
        maxRadius = 260f,
        textureIds = listOf("planet-9"),
    )

    BodyKind.Planet -> when {
        mass < ASTEROID_VISUAL_PROFILE.maxMass -> ASTEROID_VISUAL_PROFILE
        mass >= MIN_STAR_MASS -> STAR_VISUAL_PROFILE
        else -> PLANET_VISUAL_PROFILES.firstOrNull { mass < it.maxMass }
            ?: PLANET_VISUAL_PROFILES.last()
    }
}

fun visualRadiusForProfile(profile: BodyVisualProfile, mass: Float): Float {
    val t = ((mass - profile.minMass) / (profile.maxMass - profile.minMass)).coerceIn(0f, 1f)
    return profile.minRadius + sqrt(t) * (profile.maxRadius - profile.minRadius)
}

fun textureVisualScale(textureId: String): Float = TEXTURE_VISUAL_SCALE_OVERRIDES[textureId] ?: 1f

fun eventHorizonRadiusForMass(mass: Float): Float =
    (visualRadiusForBody(BodyKind.BlackHole, mass) * 0.42f).coerceIn(28f, 110f)

data class LightingState(
    val intensity: Float,
    val color: Color,
)

fun lightingForBody(body: PlanetState, bodies: List<PlanetState>): LightingState {
    if (body.kind == BodyKind.Star) return LightingState(1f, body.color)
    var intensity = 0f
    var red = 0f
    var green = 0f
    var blue = 0f
    bodies.forEach { star ->
        if (star.kind != BodyKind.Star) return@forEach
        val dx = body.body.position.x - star.body.position.x
        val dy = body.body.position.y - star.body.position.y
        val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val reach = 900f + sqrt(star.body.mass) * 6f
        val contribution = (1f - distance / reach).coerceIn(0f, 1f)
        val weighted = contribution * contribution
        intensity += weighted
        red += star.color.red * weighted
        green += star.color.green * weighted
        blue += star.color.blue * weighted
    }
    val safeIntensity = intensity.coerceIn(0f, 1f)
    val color = if (intensity > 0.001f) {
        Color(
            red = (red / intensity).coerceIn(0f, 1f),
            green = (green / intensity).coerceIn(0f, 1f),
            blue = (blue / intensity).coerceIn(0f, 1f),
            alpha = 1f,
        )
    } else {
        Color.White
    }
    return LightingState(safeIntensity, color)
}
