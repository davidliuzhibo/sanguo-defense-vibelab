package com.vrgames.sanguo.core

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A point on the sand table (meters, board frame: +x right, +z toward the player, the city at the near edge). */
data class P(val x: Double, val z: Double) {
    fun dist(o: P) = hypot(x - o.x, z - o.z)
}

/** A road the enemy walks, from the far edge to the city gate; [water] roads are for boats. */
class Path(val points: List<P>, val water: Boolean = false) {
    private val cum = DoubleArray(points.size).also { c -> for (i in 1 until points.size) c[i] = c[i - 1] + points[i].dist(points[i - 1]) }
    val length get() = cum.last()

    /** The point [s] meters along the road. */
    fun at(s: Double): P {
        val d = s.coerceIn(0.0, length)
        var i = 1
        while (i < points.size - 1 && cum[i] < d) i++
        val seg = cum[i] - cum[i - 1]
        val t = if (seg < 1e-9) 0.0 else (d - cum[i - 1]) / seg
        val a = points[i - 1]
        val b = points[i]
        return P(a.x + (b.x - a.x) * t, a.z + (b.z - a.z) * t)
    }

    /** How far along the road the point nearest to [p] is, and how far [p] is from the road. */
    fun nearest(p: P): Pair<Double, Double> {
        var best = 0.0 to Double.MAX_VALUE
        var s = 0.0
        while (s <= length) {
            val d = at(s).dist(p)
            if (d < best.second) best = s to d
            s += 0.002
        }
        return best
    }
}

enum class SlotKind { WALL, FIELD, ROAD }

/** Where a unit may stand; [low] slots are flooded when the water rises (樊城). */
data class Slot(val id: Int, val at: P, val kind: SlotKind, val low: Boolean = false)

enum class UnitType(val label: String, val cost: Int, val kinds: Set<SlotKind>, val range: Double, val damage: Double, val interval: Double, val note: String) {
    ARCHER("弓兵", 60, setOf(SlotKind.WALL, SlotKind.FIELD), 0.17, 1.5, 1.0, "远程，一次射一个，射程中等"),
    SPEAR("枪兵", 50, setOf(SlotKind.ROAD), 0.03, 1.6, 0.7, "站在路上挡住敌人，挡住时一起打"),
    CATAPULT("投石车", 120, setOf(SlotKind.WALL, SlotKind.FIELD), 0.3, 4.0, 3.5, "射程最远，砸中一片，但很慢"),
    CHEVAL("拒马", 40, setOf(SlotKind.ROAD), 0.045, 0.0, 1.0, "放在路上，经过的敌人走得慢一半"),
    CROSSBOW("连弩", 100, setOf(SlotKind.WALL, SlotKind.FIELD), 0.12, 0.8, 0.3, "近距离连发，第三关起可用"),
}

enum class EnemyType(val label: String, val hp: Double, val speed: Double, val bounty: Int, val gateDamage: Int) {
    FOOT("步兵", 9.0, 0.022, 8, 1),
    CAVALRY("骑兵", 7.0, 0.045, 10, 1),
    RAM("冲车", 45.0, 0.011, 30, 5),
    BOAT("战船", 18.0, 0.018, 15, 1),
    /** v0.3: big shields — arrows and bolts do half; stones and spears full. */
    SHIELD("盾兵", 14.0, 0.019, 12, 1),
    /** v0.3: the enemy general — slow and tough; those near him take less (his banner steadies them). */
    GENERAL("敌将", 80.0, 0.015, 60, 6),
}

enum class Stratagem(val label: String, val general: String, val story: String) {
    FIRE_ROAD("火烧博望", "诸葛亮", "一条路上燃起大火，路上的敌军都被烧退"),
    ROAR("据水断桥", "张飞", "张飞在桥头大喝一声，桥上和桥边的敌军吓得往回跑"),
    EAST_WIND("借东风", "周瑜", "东风吹起，火船顺风烧向江上的战船"),
    FLOOD("水淹七军", "关羽", "放开堤坝，大水冲退所有敌军"),
    EMPTY_CITY("空城计", "诸葛亮", "城门大开，琴声悠悠，敌军疑有埋伏，停下二十秒不敢前进"),
    EIGHT_ARRAYS("八阵图", "诸葛亮", "石阵迷阵，所有敌军原地打转十秒，并受到伤害"),
}

