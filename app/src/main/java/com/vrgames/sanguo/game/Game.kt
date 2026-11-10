package com.vrgames.sanguo.game

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.sanguo.core.Battle
import com.vrgames.sanguo.core.Level
import com.vrgames.sanguo.core.Levels
import com.vrgames.sanguo.core.UnitType
import com.vrgames.sanguo.render.Music
import com.vrgames.sanguo.render.Sfx
import com.vrgames.sanguo.render.Sound

/**
 * State the stage, its panels and both windows share (one process). [battle] holds the rules and is
 * stepped on the frame thread under [lock]; the Compose fields mirror what the panels show.
 */
object Game {
    private const val PREFS = "sanguo"
    private const val TAG = "SanguoGame"

    val lock = Any()
    private var prefs: android.content.SharedPreferences? = null
    private val best = HashMap<String, Int>()
    var level: Level = Levels.all[0]; private set
    var battle = Battle(level); private set

    enum class Phase { CARD, PLAYING, RESULT }

    var phase by mutableStateOf(Phase.CARD)
    /** Bumps when a battle (re)starts: the table is laid out again. */
    var roundVersion by mutableIntStateOf(0); private set
    /** Bumps on every change the panels show (grain, wave, gate, units). */
    var version by mutableIntStateOf(0); private set
    var note by mutableStateOf<String?>(null)
    var noteVersion by mutableIntStateOf(0); private set
    /** The placed unit whose panel (upgrade / sell) is open, by slot. */
    var selected by mutableStateOf<Int?>(null)
    var menuOpen by mutableStateOf(false)
    var lastStars by mutableIntStateOf(0); private set
    var height by mutableFloatStateOf(0f)
    var musicOn by mutableStateOf(true); private set
    var music: Music? = null
    var sound: Sound? = null

    /** The window on screen, or null: one at a time, never twice (a second open starts a second copy). */
    var window by mutableStateOf<String?>(null); private set
    private val windowCopies = HashMap<String, Any>()
    var closeWindow: (String) -> Unit = {}

    fun windowShown(id: String, copy: Any? = null) {
        if (copy != null) windowCopies[id] = copy
        window = id
    }

    fun windowGone(id: String, copy: Any? = null) {
        if (copy != null && windowCopies[id] !== copy) return
        windowCopies.remove(id)
        if (window == id) window = null
    }

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            p.getString("stars", null)?.split(",")?.forEach { e -> e.split(":").takeIf { it.size == 2 }?.let { best[it[0]] = it[1].toIntOrNull() ?: 0 } }
            musicOn = p.getBoolean("music", true)
            height = p.getFloat("height", 0f)
        }
        level = Levels.all.firstOrNull { stars(it.id) == 0 && open(it) } ?: Levels.all.last()
        battle = Battle(level)
        AutoPilot.register(context)
    }

    fun stars(id: String) = best[id] ?: 0
    fun open(l: Level) = l.number == 1 || stars(Levels.all[l.number - 2].id) > 0
    val totalStars get() = best.values.sum()

    fun select(id: String) {
        val l = Levels.byId(id)
        if (!open(l)) return
        level = l
        synchronized(lock) { battle = Battle(l) }
        phase = Phase.CARD
        selected = null
        note = null
        roundVersion++
        version++
        Log.i(TAG, "level ${l.id}")
    }

    fun start() {
        phase = Phase.PLAYING
        version++
    }

    fun restart() = select(level.id)

    fun next(): Level? = Levels.all.getOrNull(level.number)?.takeIf { open(it) }

    fun nextLevel() {
        next()?.let { select(it.id) }
    }

    fun say(text: String?) {
        note = text
        noteVersion++
    }

    fun changed() {
        version++
    }

    /** Puts [type] on [slot]; a refusal becomes the note. */
    fun place(type: UnitType, slot: Int): Boolean {
        val why = synchronized(lock) { battle.place(type, slot) }
        changed()
        if (why != null) {
            say(why)
            sound?.play(Sfx.WRONG)
            return false
        }
        sound?.play(Sfx.PLACE)
        return true
    }

    fun upgrade(slot: Int) {
        val why = synchronized(lock) { battle.upgrade(slot) }
        changed()
        if (why != null) {
            say(why)
            sound?.play(Sfx.WRONG)
        } else {
            sound?.play(Sfx.UPGRADE)
        }
    }

    fun sell(slot: Int) {
        val back = synchronized(lock) { battle.sell(slot) }
        selected = null
        if (back > 0) say("撤下了，退回粮草 $back。")
        changed()
    }

    fun startWave() {
        if (phase != Phase.PLAYING) return
        if (synchronized(lock) { battle.startWave() }) {
            sound?.play(Sfx.DRUM)
            changed()
        }
    }

    fun useStratagem() {
        if (synchronized(lock) { battle.useStratagem() }) {
            sound?.play(Sfx.STRATAGEM)
            sound?.play(com.vrgames.sanguo.render.stratagemSfx(level.stratagem))
            say("计策：${level.stratagem.label}！${level.stratagem.story}")
            changed()
        } else if (!battle.stratagemReady) {
            say("计策还没准备好：多击退几个敌人，计策条满了就能用。")
        }
    }

    /** Called on the main thread when the battle is won or lost. */
    fun finished(won: Boolean) {
        val s = synchronized(lock) { battle.stars() }
        lastStars = s
        if (won && s > stars(level.id)) {
            best[level.id] = s
            prefs?.edit()?.putString("stars", best.entries.joinToString(",") { "${it.key}:${it.value}" })?.apply()
        }
        phase = Phase.RESULT
        selected = null
        sound?.play(if (won) Sfx.WIN else Sfx.LOSE)
        changed()
        Log.i(TAG, "done ${level.id}: won=$won $s stars gate=${battle.gate} spent=${battle.spent}")
    }

    fun raise(dy: Float) {
        height = (height + dy).coerceIn(-0.25f, 0.3f)
        prefs?.edit()?.putFloat("height", height)?.apply()
    }

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean("music", musicOn)?.apply()
    }

    fun resetSave() {
        best.clear()
        prefs?.edit()?.clear()?.apply()
        select(Levels.all[0].id)
    }

    // Debug hooks (AutoPilot).
    var debugView by mutableStateOf<String?>(null)
    var debugOpen by mutableStateOf<String?>(null)
    var faceRequest by mutableIntStateOf(0)
    /** Game speed (the test hook plays battles faster on the emulator). */
    @Volatile var speed = 1.0
}
