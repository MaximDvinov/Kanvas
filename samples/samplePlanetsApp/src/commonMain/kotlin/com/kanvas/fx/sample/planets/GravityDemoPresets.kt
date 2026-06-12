package com.kanvas.fx.sample.planets

import androidx.compose.ui.geometry.Offset
import kotlin.random.Random

fun ScenePreset.bodies(): List<BodySeed> = when (this) {
    ScenePreset.Empty -> listOf()

    ScenePreset.SolarSystem -> listOf(
        BodySeed("sun", "sun-body", Offset.Zero, Offset.Zero, 30_000f, "star-shiny"),
        BodySeed("mercury", "mercury-body", Offset(0f, 760f), Offset(387f, 0f), 10f, textureId = "pucci-mercury"),
        BodySeed("venus", "venus-body", Offset(0f, 1_080f), Offset(325f, 0f), 18f, textureId = "pucci-venus"),
        BodySeed("earth", "earth-body", Offset(0f, 1_460f), Offset(279f, 0f), 28f, textureId = "pucci-planet"),
        BodySeed("mars", "mars-body", Offset(0f, 1_940f), Offset(242f, 0f), 15f, textureId = "pucci-mars"),
        BodySeed("jupiter", "jupiter-body", Offset(0f, 3_150f), Offset(190f, 0f), 680f, textureId = "pucci-jupiter"),
        BodySeed("uranus", "uranus-body", Offset(0f, 4_450f), Offset(160f, 0f), 240f, textureId = "pucci-uranus"),
    )

    ScenePreset.AlphaCentauri -> listOf(
        BodySeed("alpha-a", "alpha-a-body", Offset(-330f, 0f), Offset(0f, -162f), 14_500f, "star-alpha"),
        BodySeed("alpha-b", "alpha-b-body", Offset(330f, 0f), Offset(0f, 162f), 12_700f, "star-shiny"),
        BodySeed("planet-1", "alpha-planet-1", Offset(0f, 1_850f), Offset(330f, 0f), 15f),
        BodySeed("planet-2", "alpha-planet-2", Offset(0f, -2_350f), Offset(-292f, 0f), 11f),
    )

    ScenePreset.AsteroidField -> buildList(ASTEROID_COUNT) {
        val random = Random.Default
        // Protoplanetary disk around a central proto-star.
        val primaryStarMass = 34_000f + random.nextFloat() * 12_000f
        add(
            BodySeed(
                planetId = "proto-star",
                bodyId = "proto-star-body",
                position = Offset.Zero,
                velocity = Offset.Zero,
                mass = primaryStarMass,
                textureId = "star-shiny",
            ),
        )
        if (random.nextFloat() < 0.42f) {
            val distance = 1_200f + random.nextFloat() * 3_400f
            val angle = random.nextFloat() * 6.28318f
            val offset = Offset(kotlin.math.cos(angle) * distance, kotlin.math.sin(angle) * distance)
            val tangent = Offset(-offset.y, offset.x)
            val len = kotlin.math.sqrt(tangent.x * tangent.x + tangent.y * tangent.y).coerceAtLeast(0.001f)
            val secondaryMass = 1_500f + random.nextFloat() * 5_500f
            val orbitalSpeed = kotlin.math.sqrt((SOLVER_GRAVITY_CONSTANT * primaryStarMass) / distance)
            val velocity = Offset(
                x = tangent.x / len * orbitalSpeed * (0.65f + random.nextFloat() * 0.35f),
                y = tangent.y / len * orbitalSpeed * (0.65f + random.nextFloat() * 0.35f),
            )
            add(
                BodySeed(
                    planetId = "proto-companion",
                    bodyId = "proto-companion-body",
                    position = offset,
                    velocity = velocity,
                    mass = secondaryMass,
                    textureId = "star-alpha",
                ),
            )
        }

        val innerRadius = 600f + random.nextFloat() * 500f
        val outerRadius = 6_000f + random.nextFloat() * 3500f
        val ringThickness = 550f + random.nextFloat() * 850f
        val gm = SOLVER_GRAVITY_CONSTANT * primaryStarMass
        val globalEccentricity = random.nextFloat() * 0.2f
        val lopsidedness = random.nextFloat() * 0.28f
        val densityBands = listOf(
            0.10f to 0.22f,
            0.30f to 0.40f,
            0.52f to 0.64f,
            0.74f to 0.84f,
        )
        val bandBoost = 0.16f + random.nextFloat() * 0.2f
        val baseMassScale = 0.28f + random.nextFloat() * 0.15f
        repeat(ASTEROID_COUNT) { index ->
            val angle = random.nextFloat() * 6.28318f
            var radialT = random.nextFloat() * random.nextFloat()
            // Multi-density disk: several radial bands have increased spawn probability.
            if (random.nextFloat() < 0.6f) {
                val (a, b) = densityBands[random.nextInt(densityBands.size)]
                radialT = a + random.nextFloat() * (b - a)
            } else {
                radialT = (radialT + bandBoost * kotlin.math.sin(radialT * 18f)).coerceIn(0f, 1f)
            }
            val baseRadius = innerRadius + radialT * (outerRadius - innerRadius)
            val localOffsetAngle = random.nextFloat() * 6.28318f
            val localThicknessScale = (0.35f + 0.65f * radialT)
            val localOffsetRadius = (random.nextFloat() - 0.5f) * ringThickness * localThicknessScale
            val eccentricScale = 1f - globalEccentricity * kotlin.math.cos(angle)
            val lopsideBoost = 1f + lopsidedness * kotlin.math.sin(angle * 2f + random.nextFloat())
            val warpedRadius = baseRadius * eccentricScale * lopsideBoost
            val pos = Offset(
                x = kotlin.math.cos(angle) * warpedRadius + kotlin.math.cos(localOffsetAngle) * localOffsetRadius,
                y = kotlin.math.sin(angle) * warpedRadius + kotlin.math.sin(localOffsetAngle) * localOffsetRadius,
            )
            val r = kotlin.math.sqrt(pos.x * pos.x + pos.y * pos.y).coerceAtLeast(1f)
            val orbitSpeed = kotlin.math.sqrt(gm / r)
            val tangent = Offset(-pos.y / r, pos.x / r)
            val inward = Offset(-pos.x / r, -pos.y / r)
            val turbulence = 0.05f + 0.08f * radialT
            val radialDrift = 0.25f + 2.4f * radialT
            add(
                BodySeed(
                    planetId = "ast-$index",
                    bodyId = "ast-body-$index",
                    position = pos,
                    velocity = Offset(
                        x = tangent.x * orbitSpeed * (1f + (random.nextFloat() - 0.5f) * turbulence) +
                            inward.x * radialDrift,
                        y = tangent.y * orbitSpeed * (1f + (random.nextFloat() - 0.5f) * turbulence) +
                            inward.y * radialDrift,
                    ),
                    mass = (0.03f + random.nextFloat() * (0.8f + 1.6f * (1f - radialT))) * baseMassScale,
                ),
            )
        }
    }
}