/** A group in a wave: [count] of [type] on road [path], one every [every] seconds, starting [delay] s into the wave. */
data class Group(val type: EnemyType, val count: Int, val path: Int, val every: Double = 1.6, val delay: Double = 0.0)

/**
 * The rules of one battle, free of rendering: grain, units on slots, the waves walking their roads,
 * arrows and stones (instant), the gate, the stratagem. Time advances only through [step].
 */
class Battle(val level: Level) {
    init {
        require(level.slots.map { it.id }.toSet().size == level.slots.size)
    }

    inner class Unit(val type: UnitType, val slot: Slot) {
        var rank = 1
        var cool = 0.0
        var hp = SPEAR_HP
        val range get() = type.range * (1 + 0.1 * (rank - 1))
        val damage get() = type.damage * (1 + 0.5 * (rank - 1))
        /** Grain spent on it, for the refund. */
        var spent = type.cost
    }

    inner class Enemy(val type: EnemyType, val path: Int, val serial: Int) {
        var s = 0.0
        var hp = type.hp * level.hpScale * (if (level.endless) 1 + 0.06 * (wave - 1) else 1.0)
        var slowed = 0.0
        var held: Unit? = null
        var fear = 0.0
        val at get() = level.paths[path].at(s)
        val gone get() = hp <= 0
    }

    sealed interface Event {
        /** [target] is the serial of the enemy aimed at (-1: none). */
        data class Shot(val from: Slot, val to: P, val type: UnitType, val target: Int = -1) : Event
        data class Down(val enemy: EnemyType, val at: P) : Event
        data class Breach(val enemy: EnemyType, val damage: Int) : Event
        data class UnitDown(val slot: Slot) : Event
        data class WaveStart(val wave: Int) : Event
        data class WaveClear(val wave: Int) : Event
        data class Flooded(val slots: List<Slot>) : Event
        data class StratagemUsed(val s: Stratagem) : Event
        /** v0.3: the general's skill, from where he stands. */
        data class Skill(val hero: Hero, val at: P) : Event
        /** v0.3: two units became one of the next rank, at [slot]. */
        data class Merged(val slot: Slot) : Event
        data object Won : Event
        data object Lost : Event
    }

    val units = HashMap<Int, Unit>()
    val enemies = ArrayList<Enemy>()
    var grain = level.grain; private set
    var spent = 0; private set
    var gate = GATE_HP; private set
    /** The wave on now (1-based), or the last one finished between waves; 0 before the first. */
    var wave = 0; private set
    var inWave = false; private set
    var charge = level.startCharge; private set
    var stall = 0.0; private set
    var maze = 0.0; private set
    var flooded = false; private set
    var won = false; private set
    var lost = false; private set
    private var waveClock = 0.0
    private var spawned = IntArray(0)
    private var serial = 0
    private var clock = 0.0
    private val fires = ArrayList<Pair<Int, Double>>()

    val stratagemReady get() = charge >= 1.0

    // ---- The general (v0.3) ----

    val hero: Hero? get() = level.hero
    var heroSlot: Int? = null; private set
    /** Seconds until his skill is ready again. */
    var heroCool = 0.0; private set
    private var heroHit = 0.0
    val heroReady get() = heroSlot != null && heroCool <= 0 && !over
    /** Burning ground from 诸葛亮's 火计: where, and seconds left. */
    private val zones = ArrayList<Pair<P, Double>>()

    /** Puts the general on [slotId] (the wall or a field; once; no grain). Why not, or null. */
    fun placeHero(slotId: Int): String? {
        val h = hero ?: return "这一关没有随军大将。"
        val sl = slot(slotId)
        return when {
            over -> "这一仗已经结束了。"
            heroSlot != null -> "${h.label}已经上阵了。"
            slotId in units -> "这里已经有兵了。"
            sl.kind == SlotKind.ROAD -> "${h.label}要站在城墙或田边。"
            sl.low && flooded -> "这里已经被水淹了。"
            else -> { heroSlot = slotId; heroCool = Hero.FIRST; null }
        }
    }

