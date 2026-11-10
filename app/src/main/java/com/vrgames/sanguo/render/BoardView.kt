package com.vrgames.sanguo.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.sanguo.core.Battle
import com.vrgames.sanguo.core.EnemyType
import com.vrgames.sanguo.core.Level
import com.vrgames.sanguo.core.P
import com.vrgames.sanguo.core.SlotKind
import com.vrgames.sanguo.core.Stratagem
import com.vrgames.sanguo.core.UnitType
import com.vrgames.sanguo.core.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The sand table on the player's table. Frame: the root sits on the table top at the table's middle,
 * +z toward the player, +x to the right; the board's frame (see [P]) is the same, the city at the
 * near edge. Draws the land, roads, rivers, the city wall and gate, the slots, the defenders (the
 * figures in [models] when loaded), the enemy, shots and what stratagems do, and the tray of units
 * at the front edge to drag from. Defeated soldiers go up in a puff: no blood.
 */
class BoardView(private val models: Map<String, Entity>) {
    val root = Entity()
    private val land = Entity().also { root.addChild(it) }
    private val sphere = MeshResource.createSphere(1f).shared()
    private val smoke = Sparks("smoke", 160, sphere, pbr(color(0.92f, 0.92f, 0.9f), roughness = 1f).shared()).also { root.addChild(it.entity) }
    private val sparks = Sparks("sparks", 160, sphere, glow(color(1f, 0.8f, 0.4f, 1f)).shared()).also { root.addChild(it.entity) }
    private val fire = Sparks("fire", 220, sphere, glow(color(1f, 0.45f, 0.15f, 1f)).shared()).also { root.addChild(it.entity) }
    private val water = Sparks("water", 220, sphere, glow(color(0.45f, 0.7f, 1f, 0.8f)).shared()).also { root.addChild(it.entity) }
    // v0.2: hit flashes, stone chips, dark smoke, wind streaks, golden notes.
    private val flash = Sparks("flash", 40, sphere, glow(color(1f, 1f, 1f, 0.9f)).shared()).also { root.addChild(it.entity) }
    private val chips = Sparks("chips", 120, MeshResource.createBox(Vector3(1f, 0.7f, 0.8f), 0f).shared(), pbr(color(0.5f, 0.47f, 0.44f), roughness = 1f).shared()).also { root.addChild(it.entity) }
    private val soot = Sparks("soot", 160, sphere, pbr(color(0.25f, 0.24f, 0.24f), roughness = 1f).shared()).also { root.addChild(it.entity) }
    private val wind = Sparks("wind", 140, MeshResource.createBox(Vector3(1f, 0.05f, 0.05f), 0f).shared(), glow(color(0.9f, 0.95f, 1f, 0.55f)).shared()).also { root.addChild(it.entity) }
    private val gold = Sparks("gold", 100, sphere, glow(color(1f, 0.82f, 0.35f, 1f)).shared()).also { root.addChild(it.entity) }
    private val scenery = Scenery(root)
    private val slotViews = HashMap<Int, Entity>()
    private val slotPads = HashMap<Int, Entity>()
    private val unitViews = HashMap<Int, Pair<UnitType, Entity>>()
    private val enemyViews = HashMap<Battle.Enemy, Entity>()
    private val shots = ArrayList<Shot>()
    private var flood: Entity? = null
    private var general: Entity? = null
    private var level: Level? = null
    private var clock = 0.0

    /** Collider owner → what it is, for the stage's pinches. */
    private val hits = HashMap<Entity, Any>()
    private val tokens = HashMap<UnitType, Entity>()

    sealed interface Hit {
        data class Token(val type: UnitType) : Hit
        data class Placed(val slot: Int) : Hit
        data object General : Hit
    }

    /** A shot in flight; [e] is null for a spear's thrust (nothing flies). It lands on enemy [target]. */
    private class Shot(val e: Entity?, val from: Vector3, val to: Vector3, val arc: Float, val life: Double, val type: UnitType, val target: Int, delay: Double = 0.0) {
        var t = -delay
    }

    /** A placed unit's turning node and figure, for its attack motions. */
    private class Pose(val aim: Entity, val fig: Entity) {
        var yaw = 180.0
        var target = 180.0
        var shotAt = -9.0
        var clash = 0.0
    }

    /** A stratagem's show: [tick] (time since start, frame time) runs until [life]; then [nodes] go. */
    private class Fx(val life: Double, val nodes: List<Entity>, val tick: (Double, Double) -> Unit) {
        var t = 0.0
    }

    private val poses = HashMap<Int, Pose>()
    private val knocked = HashMap<Int, Double>()
    private val fxs = ArrayList<Fx>()
    private var gateDoor: Entity? = null
    private var gateHitAt = -9.0
    private var cracks = 0
    private var shoutAt = -9.0
    private var seed = 7

    /** Called when a shot lands (for its sound). */
    var onImpact: ((UnitType) -> Unit)? = null

