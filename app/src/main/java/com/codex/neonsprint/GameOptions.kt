package com.codex.neonsprint

import android.content.SharedPreferences

enum class GraphicsQuality(
    val label: String,
    val starCount: Int,
    val roadSegments: Int,
    val maxOpponents: Int,
    val maxBoosts: Int,
    val buildingCount: Int,
    val minimapCars: Int,
) {
    LOW("Low", 36, 10, 5, 3, 6, 2),
    MEDIUM("Medium", 64, 14, 8, 4, 10, 4),
    HIGH("High", 96, 18, 10, 5, 14, 6);

    fun next(): GraphicsQuality = entries[(ordinal + 1) % entries.size]
}

data class GameOptions(
    val graphicsQuality: GraphicsQuality = GraphicsQuality.MEDIUM,
    val shaderCacheEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val masterVolume: Int = 75,
) {
    fun toggleShaderCache() = copy(shaderCacheEnabled = !shaderCacheEnabled)
    fun toggleSound() = copy(soundEnabled = !soundEnabled)
    fun nextGraphicsQuality() = copy(graphicsQuality = graphicsQuality.next())
    fun nextVolumeStep(): GameOptions {
        val next = when (masterVolume) {
            0 -> 25
            25 -> 50
            50 -> 75
            75 -> 100
            else -> 0
        }
        return copy(masterVolume = next)
    }

    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putString(KEY_GRAPHICS, graphicsQuality.name)
            .putBoolean(KEY_SHADER_CACHE, shaderCacheEnabled)
            .putBoolean(KEY_SOUND, soundEnabled)
            .putInt(KEY_MASTER_VOLUME, masterVolume)
            .apply()
    }

    companion object {
        private const val KEY_GRAPHICS = "graphics_quality"
        private const val KEY_SHADER_CACHE = "shader_cache_enabled"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_MASTER_VOLUME = "master_volume"

        fun load(prefs: SharedPreferences): GameOptions {
            val quality = prefs.getString(KEY_GRAPHICS, GraphicsQuality.MEDIUM.name)
                ?.let { name -> GraphicsQuality.entries.firstOrNull { it.name == name } }
                ?: GraphicsQuality.MEDIUM
            return GameOptions(
                graphicsQuality = quality,
                shaderCacheEnabled = prefs.getBoolean(KEY_SHADER_CACHE, true),
                soundEnabled = prefs.getBoolean(KEY_SOUND, true),
                masterVolume = prefs.getInt(KEY_MASTER_VOLUME, 75).coerceIn(0, 100),
            )
        }
    }
}
