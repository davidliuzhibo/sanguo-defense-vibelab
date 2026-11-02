package com.vrgames.sanguo.core

/** Plays a level through on its own: the tests use it for balance, the test hook to show a battle. */
object Sim {
    data class Result(val won: Boolean, val stars: Int, val gate: Int, val spent: Int, val seconds: Double, val wave: Int)

    /** Plays [moves] before each wave and the stratagem whenever it is ready and enemies are about. */
    fun play(level: Level, moves: List<Move> = level.plan, stratagem: Boolean = true, dt: Double = 0.05): Result {
        val b = Battle(level)
        var t = 0.0
        // Moves not yet affordable wait for the grain the next waves bring, as a player would.
        val waiting = ArrayList<Move>()
        while (!b.over && t < 1200) {
            if (!b.inWave) {
                waiting += moves.filter { it.beforeWave == b.wave + 1 }
                waiting.removeAll { m -> apply(b, m).let { it == null || !it.contains("粮草") } }
                b.startWave()
            }
            if (stratagem && b.stratagemReady && b.enemies.size >= 4) b.useStratagem()
            b.step(dt)
            t += dt
        }
        return Result(b.won, b.stars(), b.gate, b.spent, t, b.wave)
    }

    fun apply(b: Battle, m: Move): String? = if (m.type != null) b.place(m.type, m.slot) else b.upgrade(m.slot)
}