    private fun rnd(): Double {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 8) and 0xFFFF) / 65536.0
    }

    private fun rr(a: Double, b: Double) = a + (b - a) * rnd()

    // Shared materials.
    private val sand = pbr(color(0.82f, 0.72f, 0.52f), roughness = 1f).shared()
    private val grass = pbr(color(0.5f, 0.6f, 0.35f), roughness = 1f).shared()
    private val roadMat = pbr(color(0.62f, 0.5f, 0.36f), roughness = 1f).shared()
    private val riverMat = pbr(color(0.3f, 0.5f, 0.68f), roughness = 0.2f).shared()
    private val wallMat = pbr(color(0.58f, 0.55f, 0.5f), roughness = 0.9f).shared()
    private val woodMat = pbr(color(0.45f, 0.3f, 0.18f), roughness = 0.8f).shared()
    private val padIdle = pbr(color(0.9f, 0.85f, 0.7f), roughness = 0.8f).shared()
    private val padOpen = glow(color(1f, 0.85f, 0.35f, 0.9f)).shared()
    private val padNear = glow(color(0.5f, 1f, 0.6f, 1f)).shared()
    private val arrowMat = glow(color(1f, 0.9f, 0.6f, 1f)).shared()
    private val stoneMat = pbr(color(0.45f, 0.42f, 0.4f)).shared()
    private val unitStar = glow(color(1f, 0.85f, 0.3f, 1f)).shared()
    private val crackMat = pbr(color(0.12f, 0.08f, 0.07f), roughness = 1f).shared()
    private val ringMesh = MeshResource.createTorus(1f, 0.93f).shared()
    private val ringGold = glow(color(1f, 0.8f, 0.35f, 0.9f)).shared()
    private val ringJade = glow(color(0.45f, 1f, 0.8f, 0.9f)).shared()
    private val waveMat = pbr(color(0.42f, 0.62f, 0.78f), roughness = 0.15f).shared()
    private val boltMesh = MeshResource.createBox(Vector3(0.0018f, 0.0018f, 0.016f), 0f).shared()
    private val arrowMesh = MeshResource.createBox(Vector3(0.002f, 0.002f, 0.02f), 0f).shared()

    init {
        // The board: a wooden frame and the sand top, with the tray ledge at the front.
        root.addChild(model(MeshResource.createBox(Vector3(W + 0.04f, 0.025f, D + 0.04f), 0.006f), woodMat).apply { setPos(Vector3(0f, 0.0125f, 0f)) })
        root.addChild(model(MeshResource.createBox(Vector3(W, 0.004f, D), 0f), sand).apply { setPos(Vector3(0f, TOP - 0.002f, 0f)) })
        root.addChild(model(MeshResource.createBox(Vector3(W + 0.04f, 0.012f, TRAY_D), 0.004f), woodMat).apply { setPos(Vector3(0f, 0.006f, D / 2 + 0.02f + TRAY_D / 2)) })
        buildTray()
    }

    /** Builds the land of [l]: fields, rivers, roads, wall, gate, slots. */
    fun show(l: Level) {
        level = l
        land.removeAllChildren()
        slotViews.clear(); slotPads.clear()
        unitViews.values.forEach { root.removeChild(it.second) }
        unitViews.clear()
        enemyViews.values.forEach { root.removeChild(it) }
        enemyViews.clear()
        hits.keys.filter { hits[it] is Hit.Placed || hits[it] == Hit.General }.forEach { hits.remove(it) }
        flood = null
        poses.clear(); knocked.clear()
        for (fx in fxs) fx.nodes.forEach { root.removeChild(it) }
        fxs.clear()
        for (s in shots) s.e?.let { root.removeChild(it) }
        shots.clear()
        cracks = 0
        gateHitAt = -9.0
        seed = l.number * 977 + 13
        scenery.build(l, WATER[l.id].orEmpty())
        for (r in WATER[l.id].orEmpty()) {
            land.addChild(model(MeshResource.createBox(Vector3(r[2] - r[0], 0.004f, r[3] - r[1]), 0f), riverMat).apply {
                setPos(Vector3((r[0] + r[2]) / 2, TOP + 0.001f, (r[1] + r[3]) / 2))
            })
        }
        if (l.id == "changban") land.addChild(model(MeshResource.createBox(Vector3(0.05f, 0.008f, 0.06f), 0.002f), woodMat).apply { setPos(Vector3(0f, TOP + 0.004f, -0.02f)) })
        // Roads as strips along their segments (boats' lanes are drawn as faint wakes).
        for (path in l.paths) {
            for (i in 1 until path.points.size) {
                val a = path.points[i - 1]
                val b = path.points[i]
                val len = a.dist(b).toFloat()
                val yaw = Math.toDegrees(atan2((b.x - a.x), (b.z - a.z))).toFloat()
                val mat = if (path.water) glow(color(0.7f, 0.85f, 1f, 0.25f)) else roadMat
                land.addChild(model(MeshResource.createBox(Vector3(if (path.water) 0.008f else 0.026f, 0.003f, len + 0.026f), 0f), mat).apply {
                    setPos(Vector3(((a.x + b.x) / 2).toFloat(), TOP + 0.002f, ((a.z + b.z) / 2).toFloat()))
                    transform().eulerAngles = EulerAngles(0f, yaw, 0f)
                })
            }
        }
        // The city wall along the near edge, crenellated, and the gate in the middle.
        land.addChild(model(MeshResource.createBox(Vector3(W, WALL_H, 0.03f), 0.002f), wallMat).apply { setPos(Vector3(0f, TOP + WALL_H / 2, 0.19f)) })
        for (k in 0 until 16) land.addChild(model(MeshResource.createBox(Vector3(0.015f, 0.008f, 0.03f), 0f), wallMat).apply { setPos(Vector3(-W / 2 + 0.02f + k * (W - 0.04f) / 15, TOP + WALL_H + 0.004f, 0.19f)) })
        gateDoor = model(MeshResource.createBox(Vector3(0.06f, 0.05f, 0.04f), 0.003f), pbr(color(0.55f, 0.18f, 0.14f))).apply { setPos(Vector3(0f, TOP + 0.045f, 0.19f)) }.also { land.addChild(it) }
        land.addChild(model(MeshResource.createBox(Vector3(0.075f, 0.012f, 0.05f), 0.003f), pbr(color(0.2f, 0.22f, 0.26f))).apply { setPos(Vector3(0f, TOP + 0.075f, 0.19f)) })
        // The general of this battle's stratagem stands on the gate tower; pinch him when it is ready.
        // [general] is the holder, so pulsing and turning it keep the model's own scale.
        val figure = piece(GENERALS[l.stratagem]!!, 0.07f) ?: model(MeshResource.createCapsule(0.06f, 0.012f), pbr(color(0.9f, 0.9f, 0.85f)))
        general = Entity().apply { setPos(Vector3(0f, TOP + 0.081f, 0.19f)) }.also { holder ->
            holder.addChild(figure)
            collide(holder, Vector3(0.05f, 0.08f, 0.05f), Vector3(0f, 0.04f, 0f), Hit.General)
            land.addChild(holder)
        }
        for (sl in l.slots) {
            val y = when (sl.kind) { SlotKind.WALL -> TOP + WALL_H; else -> TOP + 0.003f }
            val v = Entity().apply { setPos(Vector3(sl.at.x.toFloat(), y, sl.at.z.toFloat())) }
            val pad = model(MeshResource.createCylinder(0.003f, if (sl.kind == SlotKind.ROAD) 0.014f else 0.018f), padIdle)
            v.addChild(pad)
            land.addChild(v)
            slotViews[sl.id] = v
            slotPads[sl.id] = pad
        }
    }

    // ---- Tray ------------------------------------------------------------------------------------

    private fun buildTray() {
        for ((i, t) in UnitType.entries.withIndex()) {
            val holder = Entity().apply { setPos(trayAt(i)) }
            holder.addChild(model(MeshResource.createCylinder(0.004f, 0.022f), pbr(color(0.85f, 0.75f, 0.45f), metallic = 0.3f, roughness = 0.5f)))
            holder.addChild(figure(t).apply { setPos(Vector3(0f, 0.003f, 0f)) })
            collide(holder, Vector3(0.05f, 0.07f, 0.05f), Vector3(0f, 0.03f, 0f), Hit.Token(t))
            root.addChild(holder)
            tokens[t] = holder
        }
    }

    fun trayAt(i: Int) = Vector3(-0.24f + i * 0.12f, 0.012f, D / 2 + 0.02f + TRAY_D / 2)

    /** Dims the tray's units this battle cannot use yet. */
    fun trayFor(allowed: Set<UnitType>) {
        for ((t, e) in tokens) e.enabled = t in allowed
    }

    private fun figure(t: UnitType): Entity = when (t) {
        UnitType.ARCHER -> piece("archer", 0.05f)
        UnitType.SPEAR -> piece("spearman", 0.05f)
        UnitType.CATAPULT -> piece("catapult", 0.045f)
        UnitType.CROSSBOW -> piece("crossbow", 0.05f)
        UnitType.CHEVAL -> piece("cheval", 0.042f)
    } ?: when (t) {
        UnitType.CHEVAL -> Entity().apply {
            // Crossed stakes: a 拒马.
            for (k in -1..1) addChild(model(MeshResource.createBox(Vector3(0.004f, 0.03f, 0.004f), 0f), woodMat).apply {
                setPos(Vector3(k * 0.012f, 0.012f, 0f))
                transform().eulerAngles = EulerAngles(35f * (if (k % 2 == 0) 1 else -1), 0f, 0f)
            })
            addChild(model(MeshResource.createBox(Vector3(0.036f, 0.004f, 0.004f), 0f), woodMat).apply { setPos(Vector3(0f, 0.012f, 0f)) })
        }
        else -> model(MeshResource.createCapsule(0.04f, 0.009f), pbr(UNIT_TINT[t]!!))
    }

    private fun piece(name: String, height: Float): Entity? = models[name]?.clone(Entity.CloneOptions(true, true))?.also {
        it.setPos(Vector3(0f, 0f, 0f))
        it.setScale(height)
    }

    private fun collide(e: Entity, size: Vector3, center: Vector3, what: Any) {
        e.components.set(CollisionComponent(listOf(ShapeResource.createBox(size).offsetByTranslation(center)), PhysicsMaterialResource()))
        e.components.set(InteractableComponent())
        e.components.set(HoverEffectComponent())
        hits[e] = what
    }

    fun hitOf(e: Entity?): Hit? {
        var cur = e
        while (cur != null) {
            (hits[cur] as? Hit)?.let { return it }
            cur = cur.getParent()
        }
        return null
    }

    // ---- Dragging a unit from the tray -----------------------------------------------------------

    private var ghost: Entity? = null
    private var ghostType: UnitType? = null
    private var ghostAt = Vector3(0f, 0f, 0f)
    var nearSlot: Int? = null; private set

    fun beginDrag(t: UnitType, b: Battle) {
        endDrag()
        ghostType = t
        ghostAt = tokens[t]!!.transform().position.let { Vector3(it.x, TOP + 0.03f, it.z) }
        ghost = figure(t).also { root.addChild(it); it.setPos(ghostAt) }
        // Light the slots this unit may go on.
        for (sl in b.level.slots) {
            val ok = t in b.available && sl.kind in t.kinds && sl.id !in b.units && !(sl.low && b.flooded)
            slotPads[sl.id]?.let { Assets.setMaterials(it, if (ok) padOpen else padIdle) }
        }
    }

    /** Moves the dragged unit by [d] (board frame, meters) and finds the slot under it. */
    fun drag(d: Vec3, b: Battle) {
        val g = ghost ?: return
        ghostAt = Vector3((ghostAt.x + d.x).toFloat().coerceIn(-W / 2, W / 2), TOP + 0.03f, (ghostAt.z + d.z).toFloat().coerceIn(-D / 2, D / 2 + TRAY_D))
        g.setPos(ghostAt)
        val t = ghostType ?: return
        val here = P(ghostAt.x.toDouble(), ghostAt.z.toDouble())
        val best = b.level.slots.filter { t in b.available && it.kind in t.kinds && it.id !in b.units && !(it.low && b.flooded) }
            .minByOrNull { it.at.dist(here) }?.takeIf { it.at.dist(here) < SNAP }
        if (best?.id != nearSlot) {
            nearSlot?.let { id -> slotPads[id]?.let { Assets.setMaterials(it, padOpen) } }
            best?.let { slotPads[it.id]?.let { p -> Assets.setMaterials(p, padNear) } }
            nearSlot = best?.id
        }
    }

    /** Ends a drag; returns the unit type and the slot it was let go over (or null if none). */
    fun endDrag(): Pair<UnitType, Int?>? {
        val t = ghostType ?: return null
        val slot = nearSlot
        ghost?.let { root.removeChild(it) }
        ghost = null
        ghostType = null
        nearSlot = null
        slotPads.values.forEach { Assets.setMaterials(it, padIdle) }
        return t to slot
    }

    /** The point a dragged unit was let go at (for a "no slot here" puff). */
    fun ghostSpot() = Vec3(ghostAt.x.toDouble(), ghostAt.y.toDouble(), ghostAt.z.toDouble())

    // ---- Battle ----------------------------------------------------------------------------------

    /** Brings the units on the table in line with the battle (placed, upgraded, sold, lost). */
    fun sync(b: Battle) {
        for (id in unitViews.keys.toList()) if (id !in b.units || b.units[id]!!.type != unitViews[id]!!.first) {
            val e = unitViews.remove(id)!!.second
            hits.remove(e)
            root.removeChild(e)
            poses.remove(id)
        }
        for ((id, u) in b.units) {
            val existing = unitViews[id]
            if (existing == null) {
                val holder = Entity().apply { setPos(slotViews[id]!!.transform().position) }
                // Facing the enemy's side of the table until the first shot turns it to its target.
                val aim = Entity().apply { transform().eulerAngles = EulerAngles(0f, 180f, 0f) }
                val fig = figure(u.type)
                aim.addChild(fig)
                holder.addChild(aim)
                poses[id] = Pose(aim, fig)
                collide(holder, Vector3(0.04f, 0.06f, 0.04f), Vector3(0f, 0.025f, 0f), Hit.Placed(id))
                root.addChild(holder)
                unitViews[id] = u.type to holder
                puff(Vec3(u.slot.at.x, TOP + 0.02, u.slot.at.z), small = true)
            }
            // Rank: little gold stars above the unit.
            val holder = unitViews[id]!!.second
            val stars = holder.getChildren().count { it.getName() == "rank" }
            for (k in stars until u.rank - 1) holder.addChild(model(sphere, unitStar).apply {
                setName("rank")
                setScale(0.004f)
                setPos(Vector3(-0.006f + k * 0.012f, 0.065f, 0f))
            })
        }
        general?.let { g -> g.setScale(if (b.stratagemReady) 1.15f else 1f) }
    }

    /** Turns placed units toward what they shoot and plays their attack: draw and loose, thrust, swing. */
    private fun animateUnits(b: Battle, dt: Double) {
        for ((id, p) in poses) {
            val u = b.units[id] ?: continue
            var d = (p.target - p.yaw) % 360.0
            if (d > 180) d -= 360.0
            if (d < -180) d += 360.0
            p.yaw += d * (dt * 8).coerceAtMost(1.0)
            val t = clock - p.shotAt
            val f = if (u.type.interval > 0) (u.cool / u.type.interval).coerceIn(0.0, 1.0) else 1.0
            var pitch = 0.0
            var fwd = 0.0
            var up = 0.0
            when (u.type) {
                UnitType.ARCHER -> {
                    // Draws back as the next shot nears, snaps forward as the arrow leaves.
                    val draw = ((0.45 - f) / 0.45).coerceIn(0.0, 1.0)
                    pitch = -9 * draw
                    fwd = -0.002 * draw
                    if (t < 0.18) pitch += 8 * (1 - t / 0.18)
                }
                UnitType.CROSSBOW -> if (t < 0.3) {
                    // Three bolts: three kicks.
                    val k = (1 - (t % 0.07) / 0.07) * (1 - t / 0.3)
                    pitch = -7 * k
                    fwd = -0.0025 * k
                }
                UnitType.SPEAR -> {
                    if (t < 0.32) {
                        val k = sin(PI * t / 0.32)
                        fwd = 0.014 * k
                        pitch = 20 * k
                    }
                    // Holding attackers on the road: blades clash now and then.
                    if (b.enemies.any { it.held === u }) {
                        p.clash -= dt
                        if (p.clash <= 0) {
                            p.clash = 0.45 + rnd() * 0.2
                            p.shotAt = clock
                            sparks.emit(Vec3(u.slot.at.x, TOP + 0.025, u.slot.at.z - 0.012), 5, 0.07, 0.25, 0.0022f)
                        }
                    }
                }
                UnitType.CATAPULT -> {
                    // Winds down before the throw, flings back, rocks to rest.
                    pitch = 6 * ((0.35 - f) / 0.35).coerceIn(0.0, 1.0)
                    if (t < 0.7) {
                        pitch = if (t < 0.12) 6 - 30 * (t / 0.12) else -24 * exp(-(t - 0.12) * 7) * cos((t - 0.12) * 22)
                        up = if (t < 0.25) 0.003 * sin(PI * t / 0.25) else 0.0
                    }
                }
                UnitType.CHEVAL -> Unit
            }
            p.aim.transform().eulerAngles = EulerAngles(0f, p.yaw.toFloat(), 0f)
            p.fig.setPos(Vector3(0f, up.toFloat(), fwd.toFloat()))
            p.fig.transform().eulerAngles = EulerAngles(pitch.toFloat(), 0f, 0f)
        }
    }

    /** A shot lands: a white flash, the target knocked back; stones throw up dust and chips (or spray). */
    private fun impact(s: Shot, b: Battle) {
        val at = Vec3(s.to.x.toDouble(), TOP + 0.016, s.to.z.toDouble())
        val boat = b.enemies.firstOrNull { it.serial == s.target }?.type == EnemyType.BOAT
        flash.emit(at, 1, 0.0, 0.12, if (s.type == UnitType.CATAPULT) 0.02f else 0.012f)
        if (s.type == UnitType.CATAPULT) {
            val ground = Vec3(at.x, TOP + 0.004, at.z)
            if (boat) {
                water.emit(ground, 18, 0.1, 0.6, 0.005f, drift = Vec3(0.0, 0.07, 0.0))
            } else {
                smoke.emit(ground, 16, 0.12, 0.55, 0.006f, drift = Vec3(0.0, 0.012, 0.0))
                chips.emit(ground, 8, 0.12, 0.4, 0.0025f, drift = Vec3(0.0, 0.05, 0.0))
            }
            val c = P(at.x, at.z)
            for (e in b.enemies) if (e.at.dist(c) < Battle.SPLASH + 0.01) knocked[e.serial] = clock
        } else {
            sparks.emit(at, 4, 0.06, 0.25, 0.002f)
            if (boat) water.emit(Vec3(at.x, TOP + 0.004, at.z), 4, 0.04, 0.35, 0.003f, drift = Vec3(0.0, 0.04, 0.0))
        }
        if (s.target >= 0) knocked[s.target] = clock
        onImpact?.invoke(s.type)
    }

    /** Cracks show on the gate as it weakens: one more for every few points it has lost. */
    private fun crackGate(b: Battle) {
        val want = ((Battle.GATE_HP - b.gate) * 8 / Battle.GATE_HP).coerceIn(0, 8)
        while (cracks < want) {
            cracks++
            land.addChild(model(MeshResource.createBox(Vector3(0.0016f, rr(0.01, 0.022).toFloat(), 0.001f), 0f), crackMat).apply {
                setPos(Vector3(rr(-0.024, 0.024).toFloat(), (TOP + rr(0.028, 0.06)).toFloat(), 0.1695f))
                transform().eulerAngles = EulerAngles(0f, 0f, rr(-45.0, 45.0).toFloat())
            })
        }
    }

    fun frame(b: Battle, dt: Double, realDt: Double = dt) {
        clock += dt
        scenery.frame(realDt)
        animateUnits(b, dt)
        // Enemies: one figure each, facing the way they walk.
        val alive = b.enemies.toSet()
        for (e in enemyViews.keys.filter { it !in alive }) {
            root.removeChild(enemyViews.remove(e)!!)
            knocked.remove(e.serial)
        }
        for (e in b.enemies) {
            val v = enemyViews.getOrPut(e) { enemyFigure(e.type).also { root.addChild(it) } }
            val path = b.level.paths[e.path]
            val here = e.at
            val ahead = path.at(e.s + 0.01)
            val bob = if (e.held != null || b.stall > 0) 0.0 else kotlin.math.abs(sin(clock * 10 + e.serial)) * 0.003
            // Hit: knocked back a step and leaning back, for a quarter second.
            val kt = clock - (knocked[e.serial] ?: -9.0)
            val kk = if (kt < 0.25) 1 - kt / 0.25 else 0.0
            val dx = ahead.x - here.x
            val dz = ahead.z - here.z
            val n = hypot(dx, dz).coerceAtLeast(1e-6)
            val back = 0.006 * kk
            // Held at a spearman: jabbing at him.
            val jab = if (e.held != null) 10 * sin(clock * 8 + e.serial).coerceAtLeast(0.0) else 0.0
            v.setPos(Vector3((here.x - dx / n * back).toFloat(), (TOP + bob + if (path.water) 0.002 else 0.0).toFloat(), (here.z - dz / n * back).toFloat()))
            v.transform().eulerAngles = EulerAngles((-18 * kk + jab).toFloat(), Math.toDegrees(atan2(dx, dz)).toFloat() + (if (b.maze > 0) (clock * 360).toFloat() else 0f), 0f)
        }
        // Shots in flight, nose along the arc; a stone trails dust.
        val it = shots.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.t += dt
            if (s.t < 0) continue
            val k = (s.t / s.life).toFloat().coerceAtMost(1f)
            s.e?.let { e ->
                e.enabled = true
                val y = s.from.y + (s.to.y - s.from.y) * k + s.arc * 4 * k * (1 - k)
                e.setPos(Vector3(s.from.x + (s.to.x - s.from.x) * k, y, s.from.z + (s.to.z - s.from.z) * k))
                val h = hypot((s.to.x - s.from.x).toDouble(), (s.to.z - s.from.z).toDouble()).coerceAtLeast(1e-4)
                val dy = (s.to.y - s.from.y) + s.arc * 4 * (1 - 2 * k)
                val yaw = Math.toDegrees(atan2((s.to.x - s.from.x).toDouble(), (s.to.z - s.from.z).toDouble())).toFloat()
                e.transform().eulerAngles = EulerAngles(-Math.toDegrees(atan2(dy.toDouble(), h)).toFloat(), yaw, 0f)
                if (s.type == UnitType.CATAPULT && rnd() < 0.5) smoke.emit(Vec3(e.transform().position.x.toDouble(), y.toDouble(), e.transform().position.z.toDouble()), 1, 0.005, 0.3, 0.003f)
            }
            if (k >= 1f) {
                s.e?.let { root.removeChild(it) }
                it.remove()
                impact(s, b)
            }
        }
        // The gate shakes when struck.
        gateDoor?.let { g ->
            val kt = clock - gateHitAt
            val off = if (kt < 0.45) 0.0025 * sin(kt * 90) * (1 - kt / 0.45) else 0.0
            g.setPos(Vector3(off.toFloat(), TOP + 0.045f, 0.19f))
        }
        general?.let { g ->
            val st = clock - shoutAt
            when {
                st < 0.8 -> g.setScale((1 + 0.3 * sin(PI * st / 0.8)).toFloat())
                b.stratagemReady -> g.transform().eulerAngles = EulerAngles(0f, (sin(clock * 3) * 20).toFloat(), 0f)
            }
        }
        val fi = fxs.iterator()
        while (fi.hasNext()) {
            val fx = fi.next()
            fx.t += dt
            fx.tick(fx.t, dt)
            if (fx.t >= fx.life) {
                fx.nodes.forEach { root.removeChild(it) }
                fi.remove()
            }
        }
        smoke.update(dt, 1.5); sparks.update(dt, 1.0); fire.update(dt, 0.8); water.update(dt, 1.0)
        flash.update(dt, 0.0); chips.update(dt, 1.2); soot.update(dt, 0.6); wind.update(dt, 0.0); gold.update(dt, 0.5)
        flood?.let { f -> f.transform().setScaleVector(Vector3(1f, (f.transform().scaleVector.y + dt * 2).toFloat().coerceAtMost(1f), 1f)) }
    }

    private fun enemyFigure(t: EnemyType): Entity = when (t) {
        EnemyType.FOOT -> piece("foot", 0.045f)
        EnemyType.CAVALRY -> piece("cavalry", 0.05f)
        EnemyType.RAM -> piece("ram", 0.05f)
        // The war boat model lies along x with its bow at +x: turned so the bow leads (+z).
        EnemyType.BOAT -> piece("boat", 0.07f)?.let { m -> Entity().apply { m.transform().eulerAngles = EulerAngles(0f, -90f, 0f); addChild(m) } }
    } ?: when (t) {
        EnemyType.BOAT -> Entity().apply {
            addChild(model(MeshResource.createBox(Vector3(0.024f, 0.01f, 0.05f), 0.004f), woodMat).apply { setPos(Vector3(0f, 0.005f, 0f)) })
            addChild(model(MeshResource.createBox(Vector3(0.022f, 0.03f, 0.002f), 0f), pbr(color(0.85f, 0.85f, 0.8f))).apply { setPos(Vector3(0f, 0.025f, 0f)) })
            addChild(model(MeshResource.createBox(Vector3(0.004f, 0.012f, 0.004f), 0f), pbr(color(0.25f, 0.4f, 0.8f))).apply { setPos(Vector3(0f, 0.045f, 0f)) })
        }
        else -> model(MeshResource.createCapsule(0.035f, 0.008f), pbr(color(0.3f, 0.4f, 0.8f)))
    }

    /** Shows what a step of the battle did. */
    fun on(ev: Battle.Event, b: Battle) {
        when (ev) {
            is Battle.Event.Shot -> {
                val from = slotViews[ev.from.id]?.transform()?.position ?: return
                val start = Vector3(from.x, from.y + 0.04f, from.z)
                val to = Vector3(ev.to.x.toFloat(), TOP + 0.015f, ev.to.z.toFloat())
                poses[ev.from.id]?.let { p ->
                    p.target = Math.toDegrees(atan2((to.x - from.x).toDouble(), (to.z - from.z).toDouble()))
                    p.shotAt = clock
                }
                when (ev.type) {
                    UnitType.CATAPULT -> {
                        val e = model(sphere, stoneMat).apply { setScale(0.006f) }
                        root.addChild(e)
                        shots += Shot(e, start, to, 0.08f, 0.6, ev.type, ev.target)
                    }
                    UnitType.CROSSBOW -> for (k in 0 until 3) {
                        // Three bolts in quick succession, a little spread.
                        val e = model(boltMesh, arrowMat).apply { enabled = false }
                        root.addChild(e)
                        val spread = Vector3(to.x + (k - 1) * 0.004f, to.y, to.z + (k - 1) * 0.003f)
                        shots += Shot(e, start, spread, 0.01f, 0.15, ev.type, if (k == 2) ev.target else -1, delay = k * 0.07)
                    }
                    // A spear does not fly: the thrust lands a moment later.
                    UnitType.SPEAR -> shots += Shot(null, start, to, 0f, 0.14, ev.type, ev.target)
                    else -> {
                        val e = model(arrowMesh, arrowMat)
                        root.addChild(e)
                        shots += Shot(e, start, to, 0.015f, 0.18, ev.type, ev.target)
                    }
                }
            }
            is Battle.Event.Down -> puff(Vec3(ev.at.x, TOP + 0.02, ev.at.z), small = false)
            is Battle.Event.Breach -> {
                // The gate shakes, stones and dust fall off it, and cracks spread.
                val big = ev.enemy == EnemyType.RAM
                sparks.emit(Vec3(0.0, TOP + 0.05, 0.17), if (big) 24 else 12, 0.15, 0.6, 0.004f)
                chips.emit(Vec3(0.0, TOP + 0.055, 0.168), if (big) 18 else 8, 0.1, 0.6, 0.003f, drift = Vec3(0.0, -0.06, -0.03))
                smoke.emit(Vec3(0.0, TOP + 0.02, 0.165), if (big) 14 else 7, 0.05, 0.8, 0.008f, drift = Vec3(0.0, 0.02, -0.02))
                gateHitAt = clock
                crackGate(b)
            }
            is Battle.Event.UnitDown -> puff(Vec3(ev.slot.at.x, TOP + 0.02, ev.slot.at.z), small = false)
            is Battle.Event.Flooded -> {
                // The river spreads over the low fields.
                val r = FLOOD_AREA
                flood = model(MeshResource.createBox(Vector3(r[2] - r[0], 0.01f, r[3] - r[1]), 0f), riverMat).apply {
                    setPos(Vector3((r[0] + r[2]) / 2, TOP + 0.001f, (r[1] + r[3]) / 2))
                    transform().setScaleVector(Vector3(1f, 0.05f, 1f))
                }.also { land.addChild(it) }
            }
            is Battle.Event.StratagemUsed -> stratagem(ev.s, b)
            else -> Unit
        }
        sync(b)
    }

    private fun stratagem(s: Stratagem, b: Battle) {
        shoutAt = clock
        when (s) {
            Stratagem.FIRE_ROAD -> {
                // 火烧博望: the road burns for six seconds, black smoke over it.
                val path = b.level.paths[b.level.fireRoad]
                var acc = 0.0
                fxs += Fx(6.0, emptyList()) { t, dt ->
                    acc += dt * (if (t < 0.6) 260.0 else 110.0)
                    while (acc >= 1) {
                        acc -= 1
                        val p = path.at(rnd() * path.length)
                        fire.emit(Vec3(p.x + rr(-0.008, 0.008), TOP + 0.006, p.z + rr(-0.008, 0.008)), 1, 0.02, 0.8, rr(0.006, 0.011).toFloat(), drift = Vec3(0.0, 0.07, 0.0))
                        if (rnd() < 0.18) soot.emit(Vec3(p.x, TOP + 0.03, p.z), 1, 0.01, 1.6, 0.009f, drift = Vec3(0.01, 0.05, 0.0))
                    }
                }
            }
            Stratagem.ROAR -> {
                // 据水断桥: 张飞's roar rolls out from the bridge in rings; dust flies off the bridge.
                val at = Vector3(0f, TOP + 0.006f, -0.02f)
                val rings = (0 until 3).map { model(ringMesh, ringGold).apply { setPos(at); setScale(0.001f); enabled = false } }
                rings.forEach { root.addChild(it) }
                smoke.emit(Vec3(0.0, TOP + 0.01, -0.02), 22, 0.1, 0.8, 0.008f, drift = Vec3(0.0, 0.02, 0.0))
                fxs += Fx(1.5, rings) { t, _ ->
                    rings.forEachIndexed { i, r ->
                        val k = ((t - i * 0.25) / 0.9).coerceIn(0.0, 1.0)
                        r.enabled = k > 0 && k < 1
                        r.setScale((0.01 + 0.28 * k).toFloat())
                    }
                }
            }
            Stratagem.EAST_WIND -> {
                // 借东风: wind streaks race across the river, the banners stream, the fleet burns.
                scenery.gust = 3.0
                var acc = 0.0
                fxs += Fx(5.0, emptyList()) { t, dt ->
                    acc += dt * 50
                    while (acc >= 1) {
                        acc -= 1
                        wind.emit(Vec3(0.32, TOP + rr(0.01, 0.08), rr(-0.21, 0.12)), 1, 0.0, 1.4, rr(0.02, 0.04).toFloat(), drift = Vec3(-0.45, 0.0, -0.05))
                    }
                    if (t > 0.5) {
                        for (e in b.enemies) if (e.type == EnemyType.BOAT && rnd() < dt * 30) {
                            fire.emit(Vec3(e.at.x, TOP + 0.015, e.at.z), 1, 0.02, 0.9, 0.009f, drift = Vec3(-0.03, 0.06, 0.0))
                            if (rnd() < 0.3) gold.emit(Vec3(e.at.x, TOP + 0.03, e.at.z), 1, 0.02, 1.5, 0.002f, drift = Vec3(-0.04, 0.06, 0.0))
                        }
                        for (f in scenery.fleetAt) if (rnd() < dt * 22) {
                            fire.emit(Vec3(f.x + rr(-0.012, 0.012), TOP + 0.018, f.z), 1, 0.02, 1.0, 0.01f, drift = Vec3(-0.03, 0.06, 0.0))
                            if (rnd() < 0.25) soot.emit(Vec3(f.x, TOP + 0.04, f.z), 1, 0.01, 1.8, 0.01f, drift = Vec3(-0.04, 0.05, 0.0))
                        }
                    }
                }
            }
            Stratagem.FLOOD -> {
                // 水淹七军: a wall of water rolls down the table toward the city and sinks away.
                val wave = model(MeshResource.createBox(Vector3(W - 0.02f, 1f, 0.05f), 0f), waveMat).apply { enabled = false }
                root.addChild(wave)
                fxs += Fx(2.2, listOf(wave)) { t, _ ->
                    val k = (t / 1.8).coerceIn(0.0, 1.0)
                    val h = 0.035 * sin(PI * k)
                    val z = -0.2 + 0.32 * k
                    wave.enabled = h > 0.001
                    wave.setPos(Vector3(0f, (TOP + h / 2).toFloat(), z.toFloat()))
                    wave.transform().setScaleVector(Vector3(1f, h.coerceAtLeast(0.001).toFloat(), 1f))
                    repeat(3) { water.emit(Vec3(rr(-0.3, 0.3), TOP + h, z + 0.02), 1, 0.04, 0.5, 0.005f, drift = Vec3(0.0, 0.05, 0.08)) }
                }
            }
            Stratagem.EMPTY_CITY -> {
                // 空城计: 诸葛亮 plays on the gate tower; golden ripples and notes spread over the field.
                val rings = (0 until 3).map { model(ringMesh, ringGold).apply { setPos(Vector3(0f, TOP + 0.006f, 0.19f)); enabled = false } }
                rings.forEach { root.addChild(it) }
                var acc = 0.0
                fxs += Fx(Battle.STALL_S, rings) { t, dt ->
                    rings.forEachIndexed { i, r ->
                        val k = ((t + i * 0.8) % 2.4) / 2.4
                        r.enabled = t < Battle.STALL_S - 1.0 || k > 0.2
                        r.setScale((0.02 + 0.42 * k).toFloat())
                    }
                    acc += dt * 4
                    while (acc >= 1) {
                        acc -= 1
                        gold.emit(Vec3(rr(-0.02, 0.02), TOP + 0.11, 0.19), 1, 0.015, 2.5, 0.0032f, drift = Vec3(rr(-0.03, 0.03), 0.025, -0.045))
                    }
                }
            }
            Stratagem.EIGHT_ARRAYS -> {
                // 八阵图: eight stone pillars rise in a ring, a jade circle turns, mist winds through.
                val c = Vector3(0f, TOP, -0.03f)
                val pillars = (0 until 8).map { k ->
                    val a = k * PI / 4
                    model(MeshResource.createBox(Vector3(0.016f, 1f, 0.016f), 0.002f), stoneMat).apply {
                        setPos(Vector3((c.x + 0.15 * cos(a)).toFloat(), TOP, (c.z + 0.15 * sin(a)).toFloat()))
                        transform().setScaleVector(Vector3(1f, 0.001f, 1f))
                    }
                }
                val ring = model(ringMesh, ringJade).apply { setPos(Vector3(c.x, TOP + 0.004f, c.z)); setScale(0.17f) }
                val nodes = pillars + ring
                nodes.forEach { root.addChild(it) }
                fxs += Fx(Battle.MAZE_S + 0.6, nodes) { t, dt ->
                    val rise = when {
                        t < 0.6 -> t / 0.6
                        t > Battle.MAZE_S -> 1 - (t - Battle.MAZE_S) / 0.6
                        else -> 1.0
                    }.coerceIn(0.0, 1.0)
                    for (p in pillars) {
                        val pos = p.transform().position
                        val h = 0.05 * rise
                        p.setPos(Vector3(pos.x, (TOP + h / 2).toFloat(), pos.z))
                        p.transform().setScaleVector(Vector3(1f, h.coerceAtLeast(0.001).toFloat(), 1f))
                    }
                    ring.transform().eulerAngles = EulerAngles(0f, (t * 40).toFloat(), 0f)
                    ring.enabled = rise > 0.05
                    if (rnd() < dt * 20) {
                        val a = t * 1.5 + rnd() * 0.6
                        smoke.emit(Vec3(c.x + 0.15 * cos(a), TOP + 0.012, c.z + 0.15 * sin(a)), 1, 0.01, 1.4, 0.01f, drift = Vec3(-sin(a) * 0.04, 0.005, cos(a) * 0.04))
                    }
                }
            }
        }
    }

    fun puff(at: Vec3, small: Boolean) {
        smoke.emit(at, if (small) 8 else 14, if (small) 0.04 else 0.07, 0.9, if (small) 0.006f else 0.009f, drift = Vec3(0.0, 0.03, 0.0))
        if (!small) sparks.emit(at, 8, 0.08, 0.6, 0.003f)
    }

    /** Where the slot [id] is (root frame), for the unit panel. */
    fun slotSpot(id: Int): Vector3? = slotViews[id]?.transform()?.position

    companion object {
        /** Board middle, this far in front of the player (nearer felt in the lap on the device). */
        const val TABLE_Z = 0.75f
        const val TABLE_H = 0.75f
        const val W = 0.64f
        const val D = 0.44f
        const val TRAY_D = 0.08f
        const val TOP = 0.027f
        const val WALL_H = 0.03f
        /** A dragged unit snaps to a free slot within this distance (meters). */
        const val SNAP = 0.06

        val UNIT_TINT = mapOf(
            UnitType.ARCHER to color(0.8f, 0.2f, 0.15f), UnitType.SPEAR to color(0.75f, 0.25f, 0.2f),
            UnitType.CATAPULT to color(0.5f, 0.35f, 0.2f), UnitType.CROSSBOW to color(0.85f, 0.3f, 0.2f), UnitType.CHEVAL to color(0.45f, 0.3f, 0.18f),
        )

        val GENERALS = mapOf(
            Stratagem.FIRE_ROAD to "zhuge", Stratagem.ROAR to "zhangfei", Stratagem.EAST_WIND to "zhouyu",
            Stratagem.FLOOD to "guanyu", Stratagem.EMPTY_CITY to "zhuge", Stratagem.EIGHT_ARRAYS to "zhuge",
        )

        /** Water on each battle's table: rectangles x0, z0, x1, z1 (board frame). */
        val WATER = mapOf(
            "changban" to listOf(floatArrayOf(-0.32f, -0.045f, 0.32f, 0.0f)),
            "chibi" to listOf(floatArrayOf(-0.32f, -0.22f, 0.32f, -0.02f)),
            "fancheng" to listOf(floatArrayOf(-0.32f, -0.2f, 0.32f, -0.16f)),
        )

        /** Where the water spreads when it rises (樊城): the low fields. */
        val FLOOD_AREA = floatArrayOf(-0.27f, -0.16f, 0.1f, -0.04f)
    }
}

/** Material swaps on loaded figures. */
object Assets {
    fun setMaterials(e: Entity, m: Material) {
        e.components[com.pico.spatial.core.ecs.ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
        e.getChildren().forEach { setMaterials(it, m) }
    }
}
