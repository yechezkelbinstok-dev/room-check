package com.roomcheck.app

import com.roomcheck.app.data.Roster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plan as it is drawn - beds get moved by hand, and a slip puts one through a wall or another bed. */
class RosterTest {

    @Test
    fun everyBedIsInsideItsRoomAndClearOfTheOthers() {
        Roster.PLAN.forEach { room ->
            room.beds.forEach { b ->
                assertTrue(
                    "${room.id}: the bed at ${b.x},${b.y} runs through a wall",
                    b.x >= 0f && b.y >= 0f && b.x + b.w <= room.w && b.y + b.h <= room.h
                )
            }
            room.beds.forEachIndexed { i, a ->
                room.beds.drop(i + 1).forEach { b ->
                    val apart = a.x + a.w <= b.x || b.x + b.w <= a.x || a.y + a.h <= b.y || b.y + b.h <= a.y
                    assertTrue("${room.id}: the beds at ${a.x},${a.y} and ${b.x},${b.y} overlap", apart)
                }
            }
        }
    }

    @Test
    fun everyoneHasExactlyOneBed() {
        val slots = Roster.PLAN.flatMap { room -> room.beds.flatMap { it.slots } }
        assertEquals(slots.size, slots.toSet().size)
        assertEquals(Roster.PEOPLE.map { it.id }.toSet(), slots.toSet())
    }
}
