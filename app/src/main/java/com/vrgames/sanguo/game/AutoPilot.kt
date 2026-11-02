package com.vrgames.sanguo.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.sanguo.core.Levels
import com.vrgames.sanguo.core.Move
import com.vrgames.sanguo.core.UnitType

/**
 * Test hook, debug builds only: plays the battles with each level's plan, without hands, so every
 * battle can be checked on the emulator. Results go to logcat (tag SanguoTest).
 *
 *   adb shell am broadcast -a com.vrgames.sanguo.TEST -p com.vrgames.sanguo --es cmd all
 *
 * cmd: all (every battle in turn, at speed 4) · play (this battle) · level --es id xinye|changban|chibi|fancheng|xicheng|jingzhou ·
 * start · place --es id "ARCHER@2" · up --es id 2 · wave · stratagem · speed --es id 4 · select --es id 2 ·
 * view --es id "x,y,z,yaw,scale,pitch"|reset · open --es id intel|chronicle|menu|close · face · reset · stop
 */
object AutoPilot {
    const val ACTION = "com.vrgames.sanguo.TEST"
    const val TAG = "SanguoTest"
    private val main = Handler(Looper.getMainLooper())
    private var all = false
    private var running = false
    private val waiting = ArrayList<Move>()

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    /** The stage has built the table: tests wait for this line. */
    fun ready() = Log.i(TAG, "table ready")

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        when (cmd) {
            "all" -> {
                stop()
                all = true
                running = true
                Game.speed = 4.0
                Game.select(Levels.all[0].id)
                main.postDelayed(::tick, 1500)
            }
            "play" -> {
                stop()
                running = true
                main.postDelayed(::tick, 500)
            }
            "level" -> Game.select(id ?: return)
            "start" -> Game.start()
            "place" -> id?.split("@")?.let { (t, s) -> Game.place(UnitType.valueOf(t), s.toInt()) }
            "up" -> Game.upgrade(id?.toIntOrNull() ?: return)
            "wave" -> Game.startWave()
            "stratagem" -> Game.useStratagem()
            "speed" -> Game.speed = id?.toDoubleOrNull() ?: 1.0
            "select" -> Game.selected = id?.toIntOrNull()
            "view" -> Game.debugView = id?.takeIf { it != "reset" }
            "open" -> Game.debugOpen = id
            "face" -> Game.faceRequest++
            "reset" -> {
                stop()
                Game.resetSave()
            }
            "stop" -> stop()
            else -> Log.w(TAG, "unknown command $cmd")
        }
    }

    private fun stop() {
        all = false
        running = false
        waiting.clear()
        main.removeCallbacksAndMessages(null)
    }

    /** One step of a run: start the battle, lay out the plan before each wave, send the wave, move on. */
    private fun tick() {
        if (!running) return
        val b = Game.battle
        when (Game.phase) {
            Game.Phase.CARD -> {
                waiting.clear()
                Game.start()
            }
            Game.Phase.PLAYING -> {
                if (b.stratagemReady && b.enemies.size >= 4) Game.useStratagem()
                if (!b.inWave && !b.over) {
                    waiting += Game.level.plan.filter { it.beforeWave == b.wave + 1 }
                    waiting.removeAll { m ->
                        val ok = if (m.type != null) Game.place(m.type, m.slot) else { Game.upgrade(m.slot); true }
                        ok || Game.note?.contains("粮草") != true
                    }
                    Game.startWave()
                    Log.i(TAG, "wave ${b.wave} grain=${b.grain} gate=${b.gate} units=${b.units.size}")
                }
            }
            Game.Phase.RESULT -> {
                Log.i(TAG, "result ${Game.level.id}: won=${b.won} ${Game.lastStars} stars gate=${b.gate} spent=${b.spent}")
                val next = Game.next()
                if (all && b.won && next != null) {
                    Game.nextLevel()
                } else {
                    Log.i(TAG, "done total=${Game.totalStars}")
                    Game.speed = 1.0
                    stop()
                    return
                }
            }
        }
        main.postDelayed(::tick, 1000)
    }
}