    /** The general's skill, if it is ready. */
    fun useSkill(): Boolean {
        val h = hero ?: return false
        val at = heroSlot?.let { slot(it).at } ?: return false
        if (!heroReady) return false
        heroCool = Hero.COOLDOWN
        val near = enemies.filter { it.at.dist(at) <= Hero.SKILL_REACH }
        when (h) {
            Hero.ZHANG -> near.forEach { it.fear = 3.5; it.held = null }
            Hero.GUAN -> near.forEach { it.hp -= 18.0; it.s = max(0.0, it.s - 0.04); it.held = null }
            Hero.ZHUGE -> zones += at to 5.0
            Hero.ZHOU -> {
                enemies.filter { it.type == EnemyType.BOAT }.forEach { it.hp -= 25.0 }
                near.forEach { it.hp -= 8.0 }
            }
        }
        pending += Event.Skill(h, at)
        return true
    }

    // ---- Merging and moving placed units (v0.3) ----

    /** Two of the same kind and rank: the one on [from] joins the one on [to], which goes up a rank (no grain). */
    fun merge(from: Int, to: Int): String? {
        val a = units[from] ?: return "这里没有兵。"
        val b = units[to] ?: return "那里没有兵。"
        return when {
            from == to -> "要拖到另一个兵身上。"
            a.type != b.type -> "只有同一种兵才能合并：${a.type.label}和${b.type.label}不行。"
            a.rank != b.rank -> "只有同一级的兵才能合并（${a.rank} 级和 ${b.rank} 级）。"
            b.rank >= 3 -> "${b.type.label}已经是最高级了。"
            else -> {
                units.remove(from)
                enemies.forEach { if (it.held === a) it.held = null }
                b.rank++
                b.spent += a.spent
                if (b.type == UnitType.SPEAR) b.hp = SPEAR_HP * (1 + 0.5 * (b.rank - 1))
                pending += Event.Merged(b.slot)
                null
            }
        }
    }

    /** Moves the unit on [from] to the empty slot [to] (a slot it may stand on). */
    fun relocate(from: Int, to: Int): String? {
        val a = units[from] ?: return "这里没有兵。"
        val sl = slot(to)
        return when {
            to in units || to == heroSlot -> "那里已经有人了。"
            sl.kind !in a.type.kinds -> "${a.type.label}不能放在那里。"
            sl.low && flooded -> "那里已经被水淹了。"
            else -> {
                units.remove(from)
                enemies.forEach { if (it.held === a) it.held = null }
                val n = Unit(a.type, sl)
                n.rank = a.rank; n.spent = a.spent; n.hp = a.hp
                units[to] = n
                null
            }
        }
    }

    /** Fills the stratagem bar (the test hook, for pictures of the stratagems). */
    fun fillCharge() { charge = 1.0 }
    val waves get() = level.waves.size
    val over get() = won || lost
    val available get() = level.units

    fun slot(id: Int) = level.slots.first { it.id == id }

    /** Why [type] may not go on [slotId] now, or null if it may (and it is placed). */
    fun place(type: UnitType, slotId: Int): String? {
        val sl = slot(slotId)
        return when {
            over -> "这一仗已经结束了。"
            type !in level.units -> "这一关还不能用${type.label}。"
            slotId in units || slotId == heroSlot -> "这里已经有兵了。"
            sl.low && flooded -> "这里已经被水淹了。"
            sl.kind !in type.kinds -> when (sl.kind) {
                SlotKind.ROAD -> "路上只能放枪兵和拒马。"
                else -> "${type.label}要放在路上。"
            }
            grain < type.cost -> "粮草不够：${type.label}要 ${type.cost}，还剩 $grain。"
            else -> {
                grain -= type.cost
                spent += type.cost
                units[slotId] = Unit(type, sl)
                null
            }
        }
    }

    fun upgradeCost(slotId: Int) = units[slotId]?.let { (it.type.cost * 0.8).toInt() * it.rank }

    fun upgrade(slotId: Int): String? {
        val u = units[slotId] ?: return "这里没有兵。"
        if (u.rank >= 3) return "${u.type.label}已经是最高级了。"
        val c = upgradeCost(slotId)!!
        if (grain < c) return "粮草不够：升级要 $c，还剩 $grain。"
        grain -= c
        spent += c
        u.spent += c
        u.rank++
        if (u.type == UnitType.SPEAR) u.hp = SPEAR_HP * (1 + 0.5 * (u.rank - 1))
        return null
    }

    fun sell(slotId: Int): Int {
        val u = units.remove(slotId) ?: return 0
        val back = (u.spent * 0.6).toInt()
        grain += back
        enemies.forEach { if (it.held === u) it.held = null }
        return back
    }

