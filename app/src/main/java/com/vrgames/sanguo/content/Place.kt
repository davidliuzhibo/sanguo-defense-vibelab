package com.vrgames.sanguo.content

import android.os.Handler
import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.sanguo.render.BoardView
import com.vrgames.sanguo.render.setPos
import com.vrgames.sanguo.render.transform
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Where the sand table lies: in front of the player's heading, averaged over about a second once tracking
 * is in (the first pose alone left earlier games askew), at table height and [BoardView.TABLE_Z] ahead.
 */
class Place {
    var root: Entity? = null
    var debug = false
    private var x = 0f
    private var z = 0f
    private var facing = 0f
    private var height = BoardView.TABLE_H
    private var lift = 0f
    private var distance = BoardView.TABLE_Z
    private var placed = false
    private var collecting = 0.0
    private val headings = ArrayList<Float>()
    var lastPose: Pair<Float, Float>? = null

    fun again() {
        placed = false
        headings.clear()
        collecting = 0.0
    }

    fun frame(eye: Vector3, forward: Vector3, dt: Double, main: Handler) {
        lastPose = eye.x to eye.z
        if (placed || debug || hypot(forward.x, forward.z) < 0.2f) return
        headings += Math.toDegrees(atan2(-forward.x, -forward.z).toDouble()).toFloat()
        collecting += dt
        if (collecting < 1.0) return
        val s = headings.sumOf { sin(Math.toRadians(it.toDouble())) }
        val c = headings.sumOf { cos(Math.toRadians(it.toDouble())) }
        facing = Math.toDegrees(atan2(s, c)).toFloat()
        x = eye.x
        z = eye.z
        placed = true
        Log.i("SanguoStage", "facing: ${headings.size} samples, first ${headings.first()}, mean $facing, range ${headings.min()}..${headings.max()}, eye $eye")
        main.post { put() }
    }

    fun table(h: Float, d: Float) {
        height = h
        distance = d
        put()
    }

    fun lift(dy: Float) {
        lift = dy
        put()
    }

    fun put() {
        if (debug) return
        val v = root ?: return
        val a = Math.toRadians(facing.toDouble())
        v.setPos(Vector3(x - distance * sin(a).toFloat(), height + lift, z - distance * cos(a).toFloat()))
        v.transform().eulerAngles = EulerAngles(0f, facing, 0f)
    }

    /** A hand movement in the room as (x, z) in the book's frame. */
    fun toBook(dx: Double, dz: Double): Pair<Double, Double> {
        val a = Math.toRadians(-facing.toDouble())
        return (dx * cos(a) + dz * sin(a)) to (-dx * sin(a) + dz * cos(a))
    }

    val heading get() = facing
}
