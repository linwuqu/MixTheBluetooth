package com.biosensor.migratedev.ui.cgm

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CgmCommandBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun threeButtonsDispatchTheirOwnActions() {
        var reads = 0
        var syncs = 0
        var deletes = 0
        compose.setContent {
            MaterialTheme {
                CgmCommandBar(
                    onRead = { reads += 1 },
                    onForceStopRead = {},
                    onSyncTime = { syncs += 1 },
                    onDelete = { deletes += 1 }
                )
            }
        }

        compose.onNodeWithText("读缓存").performClick()
        compose.onNodeWithText("对时").performClick()
        compose.onNodeWithText("删除").performClick()

        compose.runOnIdle {
            assertEquals(1, reads)
            assertEquals(1, syncs)
            assertEquals(1, deletes)
        }
    }

    @Test
    fun holdingReadForThreeSecondsForceStopsWithoutStartingAnotherRead() {
        var reads = 0
        var forceStops = 0
        compose.setContent {
            MaterialTheme {
                CgmCommandBar(
                    onRead = { reads += 1 },
                    onForceStopRead = { forceStops += 1 },
                    onSyncTime = {},
                    onDelete = {}
                )
            }
        }

        compose.onNodeWithText("读缓存").performTouchInput {
            down(center)
            advanceEventTime(3_100L)
            up()
        }

        compose.runOnIdle {
            assertEquals(0, reads)
            assertEquals(1, forceStops)
        }
    }
}
