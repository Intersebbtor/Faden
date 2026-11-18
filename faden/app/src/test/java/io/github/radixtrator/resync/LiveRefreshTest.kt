package io.github.radixtrator.resync

import io.github.radixtrator.resync.data.LiveRefresh
import io.github.radixtrator.resync.data.Settings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveRefreshTest {
    @Test
    fun `live refresh offers Sync's nine rates, in order, still defaulting to 15 seconds`() {
        assertEquals(listOf(2, 5, 10, 15, 20, 25, 30, 45, 60), LiveRefresh.entries.map { it.seconds })
        assertEquals(LiveRefresh.S15, Settings().liveRefresh)
        // A rate saved before the new ones keeps its value.
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(LiveRefresh.S30, json.decodeFromString(Settings.serializer(), """{"liveRefresh":"S30"}""").liveRefresh)
    }
}