    /** Sends the next wave. False if one is still on or none is left. */
    fun startWave(): Boolean {
        if (inWave || over || wave >= level.waves.size) return false
        wave++
        inWave = true
        waveClock = 0.0
        spawned = IntArray(level.waves[wave - 1].size)
        return true
    }

    fun useStratagem(): Boolean {
        if (!stratagemReady || over) return false
        charge = 0.0
        when (level.stratagem) {
            Stratagem.FIRE_ROAD -> fires += level.fireRoad to 6.0
            Stratagem.ROAR -> for (e in enemies) if (level.bridge?.let { (a, b) -> e.path == 0 && e.s in a - 0.05..b + 0.05 } == true) e.fear = 3.0
            Stratagem.EAST_WIND -> for (e in enemies) if (e.type == EnemyType.BOAT) e.hp -= 40.0
            Stratagem.FLOOD -> for (e in enemies) {
                e.hp -= 12.0
                e.s = max(0.0, e.s - 0.08)
                e.held = null
            }
            Stratagem.EMPTY_CITY -> stall = STALL_S
            Stratagem.EIGHT_ARRAYS -> maze = MAZE_S
        }
        pending += Event.StratagemUsed(level.stratagem)
        return true
    }

    private val pending = ArrayList<Event>()

    /** For each road slot, where along each road it stands and how far off it (worked out once). */
    private val roadSpot = level.slots.filter { it.kind == SlotKind.ROAD }.associate { sl -> sl.id to level.paths.map { it.nearest(sl.at) } }

    fun step(dt: Double): List<Event> {
        val out = ArrayList<Event>(pending)
        pending.clear()
        if (over) return out
        clock += dt
        if (inWave) spawn(dt, out)
        if (stall > 0) stall -= dt
        if (maze > 0) maze -= dt
        // Fire on a road burns whoever walks it.
        fires.replaceAll { (road, left) -> road to left - dt }
        fires.removeAll { it.second <= 0 }
        for ((road, _) in fires) for (e in enemies) if (e.path == road) e.hp -= 3.0 * dt
        if (maze > 0) for (e in enemies) e.hp -= 1.0 * dt
        if (heroCool > 0) heroCool -= dt
        zones.replaceAll { (p, left) -> p to left - dt }
        zones.removeAll { it.second <= 0 }
        for ((p, _) in zones) for (e in enemies) if (e.at.dist(p) <= Hero.SKILL_REACH) e.hp -= 4.0 * dt
        move(dt, out)
        fight(dt, out)
        for (e in enemies.filter { it.gone }) {
            out += Event.Down(e.type, e.at)
            grain += e.type.bounty
            charge = min(1.0, charge + level.chargePerKill)
        }
        enemies.removeAll { it.gone }
        // The water rises after the second wave (樊城): low slots go under, their units come home.
        if (level.floodAfterWave > 0 && !flooded && wave >= level.floodAfterWave && !inWave) {
            flooded = true
            val under = level.slots.filter { it.low }
            for (sl in under) sell(sl.id)
            if (heroSlot?.let { slot(it).low } == true) heroSlot = null
            out += Event.Flooded(under)
        }
        if (inWave && enemies.isEmpty() && spawned.withIndex().all { (i, n) -> n >= level.waves[wave - 1][i].count }) {
            inWave = false
            out += Event.WaveClear(wave)
            if (wave == level.waves.size) {
                won = true
                out += Event.Won
            }
        }
        return out
    }

    private fun spawn(dt: Double, out: MutableList<Event>) {
        if (waveClock == 0.0) out += Event.WaveStart(wave)
        waveClock += dt
        val groups = level.waves[wave - 1]
        for ((i, g) in groups.withIndex()) {
            val due = ((waveClock - g.delay) / g.every).toInt() + 1
            while (spawned[i] < g.count && spawned[i] < due && waveClock >= g.delay) {
                enemies += Enemy(g.type, g.path, serial++)
                spawned[i]++
            }
        }
    }

