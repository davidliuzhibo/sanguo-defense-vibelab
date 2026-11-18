package com.vrgames.sanguo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** v0.3: signs drawn in the air, the general, merging, shields, 无尽. */
class V03Test {
    private fun ring() = (0..40).map { val a = 2 * PI * it / 40; 0.12 * cos(a) to 0.12 * sin(a) }
    private fun wave() = (0..60).map { val x = it * 0.006; x to 0.04 * sin(x / 0.36 * 4 * PI) }
    private fun line() = (0..20).map { it * 0.015 to it * 0.001 }

    @Test
    fun signsAreTold() {
        assertEquals(Glyph.RING, Glyph.classify(ring()))
        assertEquals(Glyph.WAVE, Glyph.classify(wave()))
        assertEquals(Glyph.LINE, Glyph.classify(line()))
        assertNull("a pinch that hardly moved is no sign", Glyph.classify((0..10).map { it * 0.001 to 0.0 }))
        // A scribble back and forth on the spot is none of them.
        assertNull(Glyph.classify((0..30).map { (if (it % 2 == 0) 0.0 else 0.08) to (if (it % 4 < 2) 0.0 else 0.06) }))
    }

    @Test
    fun everyStratagemHasASign() {
        for (s in Stratagem.entries) assertNotNull(Glyph.of(s))
    }

    @Test
    fun theGeneralGoesOnceAndHisSkillWaits() {
        val l = Levels.all[0]
        val b = Battle(l)
        assertNotNull(l.hero)
        assertTrue(b.placeHero(10)!!.contains("城墙或田边"))
        assertNull(b.placeHero(l.heroSlot))
        assertTrue(b.placeHero(1)!!.contains("已经上阵"))
        assertTrue("not ready at once", !b.heroReady)
        repeat(200) { b.step(0.05) }
        assertTrue(b.heroReady)
        assertTrue(b.useSkill())
        assertTrue(!b.heroReady)
        assertTrue("his slot is taken", b.place(UnitType.ARCHER, l.heroSlot)!!.contains("已经有兵"))
    }

    @Test
    fun twinsMergeAndOthersDoNot() {
        val b = Battle(Levels.all[0])
        b.place(UnitType.ARCHER, 2)
        b.place(UnitType.ARCHER, 3)
        assertNull(b.merge(2, 3))
        assertEquals(2, b.units[3]!!.rank)
        assertTrue(2 !in b.units)
        b.place(UnitType.ARCHER, 2)
        assertTrue(b.merge(2, 3)!!.contains("同一级"))
        assertNull(b.relocate(2, 4))
        assertTrue(4 in b.units && 2 !in b.units)
    }

    @Test
    fun theEndlessWavesGrow() {
        val e = Levels.endless
        assertTrue(e.endless)
        val sizes = (1..20).map { n -> Levels.endlessWave(n).sumOf { it.count } }
        // Bigger on the whole (a wave with rams and a general is followed by a smaller one, then they grow again).
        assertTrue(sizes.takeLast(5).average() > 2.5 * sizes.take(5).average())
        assertTrue(Levels.endlessWave(5).any { it.type == EnemyType.GENERAL })
        // With the full plan it holds some waves, and in the end it falls.
        val r = Sim.play(e)
        println("endless held ${r.wave - 1} waves")
        assertTrue(r.wave >= 5)
    }
}
