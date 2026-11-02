package com.vrgames.sanguo.core

import com.vrgames.sanguo.core.EnemyType.BOAT
import com.vrgames.sanguo.core.EnemyType.CAVALRY
import com.vrgames.sanguo.core.EnemyType.FOOT
import com.vrgames.sanguo.core.EnemyType.RAM
import com.vrgames.sanguo.core.SlotKind.FIELD
import com.vrgames.sanguo.core.SlotKind.ROAD
import com.vrgames.sanguo.core.SlotKind.WALL
import com.vrgames.sanguo.core.UnitType.ARCHER
import com.vrgames.sanguo.core.UnitType.CATAPULT
import com.vrgames.sanguo.core.UnitType.CHEVAL
import com.vrgames.sanguo.core.UnitType.CROSSBOW
import com.vrgames.sanguo.core.UnitType.SPEAR

/** What a battle's story card and the 战役志 say: the 演义's words, plain words, and what history says. */
data class Chronicle(val novel: String, val plain: String, val history: String)

/** A move of a plan: before wave [beforeWave], put [type] on [slot] (or raise the unit there a rank). */
data class Move(val beforeWave: Int, val slot: Int, val type: UnitType? = null)

class Level(
    val id: String,
    val number: Int,
    val title: String,
    val story: String,
    val lesson: String,
    val paths: List<Path>,
    val slots: List<Slot>,
    val waves: List<List<Group>>,
    val units: Set<UnitType>,
    val stratagem: Stratagem,
    val grain: Int,
    /** Grain spent within which the third star is earned. */
    val grainPar: Int,
    val chronicle: Chronicle,
    /** A plan that wins with at least two stars (the tests and the test hook play it). */
    val plan: List<Move>,
    val chargePerKill: Double = 0.06,
    val startCharge: Double = 0.0,
    val hpScale: Double = 1.0,
    /** The road set on fire by 火烧博望. */
    val fireRoad: Int = 0,
    /** Where along road 0 the bridge is (据水断桥). */
    val bridge: Pair<Double, Double>? = null,
    /** The water rises once this wave is over (樊城); 0: never. */
    val floodAfterWave: Int = 0,
    /** The river drawn on the table, as a list of rectangles (x0, z0, x1, z1). */
    val water: List<List<Double>> = emptyList(),
)

private fun p(x: Double, z: Double) = P(x, z)
private fun road(vararg pts: P) = Path(pts.toList())
private fun river(vararg pts: P) = Path(pts.toList(), water = true)
private fun wall(id: Int, x: Double) = Slot(id, p(x, 0.19), WALL)
private fun field(id: Int, x: Double, z: Double, low: Boolean = false) = Slot(id, p(x, z), FIELD, low)
private fun onRoad(id: Int, x: Double, z: Double) = Slot(id, p(x, z), ROAD)
private fun g(type: EnemyType, count: Int, path: Int = 0, every: Double = 1.6, delay: Double = 0.0) = Group(type, count, path, every, delay)
private fun put(w: Int, slot: Int, t: UnitType) = Move(w, slot, t)
private fun up(w: Int, slot: Int) = Move(w, slot, null)

private val BASIC = setOf(ARCHER, SPEAR, CATAPULT, CHEVAL)
private val ALL = BASIC + CROSSBOW

