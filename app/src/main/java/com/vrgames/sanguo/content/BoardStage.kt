package com.vrgames.sanguo.content

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.sanguo.CHRONICLE_ID
import com.vrgames.sanguo.INTEL_ID
import com.vrgames.sanguo.core.Battle
import com.vrgames.sanguo.core.Vec3
import com.vrgames.sanguo.game.AutoPilot
import com.vrgames.sanguo.game.Game
import com.vrgames.sanguo.render.BoardView
import com.vrgames.sanguo.render.Music
import com.vrgames.sanguo.render.Sfx
import com.vrgames.sanguo.render.Sound
import com.vrgames.sanguo.render.TableFinder
import com.vrgames.sanguo.render.setPos
import com.vrgames.sanguo.render.transform

/**
 * The sand table on the player's table (Stage, mixed reality). Pinch a unit in the tray at the front
 * edge and drag it onto a glowing slot; pinch a placed unit to upgrade or withdraw it; pinch the
 * general on the gate tower (or the brass button) for the stratagem. All the input is pinch-and-drag
 * on things, which is sure on the device; a pinch's end comes from the raw finger-up as well, since
 * the drag's own end sometimes never arrives.
 */
@Composable
fun BoardStage() {
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val head = remember { HMDTrackingProvider() }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    var view by remember { mutableStateOf<BoardView?>(null) }
    val place = remember { Place() }
    val panels = remember { HashMap<String, Entity>() }
    val drag = remember { longArrayOf(0L, 0L) } // [0]: dragging (1/0), [1]: last callback (uptime ms)
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }

    val toggleWindow: (String) -> Unit = { id ->
        val shown = Game.window
        if (shown != null) Game.closeWindow(shown)
        if (shown != id) {
            Game.windowShown(id)
            navigator.openWindowContainer(id)
        }
    }
    val letGo: (String) -> Unit = { why ->
        val v = view
        if (v != null && drag[0] == 1L) {
            drag[0] = 0L
            val r = synchronized(Game.lock) { v.endDrag() }
            Log.i(INPUT_TAG, "drop ($why) -> $r")
            if (r != null) {
                val (type, slot) = r
                if (slot != null) Game.place(type, slot) else Game.say("要放在发亮的空位上：弓兵、投石车、连弩放城墙或田边，枪兵和拒马放在路上。")
            }
        }
    }

    DisposableEffect(Unit) {
        Game.init(context)
        Game.closeWindow = { id ->
            navigator.closeWindowContainer(id)
            Game.windowGone(id)
        }
        val hd = runCatching { head.start() }
        Log.i(TAG, "head tracking ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
            runCatching { head.stop() }
            TableFinder.stop()
            subscription[0]?.cancel()
            Game.closeWindow(INTEL_ID)
            Game.closeWindow(CHRONICLE_ID)
            Game.closeWindow = {}
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also {
            it.muted = !Game.musicOn
            Game.music = it
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            music.release()
            Game.music = null
        }
    }
    LaunchedEffect(Game.roundVersion, view) {
        val v = view ?: return@LaunchedEffect
        synchronized(Game.lock) {
            v.show(Game.level)
            v.trayFor(Game.level.units)
            v.sync(Game.battle)
        }
    }
    LaunchedEffect(Game.version, view) { view?.let { v -> synchronized(Game.lock) { v.sync(Game.battle) } } }
    LaunchedEffect(Game.faceRequest) { if (Game.faceRequest > 0) place.again() }
    LaunchedEffect(Game.height, view) { place.lift(Game.height) }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        kotlinx.coroutines.delay(6000)
        if (Game.note == n) Game.note = null
    }
    LaunchedEffect(Game.debugView, view) {
        val v = view ?: return@LaunchedEffect
        val d = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        if (d != null && d.size >= 6) {
            place.debug = true
            v.root.setPos(Vector3(d[0], d[1], d[2]))
            v.root.transform().eulerAngles = EulerAngles(d[5], d[3], 0f)
            v.root.transform().setScaleVector(Vector3(d[4]))
        } else {
            place.debug = false
            v.root.transform().setScaleVector(Vector3(1f))
            place.again()
        }
    }
    LaunchedEffect(view) {
        if (view == null) return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        val (x, z) = place.lastPose ?: return@LaunchedEffect
        TableFinder.find(x, z, place.heading)?.let { found ->
            Log.i(TAG, "real table at ${found.height} m, ${found.distance} m ahead")
            place.table(found.height, found.distance)
        }
    }
    // Panels: one explanation at a time.
    LaunchedEffect(Game.phase, Game.menuOpen, Game.note, Game.selected, view) {
        fun show(id: String, on: Boolean) = panels[id]?.let { if (it.enabled != on) it.enabled = on }
        val playing = Game.phase == Game.Phase.PLAYING
        show(PANEL_CARD, Game.phase == Game.Phase.CARD)
        show(PANEL_RESULT, Game.phase == Game.Phase.RESULT)
        show(PANEL_HUD, playing)
        show(PANEL_TRAY, playing)
        show(PANEL_NOTE, Game.note != null)
        show(PANEL_MENU, true)
        show(PANEL_UNIT, playing && Game.selected != null)
        panels[PANEL_MENU]?.setPos(if (Game.menuOpen) MENU_OPEN_POS else MENU_POS)
        Game.selected?.let { id -> view?.slotSpot(id)?.let { p -> panels[PANEL_UNIT]?.setPos(Vector3(p.x, p.y + 0.11f, p.z + 0.02f)) } }
    }
    LaunchedEffect(Game.debugOpen) {
        when (Game.debugOpen ?: return@LaunchedEffect) {
            "intel" -> if (Game.window != INTEL_ID) toggleWindow(INTEL_ID)
            "chronicle" -> if (Game.window != CHRONICLE_ID) toggleWindow(CHRONICLE_ID)
            "menu" -> Game.menuOpen = true
            "close" -> Game.window?.let { Game.closeWindow(it) }
        }
        Game.debugOpen = null
    }

    SpatialView(
        Modifier
            .pointerInput(Unit) {
                fun meters(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }.toDouble()
                detectSpatialDragGesture(
                    context = context,
                    targetedToEntity = TargetEntity.any(),
                    onDragStart = { _, value ->
                        val v = view ?: return@detectSpatialDragGesture
                        drag[1] = SystemClock.uptimeMillis()
                        if (Game.phase != Game.Phase.PLAYING) return@detectSpatialDragGesture
                        val hit = v.hitOf(value.targetEntity)
                        Log.i(INPUT_TAG, "drag start $hit")
                        when (hit) {
                            is BoardView.Hit.Token -> {
                                Game.selected = null
                                synchronized(Game.lock) { v.beginDrag(hit.type, Game.battle) }
                                drag[0] = 1L
                            }
                            is BoardView.Hit.Placed -> Game.selected = hit.slot
                            BoardView.Hit.General -> Game.useStratagem()
                            null -> Unit
                        }
                    },
                    onDragEnd = { letGo("end") },
                    onDragCancel = { letGo("cancel") },
                ) { value ->
                    val v = view ?: return@detectSpatialDragGesture
                    if (drag[0] != 1L) return@detectSpatialDragGesture
                    drag[1] = SystemClock.uptimeMillis()
                    // Room motion into the board's frame (Compose y points down; only x and z matter here).
                    val (dx, dz) = place.toBook(meters(value.dragAmount.x), meters(value.dragAmount.z))
                    synchronized(Game.lock) { v.drag(Vec3(dx, 0.0, dz), Game.battle) }
                }
            }
            // A quick, still pinch on a placed unit or the general is a tap, not a drag.
            .pointerInput(1) {
                detectSpatialTapGesture(context, TargetEntity.any()) { value ->
                    val v = view ?: return@detectSpatialTapGesture
                    if (Game.phase != Game.Phase.PLAYING) return@detectSpatialTapGesture
                    when (val hit = v.hitOf(value.targetEntity)) {
                        is BoardView.Hit.Placed -> Game.selected = hit.slot
                        BoardView.Hit.General -> Game.useStratagem()
                        is BoardView.Hit.Token -> Game.say("捏住${hit.type.label}不放，拖到发亮的空位上再松开。")
                        null -> Unit
                    }
                }
            }
            // The fingers opening ends a drag even when the drag's own end never comes.
            .pointerInput(2) {
                detectSpatialPointerEvent(context, TargetEntity.any()) { infos ->
                    if (infos.any { it.isUpEvent() }) main.post { letGo("fingers up") }
                    false
                }
            },
        attachments = {
            AttachmentPanel(PANEL_CARD) { CardPanel(converter) }
            AttachmentPanel(PANEL_HUD) { HudPanel(converter) }
            AttachmentPanel(PANEL_TRAY) { TrayPanel(converter) }
            AttachmentPanel(PANEL_UNIT) { UnitPanel(converter) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter) }
            AttachmentPanel(PANEL_NOTE) { NotePanel(converter) }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(converter, toggleWindow, faceMe = { Game.faceRequest++ }, quit = { context.findActivity()?.finishAndRemoveTask() }, version = appVersion)
            }
        },
        update = { _, attachments -> view?.let { attach(it, attachments, panels) } },
        initial = { content, attachments ->
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFEBD0).toColor4(), 1000f, true))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-55f, 30f, 0f))
            })
            val models = HashMap<String, Entity>()
            for (name in MODELS) {
                runCatching { Entity.loadSuspend("asset://models/$name.glb") }
                    .onSuccess { models[name] = it }
                    .onFailure { Log.i(TAG, "model $name: ${it.message}") }
            }
            val v = BoardView(models)
            place.root = v.root
            place.put()
            content.addEntity(v.root)
            attach(v, attachments, panels)
            Game.sound = Sound(v.root).also { it.load() }
            synchronized(Game.lock) {
                v.show(Game.level)
                v.trayFor(Game.level.units)
            }
            view = v
            AutoPilot.ready()
            var shotSound = 0.0
            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
                val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
                if (pose != null) place.frame(pose.position, pose.rotation.rotateVector(Vector3(0f, 0f, -1f)), dt, main)
                // A drag whose end never came: let go after two silent seconds.
                if (drag[0] == 1L && SystemClock.uptimeMillis() - drag[1] > STUCK_DRAG_MS) main.post { letGo("watchdog") }
                synchronized(Game.lock) {
                    val b = Game.battle
                    // The battle is stepped in slices of at most 50 ms whatever the frame time, so a slow
                    // frame (or the test hook's speed) plays out the same as a smooth one.
                    val events = ArrayList<Battle.Event>()
                    if (Game.phase == Game.Phase.PLAYING) {
                        var left = dt * Game.speed
                        while (left > 1e-9 && !b.over) {
                            val slice = minOf(left, MAX_STEP)
                            events += b.step(slice)
                            left -= slice
                        }
                    }
                    shotSound -= dt
                    var changed = false
                    for (ev in events) {
                        v.on(ev, b)
                        when (ev) {
                            is Battle.Event.Shot -> if (shotSound <= 0) {
                                shotSound = 0.25
                                main.post { Game.sound?.play(if (ev.type == com.vrgames.sanguo.core.UnitType.CATAPULT) Sfx.STONE else Sfx.SHOOT) }
                            }
                            is Battle.Event.Down -> main.post { Game.sound?.play(Sfx.PUFF) }
                            is Battle.Event.Breach -> main.post { Game.sound?.play(Sfx.BREACH); Game.say("敌军撞到了城门！") }
                            is Battle.Event.UnitDown -> main.post { Game.say("一名枪兵被击退了，可以再派一名。") }
                            is Battle.Event.Flooded -> main.post { Game.say("汉水上涨了！低处田里的位置被淹，那里的兵已经撤回，粮草退还六成。") }
                            is Battle.Event.WaveClear -> main.post { Game.say(if (ev.wave < b.waves) "第 ${ev.wave} 波击退！布好阵，再捏「出战」。" else "最后一波也击退了！") }
                            Battle.Event.Won -> main.postDelayed({ Game.finished(true) }, 1500)
                            Battle.Event.Lost -> main.postDelayed({ Game.finished(false) }, 1200)
                            else -> Unit
                        }
                        if (ev !is Battle.Event.Shot) changed = true
                    }
                    if (changed) main.post { Game.changed() }
                    v.frame(b, dt * Game.speed)
                }
            }
        },
    )
}

