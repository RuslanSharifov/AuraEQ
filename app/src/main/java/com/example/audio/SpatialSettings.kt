package com.example.audio

/** Shared by the Media3 adapter and the standalone PCM processing engine. */
data class SpatialSettings(
    val is8dEnabled: Boolean = false,
    val mode: String = "off",
    val leftPercent: Int = 100,
    val rightPercent: Int = 100,
    val leftTone: Int = 0,
    val rightTone: Int = 0,
    val staticPan: Int = 0,
    val depth: Int = 100,
    val periodSeconds: Float = 8f
) {
    fun sanitized() = copy(
        mode = if (mode in listOf("off", "static", "dynamic")) mode else "off",
        leftPercent = leftPercent.coerceIn(0, 100),
        rightPercent = rightPercent.coerceIn(0, 100),
        leftTone = leftTone.coerceIn(-100, 100),
        rightTone = rightTone.coerceIn(-100, 100),
        staticPan = staticPan.coerceIn(-100, 100),
        depth = depth.coerceIn(0, 100),
        periodSeconds = periodSeconds.coerceIn(2f, 30f)
    )
}