object Levels {
    val all: List<Level> = listOf(
        Level(
            "xinye", 1, "新野·初守",
            "曹军先锋夏侯惇来攻新野。刘备新得军师诸葛亮，这是他第一次用兵。",
            "从托盘里捏起兵，拖到发亮的空位上：弓兵放城墙或田边，枪兵和拒马放在路上。布好了就捏金色的「出战」。",
            listOf(road(p(-0.26, -0.22), p(-0.26, -0.06), p(0.16, -0.06), p(0.16, 0.07), p(0.0, 0.07), p(0.0, 0.17))),
            listOf(wall(1, -0.2), wall(2, -0.08), wall(3, 0.08), wall(4, 0.2), field(5, -0.13, -0.13), field(6, 0.05, -0.13), field(7, 0.26, 0.0), field(8, -0.12, 0.02), onRoad(9, 0.16, 0.0), onRoad(10, 0.0, 0.12)),
            listOf(
                listOf(g(FOOT, 6)),
                listOf(g(FOOT, 10, every = 1.4)),
                listOf(g(FOOT, 8), g(CAVALRY, 3, delay = 6.0, every = 2.0)),
                listOf(g(FOOT, 12, every = 1.2), g(CAVALRY, 4, delay = 4.0)),
                listOf(g(RAM, 1), g(FOOT, 10, delay = 3.0, every = 1.2)),
            ),
            BASIC, Stratagem.FIRE_ROAD, grain = 240, grainPar = 520,
            chronicle = Chronicle(
                "（博望坡）孔明令关羽引兵伏于豫山，张飞伏于安林，赵云为前部，……夏侯惇兵至博望，两边芦苇火起，烧得曹军大败。",
                "诸葛亮第一次用兵，就在博望坡设下埋伏，等曹军进入狭窄的芦苇地，四面放火，大败夏侯惇。",
                "演义把博望坡之战写成诸葛亮的初出茅庐之战；按《三国志》，博望坡之战是刘备亲自设伏，当时诸葛亮还没有出山。",
            ),
            plan = listOf(put(1, 2, ARCHER), put(1, 3, ARCHER), put(1, 10, SPEAR), put(2, 9, CHEVAL), put(2, 8, ARCHER), put(3, 1, CATAPULT), put(4, 6, ARCHER), up(4, 2), put(5, 4, ARCHER), up(5, 3)),
        ),
        Level(
            "changban", 2, "当阳桥",
            "刘备南撤，曹操的骑兵追到当阳长坂。张飞带着二十骑守在桥头。",
            "只有一座桥能过河：把兵力集中在桥头。计策「据水断桥」能把桥上的敌人吓回去。",
            listOf(road(p(0.25, -0.22), p(0.25, -0.12), p(0.0, -0.12), p(0.0, 0.05), p(-0.15, 0.05), p(-0.15, 0.12), p(0.0, 0.12), p(0.0, 0.17))),
            listOf(wall(1, -0.12), wall(2, 0.06), wall(3, 0.2), field(4, 0.08, 0.03), field(5, -0.08, -0.08), field(6, 0.12, -0.06), field(7, -0.25, 0.08), onRoad(8, 0.0, 0.02), onRoad(9, -0.15, 0.09)),
            listOf(
                listOf(g(FOOT, 8)),
                listOf(g(FOOT, 10, every = 1.3), g(CAVALRY, 4, delay = 5.0)),
                listOf(g(CAVALRY, 8, every = 1.4)),
                listOf(g(FOOT, 14, every = 1.1), g(CAVALRY, 4, delay = 6.0)),
                listOf(g(RAM, 2, every = 6.0), g(FOOT, 10, delay = 2.0, every = 1.2)),
            ),
            BASIC, Stratagem.ROAR, grain = 240, grainPar = 560, hpScale = 1.15,
            chronicle = Chronicle(
                "张飞据水断桥，瞋目横矛曰：“身是张翼德也，可来共决死！”敌皆无敢近者。",
                "张飞拆断桥梁，站在河边，瞪着眼睛横着长矛大喊：“我就是张翼德，谁敢来决一死战！”曹军没有一个人敢靠近。",
                "这一段演义和《三国志·张飞传》的记载很接近，“据水断桥”正是史书原文；演义又加了“喝断桥梁、吓死夏侯杰”的情节。",
            ),
            bridge = 0.43 to 0.47,
            plan = listOf(put(1, 4, ARCHER), put(1, 8, SPEAR), put(1, 2, ARCHER), put(2, 5, ARCHER), put(2, 6, ARCHER), put(3, 9, SPEAR), up(3, 4), put(4, 1, CATAPULT), up(4, 8), up(5, 2), up(5, 5)),
        ),
        Level(
            "chibi", 3, "赤壁",
            "曹操八十万大军顺江而下，战船连成一片。孙刘联军在赤壁迎战。",
            "战船从江上来，守住两处码头；连弩开放了，近处连发。等东风起，用计策「借东风」火烧战船。",
            listOf(
                river(p(-0.31, -0.21), p(-0.04, -0.18), p(-0.26, -0.1), p(-0.14, -0.03)),
                river(p(0.31, -0.21), p(0.04, -0.18), p(0.26, -0.1), p(0.15, -0.03)),
                road(p(-0.32, 0.03), p(-0.1, 0.03), p(-0.1, 0.1), p(0.0, 0.1), p(0.0, 0.17)),
            ),
            listOf(wall(1, -0.15), wall(2, 0.0), wall(3, 0.15), field(4, -0.24, 0.03), field(5, 0.0, 0.0), field(6, 0.24, 0.02), field(7, 0.12, 0.08), onRoad(8, -0.1, 0.06), onRoad(9, 0.0, 0.13)),
            listOf(
                listOf(g(BOAT, 3, 0, every = 3.0), g(FOOT, 5, 2, delay = 4.0)),
                listOf(g(BOAT, 3, 0, every = 3.0), g(BOAT, 3, 1, every = 3.0, delay = 2.0)),
                listOf(g(FOOT, 10, 2, every = 1.2), g(BOAT, 4, 1, every = 2.5, delay = 4.0)),
                listOf(g(BOAT, 3, 0, every = 2.6), g(BOAT, 3, 1, every = 2.6), g(CAVALRY, 4, 2, delay = 6.0)),
                listOf(g(BOAT, 4, 0, every = 2.4), g(BOAT, 4, 1, every = 2.4, delay = 1.0), g(FOOT, 8, 2, delay = 3.0)),
            ),
            ALL, Stratagem.EAST_WIND, grain = 330, grainPar = 700, chargePerKill = 0.07, hpScale = 1.2,
            chronicle = Chronicle(
                "时东南风急，……船如箭发，烟焰涨天，曹军人马烧溺死者甚众。",
                "东南风刮得正急，盖着油布的火船像箭一样冲向曹军的连环船，大火冲天，曹军大败。",
                "火攻赤壁在《三国志》里有记载，主将是周瑜，献火攻之计的是黄盖；诸葛亮“借东风”是演义的创作。",
            ),
            plan = listOf(put(1, 4, CROSSBOW), put(1, 6, CROSSBOW), put(1, 8, SPEAR), put(2, 5, CATAPULT), put(3, 2, ARCHER), up(3, 4), put(4, 7, ARCHER), up(4, 6), put(5, 1, ARCHER), up(5, 5)),
        ),
        Level(
            "fancheng", 4, "樊城",
            "关羽北伐，围攻樊城。秋雨连绵，汉水暴涨。",
            "两条路同时来敌。第二波过后河水上涨，低处田里的位置会被淹（那里的兵会退回，粮草退还六成）。",
            listOf(
                road(p(-0.28, -0.22), p(-0.28, 0.05), p(-0.06, 0.05), p(0.0, 0.17)),
                road(p(0.28, -0.22), p(0.1, -0.1), p(0.1, 0.08), p(0.02, 0.1), p(0.0, 0.17)),
            ),
            listOf(wall(1, -0.18), wall(2, -0.06), wall(3, 0.06), wall(4, 0.18), field(5, -0.18, -0.1, low = true), field(6, 0.0, -0.08, low = true), field(7, 0.2, -0.02), field(8, -0.15, 0.1), field(9, 0.18, 0.1), onRoad(10, -0.17, 0.05), onRoad(11, 0.1, -0.02)),
            listOf(
                listOf(g(FOOT, 6, 0), g(FOOT, 6, 1, delay = 2.0)),
                listOf(g(FOOT, 8, 0, every = 1.3), g(CAVALRY, 4, 1, delay = 3.0)),
                listOf(g(CAVALRY, 5, 0), g(FOOT, 8, 1, every = 1.3)),
                listOf(g(FOOT, 9, 0, every = 1.2), g(FOOT, 9, 1, every = 1.2, delay = 2.0)),
                listOf(g(RAM, 1, 0), g(RAM, 1, 1, delay = 4.0), g(CAVALRY, 4, 0, delay = 6.0), g(FOOT, 6, 1, delay = 8.0)),
            ),
            ALL, Stratagem.FLOOD, grain = 300, grainPar = 800, hpScale = 1.3,
            chronicle = Chronicle(
                "时值秋雨，汉水暴涨，……于禁七军皆没，禁降，庞德不屈而死。",
                "正赶上秋天连下大雨，汉水暴涨，曹将于禁率领的七支军队全被大水淹没。",
                "这件事《三国志》确有记载，但大水是天降大雨造成的；演义写成关羽事先算准、掘堤放水，是艺术加工。",
            ),
            floodAfterWave = 2,
            plan = listOf(put(1, 2, ARCHER), put(1, 3, ARCHER), put(1, 10, SPEAR), put(1, 11, SPEAR), put(2, 8, CROSSBOW), put(3, 9, CROSSBOW), put(3, 7, CATAPULT), up(4, 10), up(4, 11), up(4, 2), up(4, 3), put(5, 1, ARCHER), put(5, 4, ARCHER)),
        ),
        Level(
            "xicheng", 5, "西城·空城计",
            "马谡失了街亭，司马懿大军直扑西城。城里只剩两千五百老弱兵卒。",
            "粮草很少，兵不够用。计策「空城计」一开始就能用：让敌军停下二十秒，趁机多射几箭。",
            listOf(road(p(-0.3, -0.22), p(-0.3, -0.12), p(0.25, -0.12), p(0.25, -0.02), p(-0.2, -0.02), p(-0.2, 0.08), p(0.0, 0.08), p(0.0, 0.17))),
            listOf(wall(1, -0.12), wall(2, 0.0), wall(3, 0.12), field(4, 0.0, -0.07), field(5, -0.25, 0.03), field(6, 0.18, 0.04), onRoad(7, 0.25, -0.07), onRoad(8, -0.2, 0.03)),
            listOf(
                listOf(g(FOOT, 10, every = 1.2)),
                listOf(g(CAVALRY, 8, every = 1.2)),
                listOf(g(FOOT, 12, every = 1.0)),
                listOf(g(CAVALRY, 6), g(FOOT, 12, delay = 3.0, every = 1.0)),
                listOf(g(RAM, 2, every = 5.0), g(CAVALRY, 6, delay = 2.0, every = 1.2)),
            ),
            ALL, Stratagem.EMPTY_CITY, grain = 150, grainPar = 450, chargePerKill = 0.09, startCharge = 1.0, hpScale = 1.25,
            chronicle = Chronicle(
                "孔明乃披鹤氅，戴纶巾，引二小童携琴一张，于城上敌楼前，凭栏而坐，焚香操琴。",
                "诸葛亮穿上鹤氅、戴着纶巾，带着两个小童抱着一张琴，在城楼上靠着栏杆坐下，点上香，悠然弹琴。司马懿怀疑城里有埋伏，退兵而去。",
                "空城计是演义里最有名的故事之一，但正史没有记载诸葛亮和司马懿在西城对阵；《三国志》注里有一段类似的故事，主角也不同。",
            ),
            plan = listOf(put(1, 4, CROSSBOW), put(1, 8, SPEAR), put(2, 2, ARCHER), put(3, 7, CHEVAL), put(3, 6, ARCHER), up(4, 4), up(4, 2), up(5, 8), put(5, 1, ARCHER)),
        ),
        Level(
            "jingzhou", 6, "守荆州",
            "荆州是天下要地，三路敌军同时来攻。这一仗，所学的都用上吧。",
            "三条路一起来敌。计策「八阵图」：石阵困住所有敌军十秒，还会不断伤害它们。",
            listOf(
                road(p(-0.3, -0.22), p(-0.3, 0.0), p(-0.1, 0.0), p(-0.1, 0.11), p(0.0, 0.17)),
                road(p(0.0, -0.22), p(0.0, -0.1), p(0.06, -0.1), p(0.06, 0.06), p(0.0, 0.17)),
                road(p(0.3, -0.22), p(0.3, 0.02), p(0.12, 0.02), p(0.12, 0.12), p(0.0, 0.17)),
            ),
            listOf(wall(1, -0.2), wall(2, -0.07), wall(3, 0.07), wall(4, 0.2), field(5, -0.2, -0.1), field(6, 0.15, -0.1), field(7, -0.03, 0.03), field(8, 0.2, 0.08), field(9, -0.2, 0.08), onRoad(10, -0.2, 0.0), onRoad(11, 0.06, 0.0), onRoad(12, 0.21, 0.02)),
            listOf(
                listOf(g(FOOT, 4, 0), g(FOOT, 4, 1, delay = 1.0), g(FOOT, 4, 2, delay = 2.0)),
                listOf(g(CAVALRY, 4, 0), g(FOOT, 6, 1, every = 1.3), g(CAVALRY, 4, 2, delay = 3.0)),
                listOf(g(FOOT, 10, 0, every = 1.1), g(RAM, 1, 1), g(FOOT, 10, 2, every = 1.1, delay = 2.0)),
                listOf(g(CAVALRY, 8, 0, every = 1.2), g(CAVALRY, 8, 2, every = 1.2), g(FOOT, 10, 1, delay = 3.0)),
                listOf(g(FOOT, 14, 0, every = 1.0), g(FOOT, 14, 1, every = 1.0), g(FOOT, 14, 2, every = 1.0)),
                listOf(g(RAM, 1, 0), g(RAM, 1, 1, delay = 3.0), g(RAM, 1, 2, delay = 6.0), g(CAVALRY, 7, 1, delay = 4.0, every = 1.2)),
            ),
            ALL, Stratagem.EIGHT_ARRAYS, grain = 420, grainPar = 1000, hpScale = 1.45,
            chronicle = Chronicle(
                "（八阵图）乱石作阵，……陆逊引兵入阵，忽然狂风大作，飞沙走石，遮天盖地。",
                "诸葛亮在鱼腹浦用乱石摆下八阵图，东吴大将陆逊误入阵中，只觉得狂风大作、飞沙走石，找不到出路。",
                "八阵图是诸葛亮推演的一种兵法阵型，《三国志》记载他“推演兵法，作八阵图”；石阵困住陆逊的情节出自演义。荆州最后是被东吴吕蒙夺取的。",
            ),
            plan = listOf(put(1, 2, ARCHER), put(1, 3, ARCHER), put(1, 7, CROSSBOW), put(2, 10, SPEAR), put(2, 12, SPEAR), put(2, 11, SPEAR), put(3, 1, ARCHER), put(3, 4, ARCHER), put(4, 5, CATAPULT), up(4, 7), put(5, 8, CROSSBOW), put(5, 9, CROSSBOW), up(6, 2), up(6, 3)),
        ),
    )

    fun byId(id: String) = all.first { it.id == id }
}
