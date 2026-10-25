package com.example.audio

/**
 * Pure calculation: [baseGain] is the stored slider position, never the effective
 * hardware level. The treble offset is applied once, on output only.
 */
internal object EqBandTuning {
    fun effectiveGain(
        baseGain: Int,
        treble: Int,
        index: Int,
        bandCount: Int,
        minGain: Int,
        maxGain: Int
    ): Short {
        val trebleDelta = ((treble.coerceIn(-1000, 1000) / 1000f) * 600f).toInt()
        val trebleOffset = when {
            bandCount < 2 -> 0
            index == bandCount - 1 -> trebleDelta
            index == bandCount - 2 -> trebleDelta / 2
            else -> 0
        }
        return (baseGain + trebleOffset).coerceIn(minGain, maxGain).toShort()
    }
}