fun ScenePreset.bodies(asteroidCount: Int): List<BodySeed> {
    if (this != ScenePreset.AsteroidField) return bodies()
    val count = asteroidCount.coerceAtLeast(16)
    val baseCount = ASTEROID_COUNT.toFloat().coerceAtLeast(1f)
    val densityScale = kotlin.math.sqrt(count.toFloat() / baseCount)
    val random = Random.Default

    return buildList(count + 2) {
        val primaryStarMass = 34_000f + random.nextFloat() * 12_000f
        add(
            BodySeed(
                planetId = "proto-star",
                bodyId = "proto-star-body",
                position = Offset.Zero,
                velocity = Offset.Zero,
                mass = primaryStarMass,
                textureId = "star-shiny",
            ),
        )
        if (random.nextFloat() < 0.42f) {
            val distance = (1_200f + random.nextFloat() * 3_400f) * densityScale
            val angle = random.nextFloat() * 6.28318f
            val offset = Offset(kotlin.math.cos(angle) * distance, kotlin.math.sin(angle) * distance)
            val tangent = Offset(-offset.y, offset.x)
            val len = kotlin.math.sqrt(tangent.x * tangent.x + tangent.y * tangent.y).coerceAtLeast(0.001f)
            val secondaryMass = 1_500f + random.nextFloat() * 5_500f
            val orbitalSpeed = kotlin.math.sqrt((SOLVER_GRAVITY_CONSTANT * primaryStarMass) / distance)
            val velocity = Offset(
                x = tangent.x / len * orbitalSpeed * (0.65f + random.nextFloat() * 0.35f),
                y = tangent.y / len * orbitalSpeed * (0.65f + random.nextFloat() * 0.35f),
            )
            add(
                BodySeed(
                    planetId = "proto-companion",
                    bodyId = "proto-companion-body",
                    position = offset,
                    velocity = velocity,
                    mass = secondaryMass,
                    textureId = "star-alpha",
                ),
            )
        }

        val innerRadius = (600f + random.nextFloat() * 500f) * densityScale
        val outerRadius = (6_000f + random.nextFloat() * 3_500f) * densityScale
        val ringThickness = (550f + random.nextFloat() * 850f) * densityScale
        val gm = SOLVER_GRAVITY_CONSTANT * primaryStarMass
        val globalEccentricity = random.nextFloat() * 0.2f
        val lopsidedness = random.nextFloat() * 0.28f
        val densityBands = listOf(
            0.10f to 0.22f,
            0.30f to 0.40f,
            0.52f to 0.64f,
            0.74f to 0.84f,
        )
        val bandBoost = 0.16f + random.nextFloat() * 0.2f
        val baseMassScale = 0.28f + random.nextFloat() * 0.15f
        repeat(count) { index ->
            val angle = random.nextFloat() * 6.28318f
            var radialT = random.nextFloat() * random.nextFloat()
            if (random.nextFloat() < 0.6f) {
                val (a, b) = densityBands[random.nextInt(densityBands.size)]
                radialT = a + random.nextFloat() * (b - a)
            } else {
                radialT = (radialT + bandBoost * kotlin.math.sin(radialT * 18f)).coerceIn(0f, 1f)
            }
            val baseRadius = innerRadius + radialT * (outerRadius - innerRadius)
            val localOffsetAngle = random.nextFloat() * 6.28318f
            val localThicknessScale = (0.35f + 0.65f * radialT)
            val localOffsetRadius = (random.nextFloat() - 0.5f) * ringThickness * localThicknessScale
            val eccentricScale = 1f - globalEccentricity * kotlin.math.cos(angle)
            val lopsideBoost = 1f + lopsidedness * kotlin.math.sin(angle * 2f + random.nextFloat())
            val warpedRadius = baseRadius * eccentricScale * lopsideBoost
            val pos = Offset(
                x = kotlin.math.cos(angle) * warpedRadius + kotlin.math.cos(localOffsetAngle) * localOffsetRadius,
                y = kotlin.math.sin(angle) * warpedRadius + kotlin.math.sin(localOffsetAngle) * localOffsetRadius,
            )
            val r = kotlin.math.sqrt(pos.x * pos.x + pos.y * pos.y).coerceAtLeast(1f)
            val orbitSpeed = kotlin.math.sqrt(gm / r)
            val tangent = Offset(-pos.y / r, pos.x / r)
            val inward = Offset(-pos.x / r, -pos.y / r)
            val turbulence = 0.05f + 0.08f * radialT
            val radialDrift = 0.25f + 2.4f * radialT
            add(
                BodySeed(
                    planetId = "ast-$index",
                    bodyId = "ast-body-$index",
                    position = pos,
                    velocity = Offset(
                        x = tangent.x * orbitSpeed * (1f + (random.nextFloat() - 0.5f) * turbulence) + inward.x * radialDrift,
                        y = tangent.y * orbitSpeed * (1f + (random.nextFloat() - 0.5f) * turbulence) + inward.y * radialDrift,
                    ),
                    mass = (0.03f + random.nextFloat() * (0.8f + 1.6f * (1f - radialT))) * baseMassScale,
                ),
            )
        }
    }
}