    private fun move(dt: Double, out: MutableList<Event>) {
        val gateHit = ArrayList<Enemy>()
        for (e in enemies) {
            if (stall > 0 || maze > 0) continue
            if (e.fear > 0) {
                e.fear -= dt
                e.s = max(0.0, e.s - e.type.speed * 1.5 * dt)
                continue
            }
            // A spearman on the road holds those who reach him (three at a time) while he stands.
            val holder = e.held?.takeIf { units[it.slot.id] === it }
            if (holder != null) continue
            e.held = null
            val path = level.paths[e.path]
            val slow = units.values.any { it.type == UnitType.CHEVAL && it.slot.at.dist(e.at) < it.range }
            val speed = e.type.speed * (if (slow) 0.5 else 1.0)
            val next = e.s + speed * dt
            if (!path.water) {
                val block = units.values.firstOrNull { u ->
                    u.type == UnitType.SPEAR && e.type != EnemyType.BOAT &&
                        roadSpot[u.slot.id]!![e.path].let { (s, d) -> d < 0.02 && e.s <= s && next >= s - 0.01 } &&
                        enemies.count { it.held === u } < 3
                }
                if (block != null) {
                    e.held = block
                    continue
                }
            }
            e.s = next
            if (e.s >= path.length) gateHit += e
        }
        for (e in gateHit) {
            gate -= e.type.gateDamage
            out += Event.Breach(e.type, e.type.gateDamage)
            enemies.remove(e)
        }
        if (gate <= 0 && !lost) {
            gate = 0
            lost = true
            out += Event.Lost
        }
    }

    private fun fight(dt: Double, out: MutableList<Event>) {
        for (u in units.values.toList()) {
            if (u.type == UnitType.CHEVAL) continue
            // A spearman being hit by those he holds.
            if (u.type == UnitType.SPEAR) {
                val holding = enemies.count { it.held === u }
                u.hp -= holding * 0.8 * dt
                if (u.hp <= 0) {
                    units.remove(u.slot.id)
                    enemies.forEach { if (it.held === u) it.held = null }
                    out += Event.UnitDown(u.slot)
                    continue
                }
            }
            u.cool -= dt
            if (u.cool > 0) continue
            val inReach = enemies.filter { !it.gone && it.at.dist(u.slot.at) <= u.range && (u.type != UnitType.CATAPULT || it.at.dist(u.slot.at) > 0.05) }
            // The one furthest along its road, nearest the gate.
            val target = (if (u.type == UnitType.SPEAR) inReach.filter { it.held === u }.ifEmpty { inReach } else inReach)
                .maxByOrNull { it.s / level.paths[it.path].length } ?: continue
            u.cool = u.type.interval
            out += Event.Shot(u.slot, target.at, u.type, target.serial)
            if (u.type == UnitType.CATAPULT) {
                val c = target.at
                for (e in enemies) if (e.at.dist(c) < SPLASH) e.hp -= u.damage * shielded(e, u.type)
            } else {
                target.hp -= u.damage * shielded(target, u.type)
            }
        }
        // The general strikes the nearest within his reach.
        val hs = heroSlot
        if (hs != null) {
            heroHit -= dt
            if (heroHit <= 0) {
                val at = slot(hs).at
                val t = enemies.filter { !it.gone && it.at.dist(at) <= Hero.REACH }.maxByOrNull { it.s / level.paths[it.path].length }
                if (t != null) {
                    heroHit = Hero.INTERVAL
                    t.hp -= Hero.DAMAGE * shielded(t, null)
                    out += Event.Shot(slot(hs), t.at, UnitType.SPEAR, t.serial)
                }
            }
        }
    }

    /** What share of a blow lands: shields stop half the arrows and bolts; the enemy general's banner steadies those near him. */
    private fun shielded(e: Enemy, by: UnitType?): Double {
        var k = 1.0
        if (e.type == EnemyType.SHIELD && (by == UnitType.ARCHER || by == UnitType.CROSSBOW)) k *= 0.5
        if (e.type != EnemyType.GENERAL && enemies.any { it.type == EnemyType.GENERAL && !it.gone && it.at.dist(e.at) < BANNER }) k *= 0.7
        return k
    }

    /** Stars: won; the gate at half or more; within the grain par. */
    fun stars() = if (!won) 0 else 1 + (if (gate >= GATE_HP / 2) 1 else 0) + (if (spent <= level.grainPar) 1 else 0)

    companion object {
        const val GATE_HP = 20
        const val SPEAR_HP = 14.0
        const val SPLASH = 0.05
        const val STALL_S = 20.0
        const val MAZE_S = 10.0
        /** Within this of the enemy general, his men take less. */
        const val BANNER = 0.08
    }
}
