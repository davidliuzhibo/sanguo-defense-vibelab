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
import kotlin.math.atan2
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
    private val water = Sparks("water", 160, sphere, glow(color(0.45f, 0.7f, 1f, 0.8f)).shared()).also { root.addChild(it.entity) }
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

    private class Shot(val e: Entity, val from: Vector3, val to: Vector3, val arc: Float, val life: Double) {
        var t = 0.0
    }

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
        // Grass patches away from the roads.
        for (i in 0 until 14) {
            val x = -0.28f + (i % 7) * 0.093f
            val z = -0.18f + (i / 7) * 0.2f
            land.addChild(model(MeshResource.createCylinder(0.003f, 0.03f + (i % 3) * 0.008f), grass).apply { setPos(Vector3(x, TOP, z)) })
        }
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
        land.addChild(model(MeshResource.createBox(Vector3(0.06f, 0.05f, 0.04f), 0.003f), pbr(color(0.55f, 0.18f, 0.14f))).apply { setPos(Vector3(0f, TOP + 0.045f, 0.19f)) })
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
        UnitType.CHEVAL -> null
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
        }
        for ((id, u) in b.units) {
            val existing = unitViews[id]
            if (existing == null) {
                val holder = Entity().apply { setPos(slotViews[id]!!.transform().position) }
                holder.addChild(figure(u.type))
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

    fun frame(b: Battle, dt: Double) {
        clock += dt
        // Enemies: one figure each, facing the way they walk.
        val alive = b.enemies.toSet()
        for (e in enemyViews.keys.filter { it !in alive }) root.removeChild(enemyViews.remove(e)!!)
        for (e in b.enemies) {
            val v = enemyViews.getOrPut(e) { enemyFigure(e.type).also { root.addChild(it) } }
            val path = b.level.paths[e.path]
            val here = e.at
            val ahead = path.at(e.s + 0.01)
            val bob = if (e.held != null || b.stall > 0) 0.0 else kotlin.math.abs(sin(clock * 10 + e.serial)) * 0.003
            v.setPos(Vector3(here.x.toFloat(), (TOP + bob + if (path.water) 0.002 else 0.0).toFloat(), here.z.toFloat()))
            v.transform().eulerAngles = EulerAngles(0f, Math.toDegrees(atan2(ahead.x - here.x, ahead.z - here.z)).toFloat() + (if (b.maze > 0) (clock * 360).toFloat() else 0f), 0f)
        }
        // Shots in flight.
        val it = shots.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.t += dt
            val k = (s.t / s.life).toFloat().coerceAtMost(1f)
            val y = s.from.y + (s.to.y - s.from.y) * k + s.arc * 4 * k * (1 - k)
            s.e.setPos(Vector3(s.from.x + (s.to.x - s.from.x) * k, y, s.from.z + (s.to.z - s.from.z) * k))
            if (k >= 1f) {
                root.removeChild(s.e)
                it.remove()
            }
        }
        general?.let { g -> if (b.stratagemReady) g.transform().eulerAngles = EulerAngles(0f, (sin(clock * 3) * 20).toFloat(), 0f) }
        smoke.update(dt, 1.5); sparks.update(dt, 1.0); fire.update(dt, 0.8); water.update(dt, 1.0)
        flood?.let { f -> f.transform().setScaleVector(Vector3(1f, (f.transform().scaleVector.y + dt * 2).toFloat().coerceAtMost(1f), 1f)) }
    }

    private fun enemyFigure(t: EnemyType): Entity = when (t) {
        EnemyType.FOOT -> piece("foot", 0.045f)
        EnemyType.CAVALRY -> piece("cavalry", 0.05f)
        EnemyType.RAM -> piece("ram", 0.05f)
        EnemyType.BOAT -> null
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
                val stone = ev.type == com.vrgames.sanguo.core.UnitType.CATAPULT
                val e = if (stone) model(sphere, stoneMat).apply { setScale(0.006f) } else model(MeshResource.createBox(Vector3(0.002f, 0.002f, 0.02f), 0f), arrowMat)
                if (!stone) e.transform().eulerAngles = EulerAngles(0f, Math.toDegrees(atan2((to.x - start.x).toDouble(), (to.z - start.z).toDouble())).toFloat(), 0f)
                root.addChild(e)
                shots += Shot(e, start, to, if (stone) 0.08f else 0.015f, if (stone) 0.6 else 0.18)
            }
            is Battle.Event.Down -> puff(Vec3(ev.at.x, TOP + 0.02, ev.at.z), small = false)
            is Battle.Event.Breach -> sparks.emit(Vec3(0.0, TOP + 0.05, 0.19), 20, 0.15, 0.6, 0.004f)
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
        when (s) {
            Stratagem.FIRE_ROAD -> {
                val path = b.level.paths[b.level.fireRoad]
                var d = 0.0
                while (d < path.length) {
                    val p = path.at(d)
                    fire.emit(Vec3(p.x, TOP + 0.01, p.z), 6, 0.05, 1.6, 0.008f, drift = Vec3(0.0, 0.05, 0.0))
                    d += 0.03
                }
            }
            Stratagem.ROAR -> for (k in 0 until 24) sparks.emit(Vec3(0.0, TOP + 0.03, -0.02), 6, 0.25, 0.8, 0.005f)
            Stratagem.EAST_WIND -> for (e in b.enemies) if (e.type == EnemyType.BOAT) fire.emit(Vec3(e.at.x, TOP + 0.02, e.at.z), 30, 0.06, 1.8, 0.01f, drift = Vec3(0.02, 0.05, 0.0))
            Stratagem.FLOOD -> for (k in 0 until 20) water.emit(Vec3(-0.3 + k * 0.03, TOP + 0.01, -0.1), 8, 0.08, 1.2, 0.008f, drift = Vec3(0.0, 0.0, 0.05))
            Stratagem.EMPTY_CITY -> sparks.emit(Vec3(0.0, TOP + 0.1, 0.19), 60, 0.08, 3.0, 0.004f, drift = Vec3(0.0, 0.03, -0.03))
            Stratagem.EIGHT_ARRAYS -> for (e in b.enemies) smoke.emit(Vec3(e.at.x, TOP + 0.02, e.at.z), 10, 0.04, 2.0, 0.01f)
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
