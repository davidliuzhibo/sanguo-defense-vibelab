package com.vrgames.sanguo.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.sanguo.CHRONICLE_ID
import com.vrgames.sanguo.INTEL_ID
import com.vrgames.sanguo.core.Battle
import com.vrgames.sanguo.core.UnitType
import com.vrgames.sanguo.game.Game

const val PANEL_CARD = "card"
const val PANEL_HUD = "hud"
const val PANEL_TRAY = "tray"
const val PANEL_UNIT = "unit"
const val PANEL_RESULT = "result"
const val PANEL_MENU = "menu"
const val PANEL_NOTE = "note"

val LabelInk = Color(0xFFF4EEE2)
val Brass = Color(0xFFE2B75A)
val Dim = Color(0xFFC2B8A6)
val Jade = Color(0xFF8FD3A8)
val Clay = Color(0xFFE07A4F)
private val PanelBg = Color(0xE6151A22)
/** Window backdrop: the system glass can be light, and every text color here is meant for dark. */
val WindowBg = Color(0xF2151A22)

@Composable
fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

@Composable
private fun PanelBox(c: PhysicalLengthConverter, width: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.014f))
    Column(
        Modifier.width(c.m(width)).clip(shape).background(PanelBg).border(c.m(0.0012f), Color(0x55E2B75A), shape).padding(c.m(0.015f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.007f)),
    ) { content() }
}

/**
 * The step that moves the battle on, filled in brass with dark text: the design system's dark
 * buttons read as plain text, and players on the device never thought to pinch them.
 */
@Composable
fun CallToAction(c: PhysicalLengthConverter, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        text,
        Modifier.clip(shape).background(Brass).border(c.m(0.002f), Color(0xFFFFF1C9), shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.018f), vertical = c.m(0.01f)),
        fontSize = c.sz(0.022f),
        color = Color(0xFF1B1408),
    )
}

@Composable
fun Pill(c: PhysicalLengthConverter, text: String, color: Color = Dim, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.01f))
    Text(
        text,
        Modifier.clip(shape).border(c.m(0.0015f), color, shape).clickable { onClick() }.padding(horizontal = c.m(0.011f), vertical = c.m(0.007f)),
        fontSize = c.sz(0.015f),
        color = color,
    )
}

/** Before a battle: the story, what is new, the stratagem, and the brass button to start laying out. */
@Composable
fun CardPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val l = Game.level
    PanelBox(c, 0.52f) {
        Text("第 ${l.number} 关　${l.title}", fontSize = c.sz(0.03f), color = Brass)
        Text(l.story, fontSize = c.sz(0.017f), color = LabelInk)
        Text("新本领：${l.lesson}", fontSize = c.sz(0.016f), color = Jade)
        Text("本关计策：${l.stratagem.label}（${l.stratagem.general}）——${l.stratagem.story}。计策条满了，捏一下城楼上的${l.stratagem.general}就能用。", fontSize = c.sz(0.015f), color = Dim)
        Text("共 ${l.waves.size} 波敌军。开局粮草 ${l.grain}，击退敌人还能缴获粮草。", fontSize = c.sz(0.015f), color = Dim)
        CallToAction(c, "开始布阵 →") { Game.start() }
    }
}

/** Behind the table, raised: grain, waves, the gate, the stratagem, and the way on. */
@Composable
fun HudPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val b = Game.battle
    val l = Game.level
    PanelBox(c, 0.5f) {
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.014f)), verticalAlignment = Alignment.CenterVertically) {
            Text("第 ${l.number} 关 ${l.title}", fontSize = c.sz(0.02f), color = Brass)
            Text("粮草 ${b.grain}", fontSize = c.sz(0.02f), color = LabelInk)
            Text("第 ${b.wave}/${b.waves} 波", fontSize = c.sz(0.02f), color = LabelInk)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            Text("城门", fontSize = c.sz(0.016f), color = Dim)
            Bar(c, b.gate / Battle.GATE_HP.toFloat(), if (b.gate > Battle.GATE_HP / 2) Jade else Clay)
            Text("计策", fontSize = c.sz(0.016f), color = Dim)
            Bar(c, b.charge.toFloat(), Brass)
        }
        when {
            !b.inWave && b.wave < b.waves -> {
                Text("从桌子前沿的托盘里捏起兵，拖到发亮的空位上；捏一下已放好的兵可以升级或撤下。", fontSize = c.sz(0.014f), color = Dim)
                CallToAction(c, if (b.wave == 0) "出战：第 1 波 →" else "出战：第 ${b.wave + 1} 波 →") { Game.startWave() }
            }
            b.inWave -> Text("第 ${b.wave} 波来袭！还可以继续布兵。", fontSize = c.sz(0.016f), color = Clay)
        }
        if (b.stratagemReady && !b.over) CallToAction(c, "用计：${l.stratagem.label}（${l.stratagem.general}）") { Game.useStratagem() }
    }
}

@Composable
private fun Bar(c: PhysicalLengthConverter, f: Float, color: Color) {
    Box(Modifier.width(c.m(0.12f)).height(c.m(0.012f)).clip(RoundedCornerShape(c.m(0.006f))).background(Color(0x33FFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0f, 1f)).background(color))
    }
}

