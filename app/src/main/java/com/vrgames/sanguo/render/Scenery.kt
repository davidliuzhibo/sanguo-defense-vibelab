package com.vrgames.sanguo.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshInstancesResource
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Transform
import com.pico.spatial.core.math.Vector3
import com.vrgames.sanguo.core.Level
import com.vrgames.sanguo.core.P
import com.vrgames.sanguo.core.Vec3
import com.vrgames.sanguo.render.BoardView.Companion.D
import com.vrgames.sanguo.render.BoardView.Companion.TOP
import com.vrgames.sanguo.render.BoardView.Companion.W
import com.vrgames.sanguo.render.BoardView.Companion.WALL_H
import kotlin.math.sin

/**
 * The land around the roads of each battle (v0.2): trees, fields, huts, hills, reeds, the moored
 * fleet at 赤壁, snow at 荆州; banners on the wall and at the enemy's road heads that move in the
 * wind; and the weather of the battle (catkins, dust, river mist, autumn rain, falling leaves,
 * snow). Everything is placed by a seeded generator clear of roads, slots and water, so a battle
 * looks the same every time. Same-looking pieces share one instanced mesh.
 */
class Scenery(parent: Entity) {
    private val node = Entity().also { parent.addChild(it) }
    private val weatherNode = Entity().also { parent.addChild(it) }

    enum class Weather { CATKINS, DUST, MIST, RAIN, LEAVES, SNOW }

    private val sphere = MeshResource.createSphere(1f).shared()
    private val cone = MeshResource.createCone(1f, 1f).shared()
    private val cyl = MeshResource.createCylinder(1f, 1f).shared()
    private val cube = MeshResource.createBox(Vector3(1f, 1f, 1f), 0f).shared()
    private val mats = HashMap<String, Material>()
    private fun mat(key: String, make: () -> Material): Material = mats.getOrPut(key) { make().shared() }

    // Weather pools: rain is a thin upright streak, the rest small round flakes.
    private val rain = Sparks("rain", 280, MeshResource.createBox(Vector3(0.05f, 1f, 0.05f), 0f).shared(), glow(color(0.75f, 0.85f, 1f, 0.6f)).shared())
    private val snow = Sparks("snow", 220, sphere, glow(color(1f, 1f, 1f, 0.95f)).shared())
    private val leaves = Sparks("leaves", 120, MeshResource.createBox(Vector3(1f, 0.2f, 0.7f), 0f).shared(), pbr(color(0.86f, 0.42f, 0.14f), roughness = 0.9f).shared())
    private val catkins = Sparks("catkins", 80, sphere, glow(color(1f, 1f, 0.95f, 0.8f)).shared())
    private val dust = Sparks("dust", 140, sphere, pbr(color(0.8f, 0.7f, 0.52f), roughness = 1f).shared())
    private val mist = Sparks("mist", 60, sphere, glow(color(0.85f, 0.9f, 1f, 0.12f)).shared())

    init {
        for (s in listOf(rain, snow, leaves, catkins, dust, mist)) weatherNode.addChild(s.entity)
    }

    var weather = Weather.CATKINS; private set

    /** Where the moored fleet lies (赤壁), for the fire of 借东风. */
    val fleetAt = ArrayList<P>()
    private val flags = ArrayList<Pair<Entity, Double>>()
    private var seed = 1
    private var acc = 0.0
    private var clock = 0.0

    /** How hard the wind blows the banners (1 normal; 借东风 raises it for a while). */
    var gust = 1.0

