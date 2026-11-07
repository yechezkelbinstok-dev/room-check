package com.roomcheck.app

import com.roomcheck.app.data.DoorWall
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

    /**
     * A door opens onto floor: no bed stands in front of the doorway closer than the door is wide,
     * or it could not swing open - as Room 1's could not once Altein's bed stood against its wall.
     */
    @Test
    fun noBedStandsInFrontOfADoor() {
        val half = 7f            // half the doorway, as FloorPlan.kt draws it
        val swing = 2 * half     // and the door is as wide as the doorway
        Roster.PLAN.forEach { room ->
            val d = room.door
            room.beds.forEach { b ->
                // where the bed runs along the door's wall, and how far it stands off that wall
                val (from, to, off) = when (d.wall) {
                    DoorWall.TOP -> Triple(b.x, b.x + b.w, b.y)
                    DoorWall.BOTTOM -> Triple(b.x, b.x + b.w, room.h - (b.y + b.h))
                    DoorWall.LEFT -> Triple(b.y, b.y + b.h, b.x)
                    DoorWall.RIGHT -> Triple(b.y, b.y + b.h, room.w - (b.x + b.w))
                }
                val inFront = from < d.pos + half && to > d.pos - half
                assertTrue("${room.id}: the bed at ${b.x},${b.y} blocks the door", !inFront || off >= swing)
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
