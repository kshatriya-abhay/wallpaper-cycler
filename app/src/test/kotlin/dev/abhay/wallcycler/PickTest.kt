package dev.abhay.wallcycler

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickTest {
    @Test fun neverPicksTheCurrentImage() {
        val images = listOf("a", "b", "c")
        repeat(200) { seed -> assertNotEquals("b", pickNext(images, "b", Random(seed))) }
    }

    @Test fun picksTheOnlyOtherImage() = assertEquals("a", pickNext(listOf("a", "b"), "b"))

    @Test fun nothingWhenOnlyTheCurrentImageIsLeft() = assertNull(pickNext(listOf("b"), "b"))

    @Test fun nothingInAnEmptyFolder() = assertNull(pickNext(emptyList<String>(), null))

    @Test fun anyImageWhenNoneIsCurrent() = assertEquals("a", pickNext(listOf("a"), null))

    @Test fun picksEveryOtherImageEventually() {
        val images = listOf("a", "b", "c", "d")
        val seen = (0 until 200).mapNotNull { pickNext(images, "a", Random(it)) }.toSet()
        assertEquals(setOf("b", "c", "d"), seen)
    }
}
