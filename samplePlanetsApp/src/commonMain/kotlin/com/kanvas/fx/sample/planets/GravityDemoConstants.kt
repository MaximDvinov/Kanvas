package com.kanvas.fx.sample.planets

const val VELOCITY_SCALE = 0.1f
const val TRAIL_MAX_POINTS = 120
const val SELECTED_TRAIL_RENDER_MAX_POINTS = 1400
const val FAST_TRAIL_SEGMENTS_LIMIT = 260
const val MAX_FULL_LIGHTING_BODIES = 220
const val PREVIEW_ORBIT_POINTS = 180
const val PREVIEW_ORBIT_DT_SECONDS = 1f / 60f
const val SOLVER_GRAVITY_CONSTANT = 3_800f
const val PREVIEW_GRAVITY_CONSTANT = SOLVER_GRAVITY_CONSTANT
const val PREVIEW_SOFTENING = 28f
const val MAX_PLANETS_WITH_TRAILS = 300
const val ASTEROID_COUNT = 50000
const val MIN_SANDBOX_MASS = 0.05f
const val MAX_SANDBOX_MASS = 10_000_000f
const val MIN_STAR_MASS = 15_000f
const val MIN_BLACK_HOLE_MASS = 500_000f
const val CAMERA_DEFAULT_ZOOM = 0.35f
const val CAMERA_MIN_ZOOM = 0.02f
const val CAMERA_MAX_ZOOM = 6f
const val COLLISION_SCORE_PER_MERGE = 10
const val MAX_MERGES_PER_TICK = 24
const val COLLISION_GRID_CELL_SIZE = 220f
const val IMPULSE_DRAG_SCALE = 0.08f
const val STAR_SPAWN_VELOCITY_FACTOR = 0.2f
const val BLACK_HOLE_SPAWN_VELOCITY_FACTOR = 0.15f
const val BINARY_PAIR_SEPARATION = 90f
const val BINARY_PAIR_TANGENTIAL_SPEED = 110f
const val BINARY_PAIR_MASS_FACTOR = 0.9f
const val OBJECT_PICK_BASE_RADIUS = 48f
const val OBJECT_PICK_RADIUS_FACTOR = 2.5f
const val PLANET_DESPAWN_RADIUS = 100_000f

val KENNEY_PLANET_TEXTURE_IDS = List(10) { index -> "planet-$index" }
val PUCCI_ROCKY_SMALL_TEXTURE_IDS = listOf(
    "pucci-mercury",
    "pucci-moon",
    "pucci-moon2",
    "pucci-moon3",
)
val PUCCI_TERRESTRIAL_TEXTURE_IDS = listOf(
    "pucci-mars",
    "pucci-venus",
    "pucci-keplerf",
    "pucci-planet",
)
val PUCCI_ICE_WORLD_TEXTURE_IDS = listOf(
    "pucci-uranus",
    "pucci-bluemoon",
    "pucci-gliese876c",
    "pucci-gliese876e",
)
val PUCCI_GAS_GIANT_TEXTURE_IDS = listOf(
    "pucci-jupiter",
    "pucci-brahe",
    "pucci-lippershey",
    "pucci-quijote",
    "pucci-harriot",
    "pucci-galileo",
    "pucci-sancho",
)
val PLANET_TEXTURE_IDS = KENNEY_PLANET_TEXTURE_IDS +
    PUCCI_ROCKY_SMALL_TEXTURE_IDS +
    PUCCI_TERRESTRIAL_TEXTURE_IDS +
    PUCCI_ICE_WORLD_TEXTURE_IDS +
    PUCCI_GAS_GIANT_TEXTURE_IDS

val ROCKY_SMALL_TEXTURE_IDS = listOf("planet-0", "planet-1", "planet-2") + PUCCI_ROCKY_SMALL_TEXTURE_IDS
val TERRESTRIAL_TEXTURE_IDS = listOf("planet-3", "planet-4", "planet-5") + PUCCI_TERRESTRIAL_TEXTURE_IDS
val ICE_WORLD_TEXTURE_IDS = listOf("planet-6", "planet-7") + PUCCI_ICE_WORLD_TEXTURE_IDS
val GAS_GIANT_TEXTURE_IDS = listOf("planet-8", "planet-9") + PUCCI_GAS_GIANT_TEXTURE_IDS
val ASTEROID_TEXTURE_IDS = List(5) { index -> "asteroid-$index" }
val STAR_TEXTURE_IDS = listOf("star-shiny")

val TEXTURE_VISUAL_SCALE_OVERRIDES = mapOf(
    // Decorative rings should not affect physics/collider size.
    // Tuned so the visual core of Saturn matches the physical collider closer.
    "pucci-uranus" to 1.74f,
)

data class SelectionStyle(
    val selectedScale: Float,
    val hoverScale: Float,
    val selectedStrokeWidth: Float,
    val hoverStrokeWidth: Float,
    val outerSelectedScale: Float,
    val outerSelectedStrokeWidth: Float,
)

val DEFAULT_SELECTION_STYLE = SelectionStyle(
    selectedScale = 1.14f,
    hoverScale = 1.08f,
    selectedStrokeWidth = 2.2f,
    hoverStrokeWidth = 1.4f,
    outerSelectedScale = 1.045f,
    outerSelectedStrokeWidth = 1.2f,
)

val PLANET_VISUAL_PROFILES = listOf(
    BodyVisualProfile(
        visualClass = BodyVisualClass.RockySmall,
        minMass = 8f,
        maxMass = 40f,
        minRadius = 9f,
        maxRadius = 16f,
        textureIds = ROCKY_SMALL_TEXTURE_IDS,
    ),
    BodyVisualProfile(
        visualClass = BodyVisualClass.Terrestrial,
        minMass = 40f,
        maxMass = 180f,
        minRadius = 16f,
        maxRadius = 28f,
        textureIds = TERRESTRIAL_TEXTURE_IDS,
    ),
    BodyVisualProfile(
        visualClass = BodyVisualClass.IceWorld,
        minMass = 180f,
        maxMass = 800f,
        minRadius = 28f,
        maxRadius = 42f,
        textureIds = ICE_WORLD_TEXTURE_IDS,
    ),
    BodyVisualProfile(
        visualClass = BodyVisualClass.GasGiant,
        minMass = 800f,
        maxMass = MIN_STAR_MASS,
        minRadius = 42f,
        maxRadius = 76f,
        textureIds = GAS_GIANT_TEXTURE_IDS,
    ),
)

val ASTEROID_VISUAL_PROFILE = BodyVisualProfile(
    visualClass = BodyVisualClass.Asteroid,
    minMass = 0.05f,
    maxMass = 8f,
    minRadius = 3f,
    maxRadius = 9f,
    textureIds = ASTEROID_TEXTURE_IDS,
)

val STAR_VISUAL_PROFILE = BodyVisualProfile(
    visualClass = BodyVisualClass.Star,
    minMass = MIN_STAR_MASS,
    maxMass = MAX_SANDBOX_MASS,
    minRadius = 120f,
    maxRadius = 320f,
    textureIds = STAR_TEXTURE_IDS,
)
