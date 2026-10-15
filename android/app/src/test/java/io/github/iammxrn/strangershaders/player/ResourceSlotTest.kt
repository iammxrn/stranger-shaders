package io.github.iammxrn.strangershaders.player

import org.junit.Assert.*
import org.junit.Test

class ResourceSlotTest {
    @Test fun replacingReleasesExactlyThePreviousResource() {
        val released = mutableListOf<String>()
        val owner = ResourceSlot<String> { released += it }
        assertTrue(owner.replace("surface-1"))
        assertTrue(owner.replace("surface-2"))
        assertEquals(listOf("surface-1"), released)
        owner.close()
        owner.close()
        assertEquals(listOf("surface-1", "surface-2"), released)
    }
    @Test fun lateGlCallbackCannotResurrectDisposedOwner() {
        val released = mutableListOf<String>()
        val owner = ResourceSlot<String> { released += it }
        owner.close()
        assertFalse(owner.replace("late-surface"))
        assertEquals(listOf("late-surface"), released)
    }
}
