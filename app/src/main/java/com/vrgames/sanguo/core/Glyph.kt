package com.vrgames.sanguo.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 手势施计 (v0.3, PICO 1116: 「建议再丰富一下玩法」): a stratagem is cast by drawing its sign in the air with a pinched
 * hand. Three signs, simple and far apart: a stroke (一, cut the bridge / set the road alight), a wave (～, water
 * and wind) and a ring (○, the city and the stone maze). The hand's path is taken in a plane (x across, y up),
 * in metres; anything else is not recognised.
 */
enum class Glyph(val label: String, val how: String) {
    LINE("一", "一笔横划"),
    WAVE("～", "一道波浪（上下起伏地横着画）"),
    RING("○", "一个圈"),
    ;

    companion object {
        /** The sign each stratagem is cast with. */
        fun of(s: Stratagem): Glyph = when (s) {
            Stratagem.FIRE_ROAD, Stratagem.ROAR -> LINE
            Stratagem.EAST_WIND, Stratagem.FLOOD -> WAVE
            Stratagem.EMPTY_CITY, Stratagem.EIGHT_ARRAYS -> RING
        }

        /** Too short a path is no sign at all (a pinch that hardly moved). */
        const val MIN_LENGTH = 0.12

        /** Reads [pts] (x, y) as a sign, or null. */
        fun classify(pts: List<Pair<Double, Double>>): Glyph? {
            if (pts.size < 4) return null
            var len = 0.0
            for (i in 1 until pts.size) len += hypot(pts[i].first - pts[i - 1].first, pts[i].second - pts[i - 1].second)
            if (len < MIN_LENGTH) return null
            val xs = pts.map { it.first }
            val ys = pts.map { it.second }
            val w = xs.max() - xs.min()
            val h = ys.max() - ys.min()
            val size = max(w, h)
            val closed = hypot(pts.last().first - pts.first().first, pts.last().second - pts.first().second)
            // A ring: it comes back near its start, is roughly as tall as wide, and turns all the way round.
            val turn = totalTurn(pts)
            if (closed < 0.35 * size && min(w, h) > 0.45 * size && abs(turn) > 4.7) return RING
            // A wave: wider than tall, crossing its middle line up and down at least three times.
            // ...and going steadily one way across (a scribble back and forth on the spot is not a wave).
            var back = 0.0
            val dir = Math.signum(pts.last().first - pts.first().first)
            for (i in 1 until pts.size) { val dx = pts[i].first - pts[i - 1].first; if (dx * dir < 0) back += -dx * dir }
            if (w > 1.3 * h && h > 0.06 * w && crossings(pts) >= 3 && abs(pts.last().first - pts.first().first) > 0.7 * w && back < 0.25 * w) return WAVE
            // A stroke: nearly straight (the path hardly longer than the distance it covers).
            val span = hypot(pts.last().first - pts.first().first, pts.last().second - pts.first().second)
            if (span > 0.85 * len) return LINE
            return null
        }

        /** How far the path turns in all (radians, signed). */
        private fun totalTurn(pts: List<Pair<Double, Double>>): Double {
            var sum = 0.0
            var last: Double? = null
            for (i in 1 until pts.size) {
                val dx = pts[i].first - pts[i - 1].first
                val dy = pts[i].second - pts[i - 1].second
                if (hypot(dx, dy) < 1e-4) continue
                val a = atan2(dy, dx)
                last?.let { var d = a - it; while (d > Math.PI) d -= 2 * Math.PI; while (d < -Math.PI) d += 2 * Math.PI; sum += d }
                last = a
            }
            return sum
        }

        /** How often the path crosses the line through its middle height. */
        private fun crossings(pts: List<Pair<Double, Double>>): Int {
            val mid = (pts.maxOf { it.second } + pts.minOf { it.second }) / 2
            val band = (pts.maxOf { it.second } - pts.minOf { it.second }) * 0.15
            var side = 0
            var n = 0
            for (p in pts) {
                val s = if (p.second > mid + band) 1 else if (p.second < mid - band) -1 else 0
                if (s != 0 && side != 0 && s != side) n++
                if (s != 0) side = s
            }
            return n
        }
    }
}

/**
 * The general who fights beside the troops (v0.3): put on the wall or a field once per battle, he strikes those
 * near on his own, and his skill (pinch him when it is ready) turns a fight.
 */
enum class Hero(val label: String, val skill: String, val story: String, val model: String) {
    ZHANG("张飞", "喝断长坂", "一声大喝如雷，身边的敌军吓得掉头就跑", "zhangfei"),
    GUAN("关羽", "青龙偃月", "大刀一挥，身边的敌军一齐被扫退", "guanyu"),
    ZHUGE("诸葛亮", "火计", "羽扇一指，身边的地上燃起大火", "zhuge"),
    ZHOU("周瑜", "火船", "火船顺风而下，江上的战船和岸边的敌军都被烧到", "zhouyu"),
    ;

    companion object {
        /** Reach of his own blows and of his skill (metres on the table). */
        const val REACH = 0.12
        const val SKILL_REACH = 0.14
        const val DAMAGE = 1.4
        const val INTERVAL = 0.8
        /** Seconds between skills; the first is ready this long after he is put down. */
        const val COOLDOWN = 25.0
        const val FIRST = 8.0
    }
}