/** Figures loaded if present (tools: Seed3D + prep_ai), else simple stand-ins. */
private val MODELS = listOf("zhuge", "zhangfei", "guanyu", "zhouyu", "archer", "spearman", "crossbow", "catapult", "foot", "cavalry", "ram")

private val MENU_POS = Vector3(-0.36f, 0.03f, 0.3f)
private val MENU_OPEN_POS = Vector3(-0.48f, 0.2f, 0.18f)

private fun attach(v: BoardView, a: SpatialViewAttachments, panels: HashMap<String, Entity>) {
    fun put(id: String, pos: Vector3, pitch: Float, yaw: Float = 0f) {
        val e = a.entity(id) ?: return
        if (panels[id] === e) return
        panels[id] = e
        v.root.addChild(e)
        e.setPos(pos)
        e.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
    }
    // Card, HUD and result stand behind the table, raised; the tray labels lie along the front edge.
    put(PANEL_CARD, Vector3(0f, 0.3f, -0.3f), -12f)
    put(PANEL_HUD, Vector3(0f, 0.3f, -0.3f), -12f)
    put(PANEL_RESULT, Vector3(0f, 0.3f, -0.3f), -12f)
    put(PANEL_NOTE, Vector3(0f, 0.45f, -0.3f), -12f)
    put(PANEL_TRAY, Vector3(0f, 0.016f, BoardView.D / 2 + 0.02f + BoardView.TRAY_D + 0.012f), -70f)
    put(PANEL_UNIT, Vector3(0f, 0.15f, 0f), -20f)
    put(PANEL_MENU, MENU_POS, -40f, 15f)
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private const val TAG = "SanguoStage"
/** Every pinch the stage sees (kept in release builds, for on-device reports). */
private const val INPUT_TAG = "SanguoInput"
private const val STUCK_DRAG_MS = 2000L

/** The longest slice of battle time stepped at once (the balance tests use 50 ms). */
private const val MAX_STEP = 0.05
