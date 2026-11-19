package io.github.radixtrator.resync

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.radixtrator.resync.ui.media.FlingSpline
import io.github.radixtrator.resync.ui.media.ZoomState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZoomTest {
    @Test
    fun `zoomed media can't be panned past its edges`() {
        val zoom = ZoomState().apply { bounds = Size(100f, 200f) }
        zoom.zoomTo(2f)
        // At 2x the media reaches half the box past each edge: 50 sideways, 100 up and down.
        assertEquals(Offset(50f, 0f), zoom.pan(Offset(1000f, 0f)))
        assertEquals(Offset(50f, 0f), zoom.offset)
        zoom.pan(Offset(0f, -1000f))
        assertEquals(Offset(50f, -100f), zoom.offset)
        // Already at the edge: nothing moves, which is what stops a glide there.
        assertEquals(Offset.Zero, zoom.pan(Offset(10f, -10f)))
        // Zooming out pulls it back inside.
        zoom.zoomTo(1.5f)
        assertEquals(Offset(25f, -50f), zoom.offset)
    }

    @Test
    fun `the fling spline runs from start to end, fast first and slowing down`() {
        assertEquals(0f, FlingSpline.position(0f), 1e-3f)
        assertEquals(1f, FlingSpline.position(1f), 1e-3f)
        val samples = (0..20).map { FlingSpline.position(it / 20f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a }, "never goes back")
        // Decelerating: the first fifth of the time covers far more ground than the last fifth.
        assertTrue(FlingSpline.position(0.2f) - FlingSpline.position(0f) > 4 * (FlingSpline.position(1f) - FlingSpline.position(0.8f)))
    }
}
