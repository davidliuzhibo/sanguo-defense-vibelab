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
        signMisses = 0
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

    // ---- v0.3: the general, signs drawn in the air, merging, 无尽 ----

    fun placeHero(slot: Int) {
        val why = synchronized(lock) { battle.placeHero(slot) }
        changed()
        if (why != null) { say(why); sound?.play(Sfx.WRONG); return }
        sound?.play(Sfx.PLACE)
        battle.hero?.let { say("${it.label}上阵！他会自己打身边的敌人；身上发亮时捏一下他，用「${it.skill}」。") }
    }

    fun useSkill() {
        if (phase != Phase.PLAYING) return
        val h = battle.hero ?: return
        if (synchronized(lock) { battle.useSkill() }) { changed(); return }
        say(if (battle.heroSlot == null) "先把${h.label}从托盘拖到城墙或田边。" else "${h.label}的「${h.skill}」还要 ${battle.heroCool.toInt() + 1} 秒。")
    }

    /** Failed signs in a row this battle: after two, a plain pinch on the general also casts (no one is left stuck). */
    private var signMisses = 0

    /** A sign drawn in the air with the general pinched ([points]: how long the stroke was, in samples). */
    fun cast(g: com.vrgames.sanguo.core.Glyph?, points: Int) {
        if (phase != Phase.PLAYING) return
        val want = com.vrgames.sanguo.core.Glyph.of(level.stratagem)
        if (points < 4) { generalTapped(); return }
        if (!battle.stratagemReady) { say("计策条还没满：多击退几个敌人。"); return }
        if (g == want) {
            signMisses = 0
            useStratagem()
        } else {
            signMisses++
            sound?.play(Sfx.WRONG)
            say("没认出来${if (g != null) "（像是「${g.label}」）" else ""}：${level.stratagem.label}要画${want.how}「${want.label}」。" + if (signMisses >= 2) "也可以直接捏一下将领。" else "")
        }
    }

    /** A plain pinch on the tower general: tells how to draw the sign (or, after two misses, casts). */
    fun generalTapped() {
        if (phase != Phase.PLAYING) return
        val want = com.vrgames.sanguo.core.Glyph.of(level.stratagem)
        if (!battle.stratagemReady) { useStratagem(); return }
        if (signMisses >= 2) { useStratagem(); return }
        say("计策准备好了！捏住城楼上的${level.stratagem.general}不放，在空中画${want.how}「${want.label}」，松手就施计。")
    }

    /** A placed unit carried onto [to]: merged with its twin there, or moved if [to] is empty. */
    fun mergeOrMove(from: Int, to: Int) {
        val merging = synchronized(lock) { battle.units[to] } != null
        val why = synchronized(lock) { if (merging) battle.merge(from, to) else battle.relocate(from, to) }
        changed()
        if (why != null) { say(why); sound?.play(Sfx.WRONG); return }
        if (merging) say("两个兵合成了一个更高级的！") else sound?.play(Sfx.PLACE)
    }

    /** 守荆州 · 无尽: the most waves held. */
    fun endlessBest() = prefs?.getInt("endless_best", 0) ?: 0

    /** Called on the main thread when the battle is won or lost. */
    fun finished(won: Boolean) {
        if (level.endless) {
            val held = (battle.wave - 1).coerceAtLeast(0)
            lastStars = 0
            if (held > endlessBest()) prefs?.edit()?.putInt("endless_best", held)?.apply()
            phase = Phase.RESULT
            selected = null
            sound?.play(Sfx.LOSE)
            say("城破了。这一次守住了 $held 波" + if (held >= endlessBest()) "，新纪录！" else "（纪录 ${endlessBest()} 波）。")
            changed()
            Log.i(TAG, "endless over: held $held waves")
            return
        }
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
