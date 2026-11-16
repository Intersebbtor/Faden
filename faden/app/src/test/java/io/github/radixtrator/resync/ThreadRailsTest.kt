package io.github.radixtrator.resync

import io.github.radixtrator.resync.ui.post.MAX_DEPTH
import io.github.radixtrator.resync.ui.post.threadContinuations
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadRailsTest {
    /** The levels continuing below each row, as lists, for readable failures. */
    private fun levels(vararg depths: Int) = threadContinuations(depths.toList()).map { bits -> (1..MAX_DEPTH).filter { (bits and (1 shl it)) != 0 } }

    @Test
    fun `the last reply's thread stops at it`() {
        // A top-level comment with one reply, as in the screenshot: nothing runs on below the reply.
        assertEquals(listOf(emptyList(), emptyList()), levels(0, 1))
    }

    @Test
    fun `a thread runs on to the next sibling and through its replies`() {
        // 0
        // ├ 1  (has a sibling below: level 1 continues, through its replies too)
        // │ └ 2 (last reply of its parent: level 2 stops)
        // └ 1  (last: stops)
        // 0
        assertEquals(
            listOf(emptyList(), listOf(1), listOf(1), emptyList(), emptyList()),
            levels(0, 1, 2, 1, 0),
        )
    }

    @Test
    fun `an ancestor's thread stops below its last child even when deeper replies follow`() {
        // 0
        // ├ 1
        // └ 1        <- last child of the top-level comment
        //   ├ 2      level 1 must not show here: nothing at depth 1 follows
        //   └ 2
        assertEquals(
            listOf(emptyList(), listOf(1), emptyList(), listOf(2), emptyList()),
            levels(0, 1, 1, 2, 2),
        )
    }

    @Test
    fun `replies past the indent cap share its level`() {
        assertEquals(listOf(listOf(MAX_DEPTH), emptyList()), levels(MAX_DEPTH + 2, MAX_DEPTH + 1))
    }
}
