package com.vrgames.sanguo.core

import org.junit.Test

/** Prints how each battle goes with the full plan, a third of it, and none: for tuning by hand. */
class BalanceLab {
    @Test
    fun report() {
        for (l in Levels.all) {
            val full = Sim.play(l)
            val third = Sim.play(l, l.plan.take(l.plan.size / 3), stratagem = false)
            val half = Sim.play(l, l.plan.take(l.plan.size / 2), stratagem = true)
            println("LAB ${l.id}: full won=${full.won} gate=${full.gate} spent=${full.spent}/${l.grainPar} w${full.wave} | half won=${half.won} gate=${half.gate} w${half.wave} | third won=${third.won} gate=${third.gate} w${third.wave}")
        }
    }
}
