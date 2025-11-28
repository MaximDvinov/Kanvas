package com.kanvas.fx.physics

/**
 * Unit conversion description for simulation world.
 */
data class PhysicsUnits(
    /** Number of real-world meters in one simulation distance unit. */
    val distanceInMetersPerUnit: Double = 1.0,
    /** Number of kilograms in one simulation mass unit. */
    val massInKgPerUnit: Double = 1.0,
    /** Number of real-world seconds in one simulation time unit. */
    val timeInSecondsPerUnit: Double = 1.0,
)

/**
 * Gravitational configuration in simulation units.
 */
data class GravityConfig(
    val gravitationalConstant: Double,
    val softeningDistanceUnits: Double = 0.0,
) {
    companion object {
        /**
         * Converts SI gravitational constant (m^3 / (kg * s^2)) into simulation units.
         */
        fun fromSI(
            units: PhysicsUnits,
            softeningDistanceUnits: Double = 0.0,
            gSi: Double = 6.67430e-11,
        ): GravityConfig {
            val gUnits = gSi * units.massInKgPerUnit * units.timeInSecondsPerUnit * units.timeInSecondsPerUnit /
                (units.distanceInMetersPerUnit * units.distanceInMetersPerUnit * units.distanceInMetersPerUnit)
            return GravityConfig(
                gravitationalConstant = gUnits,
                softeningDistanceUnits = softeningDistanceUnits,
            )
        }
    }
}