/** Under the tray: what each unit is and costs (the figures above are what one pinches). */
@Composable
fun TrayPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val b = Game.battle
    Row(Modifier.width(c.m(0.62f)).clip(RoundedCornerShape(c.m(0.008f))).background(PanelBg).padding(c.m(0.006f)), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (t in UnitType.entries) {
            val ok = t in b.available
            Column(Modifier.width(c.m(0.11f)), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t.label, fontSize = c.sz(0.016f), color = if (ok) LabelInk else Dim)
                Text(if (ok) "粮 ${t.cost}" else "未开放", fontSize = c.sz(0.013f), color = if (ok && b.grain >= t.cost) Brass else Dim)
            }
        }
    }
}

/** Over a placed unit: its rank, what it does, and upgrade / withdraw. */
@Composable
fun UnitPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val id = Game.selected ?: return
    val u = Game.battle.units[id] ?: return
    PanelBox(c, 0.24f) {
        Text("${u.type.label}　${u.rank} 级", fontSize = c.sz(0.018f), color = Brass)
        Text(u.type.note, fontSize = c.sz(0.013f), color = Dim)
        if (u.rank < 3) CallToAction(c, "升级（粮 ${Game.battle.upgradeCost(id)}）") { Game.upgrade(id) }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            Pill(c, "撤下（退 ${(u.spent * 0.6).toInt()}）") { Game.sell(id) }
            Pill(c, "关闭") { Game.selected = null }
        }
    }
}

/** After a battle: won or lost, the stars and what each means, and the way on. */
@Composable
fun ResultPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val b = Game.battle
    val l = Game.level
    val s = Game.lastStars
    PanelBox(c, 0.48f) {
        if (b.won) {
            Text("守住了！", fontSize = c.sz(0.032f), color = Brass)
            Text("★".repeat(s) + "☆".repeat(3 - s), fontSize = c.sz(0.04f), color = Brass)
            Text("★ 守住城池\n${if (b.gate >= Battle.GATE_HP / 2) "★" else "☆"} 城门耐久剩一半以上（剩 ${b.gate}/${Battle.GATE_HP}）\n${if (b.spent <= l.grainPar) "★" else "☆"} 用粮不超过 ${l.grainPar}（用了 ${b.spent}）", fontSize = c.sz(0.016f), color = LabelInk)
            val next = Game.next()
            if (next != null) CallToAction(c, "下一关：${next.title} →") { Game.nextLevel() } else Text("六场战役全部守住！", fontSize = c.sz(0.02f), color = Jade)
        } else {
            Text("城门被攻破了", fontSize = c.sz(0.03f), color = Clay)
            Text("试试把兵集中在敌人必经的路口，用拒马让敌人走慢，计策条满了就用。", fontSize = c.sz(0.016f), color = LabelInk)
            CallToAction(c, "再守一次") { Game.restart() }
        }
        if (b.won) Pill(c, "再玩一次这一关") { Game.restart() }
    }
}

@Composable
fun NotePanel(c: PhysicalLengthConverter) {
    val n = Game.note ?: return
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(n, Modifier.widthIn(max = c.m(0.46f)).clip(shape).background(PanelBg).padding(c.m(0.01f)), fontSize = c.sz(0.017f), color = Brass)
}

@Composable
fun MenuPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    if (!Game.menuOpen) {
        val label = if (Game.phase == Game.Phase.RESULT && Game.battle.won && Game.next() != null) "菜单 · 可以进入下一关" else "菜单"
        Pill(c, label, Brass) { Game.menuOpen = true }
        return
    }
    PanelBox(c, 0.26f) {
        if (Game.phase == Game.Phase.RESULT && Game.battle.won) Game.next()?.let { n -> CallToAction(c, "下一关：${n.title} →") { Game.menuOpen = false; Game.nextLevel() } }
        Pill(c, if (Game.window == INTEL_ID) "收起军情图" else "军情图（选关、敌情、兵种）", Brass) { toggle(INTEL_ID) }
        Pill(c, if (Game.window == CHRONICLE_ID) "收起战役志" else "战役志（演义与正史）") { toggle(CHRONICLE_ID) }
        Pill(c, "这一关从头再来") { Game.menuOpen = false; Game.restart() }
        Pill(c, "把沙盘摆到我正前方") { faceMe() }
        Pill(c, if (Game.musicOn) "音乐：开" else "音乐：关") { Game.toggleMusic() }
        Pill(c, "退出") { quit() }
        Text("进度自动保存。版本 $version", fontSize = c.sz(0.012f), color = Dim)
        Pill(c, "收起菜单") { Game.menuOpen = false }
    }
}

/**
 * A window's title row with its 关闭 button. It also tells [Game] while this copy of window [id] is
 * on screen, so a window the system closes does not count as open.
 */
@Composable
fun WindowHeader(id: String, title: String, size: TextUnit) {
    val copy = remember { Any() }
    DisposableEffect(copy) {
        Game.windowShown(id, copy)
        onDispose { Game.windowGone(id, copy) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = size, color = Brass)
        Spacer(Modifier.weight(1f))
        Button(onClick = { Game.closeWindow(id) }) { Text("关闭", fontSize = 22.sp) }
    }
}
