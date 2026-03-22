package com.codex.neonsprint

import android.graphics.LinearGradient
import android.graphics.Shader

class ShaderCache {
    private data class GradientKey(
        val id: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val colors: List<Int>,
        val positions: List<Float>?,
    )

    private val cache = LinkedHashMap<GradientKey, LinearGradient>()

    fun linearGradient(
        enabled: Boolean,
        id: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        colors: IntArray,
        positions: FloatArray? = null,
    ): LinearGradient {
        if (!enabled) {
            return LinearGradient(left, top, right, bottom, colors, positions, Shader.TileMode.CLAMP)
        }
        val key = GradientKey(
            id = id,
            left = left.toInt(),
            top = top.toInt(),
            right = right.toInt(),
            bottom = bottom.toInt(),
            colors = colors.toList(),
            positions = positions?.toList(),
        )
        return cache.getOrPut(key) {
            LinearGradient(left, top, right, bottom, colors, positions, Shader.TileMode.CLAMP)
        }
    }

    fun clear() {
        cache.clear()
    }
}
