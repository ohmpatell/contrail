package com.contrail.contrail.feature.radar.map

import kotlin.math.sqrt

/**
 * Euclidean distance transform (Felzenszwalb & Huttenlocher) used to turn a rasterised
 * silhouette into a MapLibre SDF icon, so one bitmap can be recoloured per aircraft and
 * still render crisp edges and a clean outline halo.
 */
object SignedDistanceField {
    private const val INF = 1e20f

    /**
     * Encodes [coverage] (0..1 per pixel, row-major) into SDF alpha bytes the way MapLibre
     * expects: 0.75 at the edge, +1/8 per logical pixel inside, -1/8 per logical pixel outside.
     * [pixelRatio] converts bitmap pixels to logical pixels.
     */
    fun encode(coverage: FloatArray, width: Int, height: Int, pixelRatio: Float): ByteArray {
        val outside = FloatArray(width * height) { if (coverage[it] >= 0.5f) 0f else INF }
        val inside = FloatArray(width * height) { if (coverage[it] >= 0.5f) INF else 0f }
        transform2d(outside, width, height)
        transform2d(inside, width, height)
        val out = ByteArray(width * height)
        for (i in out.indices) {
            // The edge lies half a pixel from the nearest pixel centre on the other side;
            // anti-aliased edge pixels use their coverage for sub-pixel accuracy.
            val c = coverage[i]
            val px = when {
                c > 0.02f && c < 0.98f -> c - 0.5f
                c >= 0.5f -> sqrt(inside[i]) - 0.5f
                else -> 0.5f - sqrt(outside[i])
            }
            val d = px / pixelRatio
            val v = (0.75f + d / 8f).coerceIn(0f, 1f)
            out[i] = (v * 255f + 0.5f).toInt().toByte()
        }
        return out
    }

    /** In-place squared distance transform of a 2D grid where 0 marks feature pixels. */
    fun transform2d(grid: FloatArray, width: Int, height: Int) {
        val n = maxOf(width, height)
        val f = FloatArray(n)
        val d = FloatArray(n)
        val v = IntArray(n)
        val z = FloatArray(n + 1)
        for (x in 0 until width) {
            for (y in 0 until height) f[y] = grid[y * width + x]
            transform1d(f, height, d, v, z)
            for (y in 0 until height) grid[y * width + x] = d[y]
        }
        for (y in 0 until height) {
            for (x in 0 until width) f[x] = grid[y * width + x]
            transform1d(f, width, d, v, z)
            for (x in 0 until width) grid[y * width + x] = d[x]
        }
    }

    private fun transform1d(f: FloatArray, n: Int, d: FloatArray, v: IntArray, z: FloatArray) {
        var k = 0
        v[0] = 0
        z[0] = Float.NEGATIVE_INFINITY
        z[1] = Float.POSITIVE_INFINITY
        for (q in 1 until n) {
            var r = v[k]
            var s = ((f[q] + q * q) - (f[r] + r * r)) / (2f * (q - r))
            while (s <= z[k]) {
                k--
                r = v[k]
                s = ((f[q] + q * q) - (f[r] + r * r)) / (2f * (q - r))
            }
            k++
            v[k] = q
            z[k] = s
            z[k + 1] = Float.POSITIVE_INFINITY
        }
        k = 0
        for (q in 0 until n) {
            while (z[k + 1] < q) k++
            val r = v[k]
            d[q] = (q - r).toFloat() * (q - r) + f[r]
        }
    }
}
