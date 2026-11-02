package com.vrgames.sanguo.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.vrgames.sanguo.CHRONICLE_ID
import com.vrgames.sanguo.INTEL_ID
import com.vrgames.sanguo.core.EnemyType
import com.vrgames.sanguo.core.Levels
import com.vrgames.sanguo.core.UnitType
import com.vrgames.sanguo.game.Game

private val CardBg = Color(0x26F4EEE2)

/** 军情图: the six battles (open one already reached), this battle's waves, and every unit and enemy. */
@Composable
fun IntelWindow() {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    Column(
        Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WindowHeader(INTEL_ID, "军情图　共 ${Game.totalStars}/18 ★", 32.sp)
        for (l in Levels.all) {
            val open = Game.open(l)
            val s = Game.stars(l.id)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { Game.select(l.id); Game.closeWindow(INTEL_ID) }, enabled = open && l.id != Game.level.id) {
                    Text("第 ${l.number} 关　${l.title}", fontSize = 21.sp)
                }
                Text(
                    when {
                        l.id == Game.level.id -> "正在守　" + "★".repeat(s) + "☆".repeat(3 - s)
                        !open -> "守住上一关后开放"
                        else -> "★".repeat(s) + "☆".repeat(3 - s)
                    },
                    fontSize = 19.sp, color = if (s > 0) Brass else Dim,
                )
            }
        }
        val l = Game.level
        Text("这一关的敌情", fontSize = 24.sp, color = Jade)
        for ((i, w) in l.waves.withIndex()) {
            val text = w.groupBy { it.type }.entries.joinToString("，") { (t, gs) -> "${t.label} ${gs.sumOf { it.count }}" }
            Text("第 ${i + 1} 波：$text" + if (i < Game.battle.wave) "（已击退）" else "", fontSize = 19.sp, color = if (i < Game.battle.wave) Dim else LabelInk)
        }
        Text("我军兵种", fontSize = 24.sp, color = Jade)
        for (t in UnitType.entries) {
            Text("${t.label}（粮 ${t.cost}）：${t.note}" + if (t !in l.units) "　【这一关还不能用】" else "", fontSize = 18.sp, color = if (t in l.units) LabelInk else Dim)
        }
        Text("升级要原价八成乘以当前等级的粮草，最高 3 级；撤下退还六成。", fontSize = 17.sp, color = Dim)
        Text("敌军", fontSize = 24.sp, color = Jade)
        for (e in EnemyType.entries) {
            val what = when (e) {
                EnemyType.FOOT -> "数量多，走得不快"
                EnemyType.CAVALRY -> "跑得快，血少"
                EnemyType.RAM -> "很结实，撞到城门伤害大"
                EnemyType.BOAT -> "从江上来，枪兵和拒马管不到它，靠弓弩和投石"
            }
            Text("${e.label}：$what。击退缴获粮草 ${e.bounty}。", fontSize = 18.sp, color = LabelInk)
        }
        Text("所有人物形象为本作原创。敌兵被击退时化烟或逃走，不表现伤亡。", fontSize = 15.sp, color = Dim)
    }
}

/** 战役志: each battle's lines from the 演义, plain words, and what history says. */
@Composable
fun ChronicleWindow() {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    Column(
        Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WindowHeader(CHRONICLE_ID, "战役志", 32.sp)
        Text("每守住一关，这里就多一页。原文出自《三国演义》；“史书怎么说”根据《三国志》等史书，帮你分清小说和历史。", fontSize = 18.sp, color = Dim)
        for (l in Levels.all) {
            val read = Game.stars(l.id) > 0 || l.id == Game.level.id
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("第 ${l.number} 关　${l.title}", fontSize = 25.sp, color = if (read) Brass else Dim)
                if (!read) {
                    Text("守住前面的战役后解锁。", fontSize = 18.sp, color = Dim)
                } else {
                    Text("演义原文：${l.chronicle.novel}", fontSize = 18.sp, color = LabelInk)
                    Text("白话：${l.chronicle.plain}", fontSize = 18.sp, color = LabelInk)
                    Text("史书怎么说：${l.chronicle.history}", fontSize = 18.sp, color = Jade)
                    Text("本关计策：${l.stratagem.label}（${l.stratagem.general}）", fontSize = 17.sp, color = Dim)
                }
            }
        }
    }
}
