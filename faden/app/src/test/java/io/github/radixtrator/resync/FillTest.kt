package io.github.radixtrator.resync

import io.github.radixtrator.resync.i18n.fill
import org.junit.Assert.assertEquals
import org.junit.Test

class FillTest {
    private fun f(template: String, vararg args: Any?) = fill(template, args)

    @Test
    fun replacesPlaceholdersInAnyOrderAndRepeats() {
        assertEquals("b a b", f("{1} {0} {1}", "a", "b"))
        assertEquals("u/bob and 3", f("u/{0} and {1}", "bob", 3))
        assertEquals("x", f("{0}", "x"))
        assertEquals("ab", f("{0}{1}", "a", "b"))
    }

    @Test
    fun missingOrOutOfRangeArgsStayLiteral() {
        assertEquals("a {1}", f("{0} {1}", "a"))
        assertEquals("{5}", f("{5}", "a"))
        assertEquals("{99999999999999}", f("{99999999999999}", "a"))
    }

    @Test
    fun otherBracesStayLiteral() {
        assertEquals("{ a } {x} {} {0", f("{ {0} } {x} {} {0", "a"))
        assertEquals("{a}", f("{{0}}", "a"))
        assertEquals("{a", f("{{0}", "a"))
        assertEquals("a}", f("{0}}", "a"))
        assertEquals("{", f("{", "a"))
        assertEquals("{-1}", f("{-1}", "a"))
    }

    @Test
    fun noArgsReturnsTemplateUntouched() {
        assertEquals("{0} stays", f("{0} stays"))
    }

    @Test
    fun nullArgPrintsNull() {
        assertEquals("null!", f("{0}!", null))
    }

    @Test
    fun replacementIsNotReScanned() {
        assertEquals("{1} b", f("{0} {1}", "{1}", "b"))
    }
}
