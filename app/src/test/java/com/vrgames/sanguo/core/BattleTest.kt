package com.vrgames.sanguo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleTest {
    @Test
    fun everyPlanIsAffordableAndWinsWithTwoStars() {
        for (l in Levels.all) {
            val b = Battle(l)
            // Every move of the plan is legal when its wave comes (grain earned on the way counts).
            val r = Sim.play(l)
            println("${l.id}: won=${r.won} stars=${r.stars} gate=${r.gate} spent=${r.spent} par=${l.grainPar} t=${"%.0f".format(r.seconds)}s wave=${r.wave}")
            assertTrue("${l.id} lost: $r", r.won)
            assertTrue("${l.id} only ${r.stars} stars: $r", r.stars >= 2)
            assertNotNull(b)
        }
    }

    @Test
    fun doingNothingLoses() {
        for (l in Levels.all) assertFalse("${l.id} won with no defence", Sim.play(l, emptyList(), stratagem = false).won)
    }

    @Test
    fun halfThePlanIsNotEnoughEverywhere() {
        // A thin defence should not be a sure win: at least four of the six battles are lost with half the plan.
        val lost = Levels.all.count { l -> !Sim.play(l, l.plan.take(l.plan.size / 3), stratagem = false).won }
        assertTrue("only $lost lost", lost >= 4)
    }

    @Test
    fun placementSaysWhyNot() {
        val b = Battle(Levels.all[0])
        assertTrue(b.place(UnitType.SPEAR, 2)!!.contains("路上"))
        assertTrue(b.place(UnitType.ARCHER, 9)!!.contains("路上"))
        assertTrue(b.place(UnitType.CROSSBOW, 2)!!.contains("还不能用"))
        assertNull(b.place(UnitType.ARCHER, 2))
        assertTrue(b.place(UnitType.ARCHER, 2)!!.contains("已经有兵"))
        assertNull(b.place(UnitType.CATAPULT, 1))
        assertTrue(b.place(UnitType.CATAPULT, 3)!!.contains("粮草不够"))
    }

    @Test
    fun sellingGivesBackSixTenths() {
        val b = Battle(Levels.all[0])
        b.place(UnitType.ARCHER, 2)
        val before = b.grain
        assertEquals(36, b.sell(2))
        assertEquals(before + 36, b.grain)
    }

    @Test
    fun theWaterTakesTheLowFields() {
        val l = Levels.byId("fancheng")
        val b = Battle(l)
        assertNull(b.place(UnitType.ARCHER, 5))
        while (b.wave < 2 || b.inWave) {
            if (!b.inWave) b.startWave()
            b.step(0.05)
            if (b.over) break
        }
        b.step(0.05)
        assertTrue(b.flooded)
        assertFalse(5 in b.units)
        assertTrue(b.place(UnitType.ARCHER, 5)!!.contains("水淹"))
    }

    @Test
    fun theEmptyCityIsReadyFromTheStart() {
        val b = Battle(Levels.byId("xicheng"))
        assertTrue(b.stratagemReady)
        assertTrue(b.useStratagem())
        assertFalse(b.stratagemReady)
    }
}