    private fun rnd(): Double {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 8) and 0xFFFF) / 65536.0
    }

    private fun rr(a: Double, b: Double) = a + (b - a) * rnd()

    /** Builds the scenery of [l]; [water] are the river rectangles drawn on the table. */
    fun build(l: Level, water: List<FloatArray>) {
        node.removeAllChildren()
        flags.clear()
        fleetAt.clear()
        for (s in listOf(rain, snow, leaves, catkins, dust, mist)) s.clear()
        seed = l.id.hashCode() or 1
        weather = WEATHER[l.id] ?: Weather.CATKINS
        val taken = ArrayList<Pair<P, Double>>()

        fun clear(p: P, r: Double, nearWater: Boolean = false): Boolean {
            if (p.x < -W / 2 + 0.02 + r || p.x > W / 2 - 0.02 - r || p.z < -D / 2 + 0.015 + r || p.z > 0.155 - r) return false
            for (path in l.paths) if (!path.water && path.nearest(p).second < 0.016 + r) return false
            for (sl in l.slots) if (sl.at.dist(p) < 0.026 + r) return false
            for (t in taken) if (t.first.dist(p) < t.second + r) return false
            val inWater = water.any { w -> p.x > w[0] - r && p.x < w[2] + r && p.z > w[1] - r && p.z < w[3] + r }
            if (inWater && !nearWater) return false
            if (l.id == "changban" && p.dist(P(0.0, -0.02)) < 0.05 + r) return false
            return true
        }

        fun spots(n: Int, r: Double, tries: Int = 400, nearWater: Boolean = false, zMin: Double = -1.0, zMax: Double = 1.0): List<P> {
            val out = ArrayList<P>()
            var k = 0
            while (out.size < n && k++ < tries) {
                val p = P(rr(-0.3, 0.3), rr(maxOf(-0.21, zMin), minOf(0.15, zMax)))
                if (clear(p, r, nearWater)) {
                    out += p
                    taken += p to r
                }
            }
            return out
        }

        val trees = ArrayList<Transform>()
        val canopy = HashMap<String, ArrayList<Transform>>()
        val trunks = ArrayList<Transform>()
        fun at(x: Double, y: Double, z: Double, sx: Double, sy: Double = sx, sz: Double = sx, yaw: Float = 0f) =
            Transform(Vector3(x.toFloat(), y.toFloat(), z.toFloat()), EulerAngles(0f, yaw, 0f), Vector3(sx.toFloat(), sy.toFloat(), sz.toFloat()))
        fun leaf(key: String, t: Transform) { canopy.getOrPut(key) { ArrayList() } += t }

        // A round broadleaf: a trunk and two or three overlapping crowns.
        fun broadleaf(p: P, s: Double, palette: List<String>) {
            trunks += at(p.x, TOP + 0.012 * s, p.z, 0.0035 * s, 0.024 * s, 0.0035 * s)
            val c = palette[(rnd() * palette.size).toInt().coerceAtMost(palette.size - 1)]
            leaf(c, at(p.x, TOP + 0.03 * s, p.z, 0.016 * s, 0.014 * s, 0.016 * s))
            leaf(c, at(p.x + 0.008 * s, TOP + 0.026 * s, p.z + 0.004 * s, 0.011 * s))
            leaf(palette.last(), at(p.x - 0.006 * s, TOP + 0.038 * s, p.z - 0.003 * s, 0.01 * s))
        }

        // A pine: a trunk and three stacked cones; snow on the tips in winter.
        fun pine(p: P, s: Double, snowy: Boolean) {
            trunks += at(p.x, TOP + 0.008 * s, p.z, 0.003 * s, 0.016 * s, 0.003 * s)
            for (k in 0 until 3) {
                val h = 0.022 * s * (1 - k * 0.18)
                val r = 0.015 * s * (1 - k * 0.27)
                val y = TOP + (0.022 + k * 0.012) * s
                leaf("pine", at(p.x, y, p.z, r, h, r))
                if (snowy) leaf("snowcap", at(p.x, y + h * 0.28, p.z, r * 0.62, h * 0.45, r * 0.62))
            }
        }

        // A willow: a trunk and a tall drooping crown.
        fun willow(p: P, s: Double) {
            trunks += at(p.x, TOP + 0.012 * s, p.z, 0.0035 * s, 0.024 * s, 0.0035 * s)
            leaf("willow", at(p.x, TOP + 0.028 * s, p.z, 0.015 * s, 0.022 * s, 0.015 * s))
            leaf("willowLight", at(p.x + 0.005 * s, TOP + 0.036 * s, p.z, 0.009 * s, 0.012 * s, 0.009 * s))
        }

        // A field: a flat plot with rows of crops across it.
        val plots = HashMap<String, ArrayList<Transform>>()
        fun field(p: P, crop: String, ground: String) {
            val yaw = (if (rnd() < 0.5) 0f else 90f) + rr(-8.0, 8.0).toFloat()
            plots.getOrPut(ground) { ArrayList() } += at(p.x, TOP + 0.0008, p.z, 0.05, 0.0016, 0.038, yaw)
            val rad = Math.toRadians(yaw.toDouble())
            for (k in -3..3) {
                val off = k * 0.0052
                plots.getOrPut(crop) { ArrayList() } += at(p.x + off * kotlin.math.cos(rad), TOP + 0.0025, p.z - off * sin(rad), 0.0026, 0.003, 0.034, yaw)
            }
        }

        // A hut: walls and a dark roof; [roof] picks the roof colour.
        val huts = HashMap<String, ArrayList<Transform>>()
        fun hut(p: P, roof: String) {
            val yaw = rr(-20.0, 20.0).toFloat()
            huts.getOrPut("hutWall") { ArrayList() } += at(p.x, TOP + 0.007, p.z, 0.022, 0.014, 0.016, yaw)
            huts.getOrPut(roof) { ArrayList() } += Transform(
                Vector3(p.x.toFloat(), (TOP + 0.017).toFloat(), p.z.toFloat()),
                EulerAngles(0f, yaw, 45f), Vector3(0.0125f, 0.0125f, 0.019f),
            )
        }

        when (l.id) {
            "xinye" -> {
                for (p in spots(4, 0.034)) field(p, if (rnd() < 0.5) "cropGreen" else "cropYoung", "soil")
                for (p in spots(3, 0.02)) hut(p, "roofDark")
                for (p in spots(16, 0.016)) broadleaf(p, rr(0.85, 1.2), listOf("leafGreen", "leafDeep", "leafLight"))
            }
            "changban" -> {
                for (p in spots(7, 0.016)) broadleaf(p, rr(0.8, 1.0), listOf("leafOlive", "leafDry"))
                for (p in spots(22, 0.006)) leaf("dryGrass", at(p.x, TOP + 0.005, p.z, 0.004, 0.01, 0.004))
                for (p in spots(9, 0.008)) leaf("rock", at(p.x, TOP + 0.002, p.z, rr(0.004, 0.008), 0.004, rr(0.004, 0.007), rr(0.0, 90.0).toFloat()))
                for (p in spots(3, 0.045, zMax = -0.12)) leaf("hillDry", at(p.x, TOP.toDouble(), p.z, 0.05, 0.022, 0.034))
            }
            "chibi" -> {
                // Reeds along the near shore, Cao's chained fleet moored on the far water.
                for (p in spots(26, 0.006, nearWater = true, zMin = -0.04, zMax = 0.04)) {
                    for (k in 0 until 3) leaf("reed", at(p.x + rr(-0.004, 0.004), TOP + 0.009, p.z + rr(-0.004, 0.004), 0.0012, 0.018, 0.0012))
                    leaf("reedTip", at(p.x, TOP + 0.019, p.z, 0.0022, 0.004, 0.0022))
                }
                for (p in spots(8, 0.016, zMin = 0.02)) broadleaf(p, rr(0.85, 1.1), listOf("leafGreen", "leafDeep"))
                fleet(l, water)
            }
            "fancheng" -> {
                for (p in spots(3, 0.034, zMin = -0.15, zMax = -0.04)) field(p, "cropRipe", "soilWet")
                for (p in spots(12, 0.016)) willow(p, rr(0.9, 1.15))
                for (p in spots(2, 0.02, zMin = -0.04)) hut(p, "roofDark")
            }
            "xicheng" -> {
                for (p in spots(4, 0.045, zMax = -0.14)) leaf("hill", at(p.x, TOP.toDouble(), p.z, 0.055, 0.03, 0.036))
                for (p in spots(14, 0.016)) broadleaf(p, rr(0.85, 1.2), listOf("maple", "mapleRed", "leafGold"))
                for (p in spots(2, 0.02)) hut(p, "roofDark")
            }
            "jingzhou" -> {
                for (p in spots(10, 0.03)) leaf("snowPatch", at(p.x, TOP + 0.0006, p.z, rr(0.02, 0.035), 0.0012, rr(0.015, 0.03), rr(0.0, 90.0).toFloat()))
                taken.clear()
                for (p in spots(16, 0.016)) pine(p, rr(0.9, 1.25), snowy = true)
                // Snow along the top of the wall.
                leaf("wallSnow", at(0.0, TOP + WALL_H + 0.0015, 0.19, W.toDouble(), 0.003, 0.032))
            }
        }

        add("trunk", trunks, cyl, mat("trunk") { pbr(color(0.36f, 0.25f, 0.16f), roughness = 0.9f) })
        for ((k, list) in canopy) add(k, list, meshFor(k), material(k))
        for ((k, list) in plots) add(k, list, cube, material(k))
        for ((k, list) in huts) add(k, list, cube, material(k))
        banners(l)
    }

    /** Cao's fleet at 赤壁, chained bow to stern on the far water, clear of the boats' lanes. */
    private fun fleet(l: Level, water: List<FloatArray>) {
        val w = water.firstOrNull() ?: return
        val hulls = ArrayList<Transform>()
        val sails = ArrayList<Transform>()
        val chain = ArrayList<Transform>()
        var placed = 0
        var x = w[2] - 0.035f
        val z = w[1] + 0.022f
        while (placed < 6 && x > w[0] + 0.03) {
            val p = P(x.toDouble(), z.toDouble())
            if (l.paths.all { it.nearest(p).second > 0.035 }) {
                fleetAt += p
                hulls += Transform(Vector3(x, TOP + 0.004f, z), EulerAngles(0f, 90f, 0f), Vector3(0.012f, 0.008f, 0.03f))
                sails += Transform(Vector3(x, TOP + 0.02f, z), EulerAngles(0f, 90f, 0f), Vector3(0.0015f, 0.022f, 0.018f))
                if (placed > 0) chain += Transform(Vector3(x + 0.022f, TOP + 0.006f, z), EulerAngles(0f, 0f, 0f), Vector3(0.016f, 0.0012f, 0.0012f))
                placed++
                x -= 0.042f
            } else {
                x -= 0.01f
            }
        }
        add("hull", hulls, cube, material("hull"))
        add("sail", sails, cube, material("sail"))
        add("chain", chain, cube, material("chain"))
    }

    /** Our red banners along the wall; the enemy's dark blue ones at the head of each road. */
    private fun banners(l: Level) {
        val red = material("bannerRed")
        val blue = material("bannerBlue")
        val pole = material("pole")
        fun flag(x: Float, y: Float, z: Float, cloth: Material, phase: Double) {
            val holder = Entity().apply { setPos(Vector3(x, y, z)) }
            holder.addChild(ModelEntity(cyl, pole).apply { transform().setScaleVector(Vector3(0.0012f, 0.05f, 0.0012f)); setPos(Vector3(0f, 0.025f, 0f)) })
            // The cloth hangs from the pole's top; [swing] turns about the pole.
            val swing = Entity().apply { setPos(Vector3(0f, 0.04f, 0f)) }
            swing.addChild(ModelEntity(cube, cloth).apply { transform().setScaleVector(Vector3(0.022f, 0.016f, 0.0008f)); setPos(Vector3(0.011f, 0f, 0f)) })
            holder.addChild(swing)
            node.addChild(holder)
            flags += swing to phase
        }
        for ((k, x) in listOf(-0.27f, -0.15f, 0.15f, 0.27f).withIndex()) flag(x, TOP + WALL_H, 0.19f, red, k * 1.3)
        for ((k, path) in l.paths.withIndex()) {
            if (path.water) continue
            val a = path.at(0.015)
            val b = path.at(0.03)
            // Beside the road, on the outer side.
            val nx = -(b.z - a.z)
            val nz = b.x - a.x
            val n = kotlin.math.hypot(nx, nz).coerceAtLeast(1e-6)
            flag((a.x + nx / n * 0.024).toFloat(), TOP, (a.z + nz / n * 0.024).toFloat(), blue, 0.7 + k)
        }
    }

    private fun material(k: String): Material = when (k) {
        "leafGreen" -> mat(k) { pbr(color(0.36f, 0.58f, 0.27f), roughness = 0.9f) }
        "leafDeep" -> mat(k) { pbr(color(0.24f, 0.45f, 0.22f), roughness = 0.9f) }
        "leafLight" -> mat(k) { pbr(color(0.55f, 0.72f, 0.35f), roughness = 0.9f) }
        "leafOlive" -> mat(k) { pbr(color(0.5f, 0.52f, 0.28f), roughness = 0.9f) }
        "leafDry" -> mat(k) { pbr(color(0.66f, 0.6f, 0.32f), roughness = 0.9f) }
        "leafGold" -> mat(k) { pbr(color(0.9f, 0.7f, 0.25f), roughness = 0.9f) }
        "maple" -> mat(k) { pbr(color(0.88f, 0.45f, 0.16f), roughness = 0.9f) }
        "mapleRed" -> mat(k) { pbr(color(0.75f, 0.2f, 0.12f), roughness = 0.9f) }
        "willow" -> mat(k) { pbr(color(0.3f, 0.48f, 0.3f), roughness = 0.9f) }
        "willowLight" -> mat(k) { pbr(color(0.45f, 0.62f, 0.36f), roughness = 0.9f) }
        "pine" -> mat(k) { pbr(color(0.18f, 0.36f, 0.26f), roughness = 0.9f) }
        "snowcap", "snowPatch", "wallSnow" -> mat("snow") { pbr(color(0.96f, 0.97f, 1f), roughness = 0.7f) }
        "dryGrass" -> mat(k) { pbr(color(0.78f, 0.68f, 0.38f), roughness = 1f) }
        "rock", "chain" -> mat("rock") { pbr(color(0.48f, 0.46f, 0.44f), roughness = 1f) }
        "hill" -> mat(k) { pbr(color(0.52f, 0.56f, 0.36f), roughness = 1f) }
        "hillDry" -> mat(k) { pbr(color(0.7f, 0.62f, 0.42f), roughness = 1f) }
        "reed" -> mat(k) { pbr(color(0.6f, 0.62f, 0.38f), roughness = 1f) }
        "reedTip" -> mat(k) { pbr(color(0.82f, 0.74f, 0.55f), roughness = 1f) }
        "soil" -> mat(k) { pbr(color(0.55f, 0.42f, 0.28f), roughness = 1f) }
        "soilWet" -> mat(k) { pbr(color(0.42f, 0.33f, 0.24f), roughness = 0.5f) }
        "cropGreen" -> mat(k) { pbr(color(0.45f, 0.66f, 0.28f), roughness = 0.9f) }
        "cropYoung" -> mat(k) { pbr(color(0.62f, 0.78f, 0.38f), roughness = 0.9f) }
        "cropRipe" -> mat(k) { pbr(color(0.85f, 0.72f, 0.3f), roughness = 0.9f) }
        "hutWall" -> mat(k) { pbr(color(0.86f, 0.8f, 0.68f), roughness = 0.9f) }
        "roofDark" -> mat(k) { pbr(color(0.3f, 0.3f, 0.32f), roughness = 0.8f) }
        "hull" -> mat(k) { pbr(color(0.4f, 0.28f, 0.18f), roughness = 0.8f) }
        "sail" -> mat(k) { pbr(color(0.82f, 0.78f, 0.66f), roughness = 0.9f) }
        "bannerRed" -> mat(k) { pbr(color(0.8f, 0.14f, 0.1f), roughness = 0.8f) }
        "bannerBlue" -> mat(k) { pbr(color(0.16f, 0.22f, 0.45f), roughness = 0.8f) }
        "pole" -> mat(k) { pbr(color(0.3f, 0.22f, 0.14f), roughness = 0.8f) }
        else -> mat("leafGreen") { pbr(color(0.36f, 0.58f, 0.27f), roughness = 0.9f) }
    }

    private var batch = 0

    private fun meshFor(k: String) = when (k) {
        "pine", "snowcap", "dryGrass", "reedTip" -> cone
        "reed", "snowPatch" -> cyl
        "wallSnow" -> cube
        else -> sphere
    }

    /** One entity drawing [list] instances of [mesh]. */
    private fun add(key: String, list: List<Transform>, mesh: MeshResource, m: Material) {
        if (list.isEmpty()) return
        val name = "sc${batch++}$key"
        val e = ModelEntity(mesh, m)
        e.components[ModelComponent::class.java]!!.meshInstances =
            MeshInstancesResource.create(name, list.mapIndexed { i, t -> MeshInstancesResource.Instance("$name-$i", t) })
        node.addChild(e)
    }

    /** Banners in the wind and the battle's weather; [dt] is real time. */
    fun frame(dt: Double) {
        clock += dt
        gust = 1.0 + (gust - 1.0) * (1 - dt * 0.4).coerceAtLeast(0.0)
        for ((f, ph) in flags) {
            val a = 25.0 * gust + 18.0 * sin(clock * 2.3 * gust + ph) + 6.0 * sin(clock * 5.1 + ph * 2)
            f.transform().eulerAngles = EulerAngles(0f, (-a).toFloat(), 0f)
        }
        acc += dt
        val rate = when (weather) {
            Weather.RAIN -> 260.0
            Weather.SNOW -> 26.0
            Weather.LEAVES -> 6.0
            Weather.CATKINS -> 4.0
            Weather.DUST -> 10.0
            Weather.MIST -> 3.0
        }
        while (acc > 1 / rate) {
            acc -= 1 / rate
            val x = rr(-0.3, 0.3)
            val z = rr(-0.2, 0.17)
            when (weather) {
                Weather.RAIN -> rain.emit(Vec3(x, TOP + 0.24, z), 1, 0.0, 0.32, 0.012f, drift = Vec3(-0.05, -0.75, 0.0))
                Weather.SNOW -> snow.emit(Vec3(x, TOP + 0.22, z), 1, 0.01, 4.5, 0.0024f, drift = Vec3(0.012, -0.05, 0.0))
                Weather.LEAVES -> leaves.emit(Vec3(x, TOP + rr(0.12, 0.2), z), 1, 0.02, 3.2, 0.004f, drift = Vec3(0.025, -0.055, 0.004))
                Weather.CATKINS -> catkins.emit(Vec3(x, TOP + rr(0.03, 0.14), z), 1, 0.008, 4.0, 0.0016f, drift = Vec3(0.016, -0.008, 0.002))
                Weather.DUST -> dust.emit(Vec3(-0.3, TOP + rr(0.004, 0.02), z), 1, 0.015, 2.2, rr(0.004, 0.008).toFloat(), drift = Vec3(0.17, 0.006, 0.0))
                Weather.MIST -> mist.emit(Vec3(x, TOP + 0.012, rr(-0.2, -0.04)), 1, 0.004, 5.0, 0.03f, drift = Vec3(0.01, 0.002, 0.0))
            }
        }
        rain.update(dt, 0.0); snow.update(dt, 0.0); leaves.update(dt, 0.0); catkins.update(dt, 0.0); dust.update(dt, 0.2); mist.update(dt, 0.0)
    }

    companion object {
        val WEATHER = mapOf(
            "xinye" to Weather.CATKINS, "changban" to Weather.DUST, "chibi" to Weather.MIST,
            "fancheng" to Weather.RAIN, "xicheng" to Weather.LEAVES, "jingzhou" to Weather.SNOW,
        )

        /** What the battle card says about the day's weather. */
        val WEATHER_NOTE = mapOf(
            "xinye" to "春日，柳絮纷飞", "changban" to "长坂坡上，尘土飞扬", "chibi" to "江上起雾",
            "fancheng" to "秋雨连绵", "xicheng" to "深秋，落叶满山", "jingzhou" to "大雪",
        )
    }
}
