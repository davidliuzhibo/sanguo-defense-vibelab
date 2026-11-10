package com.vrgames.sanguo.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.vrgames.sanguo.core.Vec3

/** CC0 clips from Kenney's RPG Audio, Interface Sounds and Impact Sounds; see docs/素材来源.md. */
enum class Sfx(val file: String, val volume: Float) {
    PLACE("place", 0.9f),
    UPGRADE("upgrade", 0.9f),
    WRONG("wrong", 0.7f),
    /** The war drum: a wave sets out. */
    DRUM("drum", 1f),
    STRATAGEM("stratagem", 1f),
    WIN("win", 1f),
    LOSE("lose", 0.9f),
    SHOOT("shoot", 0.5f),
    STONE("stone", 0.7f),
    /** A soldier driven off in a puff. */
    PUFF("puff", 0.6f),
    /** Something reached the gate. */
    BREACH("breach", 0.9f),
    // v0.2, synthesized by tools/make_battle_sounds.py.
    /** An arrow or a thrust lands. */
    HIT("hit", 0.55f),
    /** A stone lands. */
    CRASH("crash", 0.7f),
    /** Stones crack off the gate. */
    CRACK("crack", 0.8f),
    FIRE("fire", 0.9f),
    BOOM("boom", 1f),
    WIND("wind", 0.9f),
    WAVE("wave", 0.9f),
    QIN("qin", 0.9f),
    RUMBLE("rumble", 0.9f),
}

/** The sound of each stratagem's show. */
fun stratagemSfx(s: com.vrgames.sanguo.core.Stratagem) = when (s) {
    com.vrgames.sanguo.core.Stratagem.FIRE_ROAD -> Sfx.FIRE
    com.vrgames.sanguo.core.Stratagem.ROAR -> Sfx.BOOM
    com.vrgames.sanguo.core.Stratagem.EAST_WIND -> Sfx.WIND
    com.vrgames.sanguo.core.Stratagem.FLOOD -> Sfx.WAVE
    com.vrgames.sanguo.core.Stratagem.EMPTY_CITY -> Sfx.QIN
    com.vrgames.sanguo.core.Stratagem.EIGHT_ARRAYS -> Sfx.RUMBLE
}

/**
 * Spatial sound from the scene: each effect has one long-lived emitter with a prepared player,
 * moved to where the sound happens and restarted (creating players per event drops frames).
 * Main thread only.
 */
class Sound(private val parent: Entity) {
    private val emitters = HashMap<Sfx, Entity>()
    private val controllers = HashMap<Sfx, AudioPlayerController>()
    var enabled = true

    /** Loads every clip; a clip that fails is skipped (logged). */
    fun load() = loadFrom(0)

    /**
     * One clip per turn of the main loop (v0.2): with the new clips, loading them all in one go held
     * the main thread long enough for an "app not responding" on the emulator.
     */
    private fun loadFrom(i: Int) {
        val s = Sfx.entries.getOrNull(i) ?: return
        run {
            runCatching {
                val res = AudioResource.load(s.file, "asset://audio/${s.file}.wav", LoadType.FROM_ASSETS)
                val e = Entity()
                e.components.set(ObjectAudioComponent(s.volume))
                parent.addChild(e)
                val c = e.prepareAudio(res)
                if (c.valid) {
                    emitters[s] = e
                    controllers[s] = c
                }
            }.onFailure { android.util.Log.w(TAG, "sfx ${s.file}: ${it.message}") }
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post { loadFrom(i + 1) }
    }

    /** Plays [s] at [at] (in [parent]'s frame; by default the middle of the parent). */
    fun play(s: Sfx, at: Vec3 = Vec3(0.0, 0.1, 0.0)) {
        if (!enabled) return
        val c = controllers[s] ?: return
        emitters[s]?.setPos(at)
        runCatching {
            if (c.isPlaying()) c.stop()
            c.seekTo(0)
            c.play()
        }
    }

    companion object {
        private const val TAG = "GameSound"
    }
}
