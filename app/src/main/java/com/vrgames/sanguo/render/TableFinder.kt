package com.vrgames.sanguo.render

import android.util.Log
import com.pico.spatial.sense.base.SemanticLabelType
import com.pico.spatial.sense.plane.PlaneOrientation
import com.pico.spatial.sense.plane.PlaneTrackingManager
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.sin

/**
 * Looks for a real table in front of the player with the headset's plane detection, so the
 * miniature stands on it. Any failure (no support, no permission, nothing found in time) gives
 * null, and the stage keeps its own height and distance.
 */
object TableFinder {
    private const val TAG = "BookTable"

    data class Table(val height: Float, val distance: Float)

    /** [x], [z]: the player's position; [yaw]: the way they face (degrees, 0 = -z). */
    suspend fun find(x: Float, z: Float, yaw: Float): Table? = runCatching {
        PlaneTrackingManager.start()
        val planes = withTimeoutOrNull(4000) { PlaneTrackingManager.loadAllAnchors() } ?: return@runCatching null
        val a = Math.toRadians(yaw.toDouble())
        val fx = -sin(a).toFloat()
        val fz = -cos(a).toFloat()
        var best: Table? = null
        var bestScore = Float.MAX_VALUE
        for (p in planes) {
            val tableLike = p.semantics == SemanticLabelType.TABLE ||
                (p.planeOrientation == PlaneOrientation.HORIZONTAL_UPWARD && p.semantics != SemanticLabelType.FLOOR)
            if (!tableLike) continue
            val pos = p.transform.position
            if (pos.y !in 0.45f..1.2f) continue
            val dx = pos.x - x
            val dz = pos.z - z
            val ahead = dx * fx + dz * fz
            val side = kotlin.math.abs(dx * fz - dz * fx)
            if (ahead < 0.2f || ahead > 1.6f || side > 0.8f) continue
            val score = ahead + side * 2 + if (p.semantics == SemanticLabelType.TABLE) 0f else 0.5f
            Log.i(TAG, "plane ${p.semantics} ${p.planeOrientation} y=${pos.y} ahead=$ahead side=$side size=${p.boundingBoxSize}")
            if (score < bestScore) {
                bestScore = score
                // Never nearer than the usual place (closer felt in the lap), and the middle of a big
                // table can be out of reach: never farther than an easy lean.
                best = Table(pos.y, ahead.coerceIn(BoardView.TABLE_Z, BoardView.TABLE_Z + 0.12f))
            }
        }
        best
    }.onFailure { Log.w(TAG, "plane detection: ${it.message}") }.getOrNull()

    fun stop() {
        runCatching { PlaneTrackingManager.stop() }
    }
}
