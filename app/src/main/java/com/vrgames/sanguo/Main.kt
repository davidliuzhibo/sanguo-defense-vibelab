package com.vrgames.sanguo

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.sanguo.content.BoardStage
import com.vrgames.sanguo.content.ChronicleWindow
import com.vrgames.sanguo.content.IntelWindow

const val INTEL_ID = "Intel"
const val CHRONICLE_ID = "Chronicle"

/**
 * Three linked spaces: the sand table on the player's table (Stage), the 军情图 (battles, the enemy,
 * the units) and the 战役志 (the 演义's words and what history says). They share [com.vrgames.sanguo.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { BoardStage() }
        }
        WindowContainer(INTEL_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { IntelWindow() }
        }
        WindowContainer(CHRONICLE_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { ChronicleWindow() }
        }
    }
