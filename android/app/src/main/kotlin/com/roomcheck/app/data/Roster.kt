package com.roomcheck.app.data

data class Person(val id: String, val first: String, val last: String)

data class Bed(val x: Float, val y: Float, val w: Float, val h: Float, val slots: List<String>, val row: Boolean)

enum class DoorWall { TOP, BOTTOM, LEFT, RIGHT }
data class Door(val wall: DoorWall, val pos: Float)

data class Room(val id: String, val label: String, val door: Door, val beds: List<Bed>)

object Roster {
    val PEOPLE: List<Person> = listOf(
        Person("p1", "Shlomo", "Altein"), Person("p2", "Yosef", "Lehr"),
        Person("p3", "Menachem Mendel", "Piekarski"), Person("p4", "Dovber", "Pevzner"),
        Person("p5", "Sholom", "Holtzberg"), Person("p6", "Menachem Mendel", "Stolik"),
        Person("p7", "Avrohom", "Raices"), Person("p8", "Menachem Mendel", "Levin"),
        Person("p9", "Avrohom", "Heidingsfeld"), Person("p10", "Menachem Mendel", "Hirsch"),
        Person("p11", "Asher", "Wolfe"), Person("p12", "Yaakov", "Tzfasman"),
        Person("p13", "Dovid", "Altein"), Person("p14", "Menachem Mendel", "Goldstein"),
        Person("p15", "Nochum", "Groner"), Person("p16", "Menachem Mendel", "Palace"),
        Person("p17", "Moshe Eliezer", "Backman"), Person("p18", "Shmuel", "Browd"),
        Person("p19", "Menachem Mendel HaLevi", "Flint"), Person("p20", "Shlomo", "Schwei"),
        Person("p21", "Yaakov", "Belinitzki"), Person("p22", "Chaim Yehoshua", "Brenenson"),
        Person("p23", "Yehuda Leib", "Gourarie"), Person("p24", "Leib Meir", "November"),
        Person("p25", "Sadya", "Liberow"), Person("p26", "Yitzchok Sholom", "Lipkind"),
        Person("p27", "Dovid", "Fridman"), Person("p28", "Tzvi Dov HaLevi", "Levitansky"),
        Person("p29", "Yehuda", "Fehler"), Person("p30", "Meir Shlomo", "Ceitlin")
    )
    val byId: Map<String, Person> = PEOPLE.associateBy { it.id }

    // Every bed is the same twin bed - one footprint, rotated to run along whichever wall it's
    // against. A bunk is the same footprint as a single: two mattresses stack vertically, they
    // don't take more floor. So there is exactly one bed size in the whole app, no exceptions.
    private const val BED_SHORT = 32f   // across the mattress
    private const val BED_LONG = 60f    // head to foot

    /** Bed standing against the left or right wall - runs up and down the room. */
    private fun upright(x: Float, y: Float, vararg slots: String) =
        Bed(x, y, BED_SHORT, BED_LONG, slots.toList(), row = false)

    /** Bed against the top or bottom wall - runs across the room. Same bed, turned 90 degrees. */
    private fun across(x: Float, y: Float, vararg slots: String) =
        Bed(x, y, BED_LONG, BED_SHORT, slots.toList(), row = true)

    // Positions follow the hand-drawn sketch: which wall each bed is against, and which beds are
    // paired. Coordinates are the room INTERIOR (0-100 x 0-112); the renderer insets this inside
    // the walls so the door box can sit outside them like the sketch draws it.
    val PLAN: List<Room> = listOf(
        // bunks on opposite walls, door on the bottom wall between them
        Room("r1", "Room 1", Door(DoorWall.BOTTOM, 38f), listOf(
            upright(2f, 6f, "p2", "p1"),        // Lehr / Shlomo Altein
            upright(66f, 6f, "p3", "p4"))),     // Piekarski / Pevzner
        // bunk on the left wall, the other across the top right
        Room("r2", "Room 2", Door(DoorWall.BOTTOM, 85f), listOf(
            upright(2f, 6f, "p8", "p5"),        // Levin / Holtzberg
            across(38f, 4f, "p7", "p6"))),      // Raices / Stolik
        // single across the top, single down the left wall, bunk on the right wall
        Room("r3", "Room 3", Door(DoorWall.BOTTOM, 70f), listOf(
            across(2f, 4f, "p10"),              // Hirsch
            upright(2f, 44f, "p9"),             // Heidingsfeld
            upright(66f, 30f, "p11", "p12"))),  // Wolfe / Tzfasman
        // single across the top left, single down the right wall, bunk across the bottom right
        Room("r4", "Room 4", Door(DoorWall.BOTTOM, 24f), listOf(
            across(2f, 4f, "p15"),              // Groner
            upright(66f, 4f, "p14"),            // Goldstein
            across(38f, 76f, "p13", "p16"))),   // Dovid Altein / Palace
        // bunks on opposite walls, door low on the left wall below them
        Room("r5", "Room 5", Door(DoorWall.LEFT, 82f), listOf(
            upright(2f, 4f, "p20", "p18"),      // Schwei / Browd
            upright(66f, 4f, "p17", "p19"))),   // Backman / Flint
        // door is on the top wall, so both bunks sit low, clear of it
        Room("r6", "Room 6", Door(DoorWall.TOP, 72f), listOf(
            upright(2f, 44f, "p22", "p21"),     // Brenenson / Belinitzki
            upright(66f, 44f, "p23", "p24"))),  // Gourarie / November
        // two beds across the right, door bottom-left on the opposite side
        Room("r7", "Room 7", Door(DoorWall.BOTTOM, 14f), listOf(
            across(38f, 4f, "p26", "p27"),      // Lipkind / Fridman
            across(38f, 52f, "p25"))),          // Liberow
        // bunk across the top, single down the right wall
        Room("r8", "Room 8", Door(DoorWall.BOTTOM, 16f), listOf(
            across(2f, 4f, "p29", "p30"),       // Fehler / Ceitlin
            upright(66f, 30f, "p28")))          // Levitansky
    )

    val roomOf: Map<String, Room> = buildMap {
        PLAN.forEach { room -> room.beds.forEach { bed -> bed.slots.forEach { pid -> put(pid, room) } } }
    }

    val SLOTS = listOf("1115" to "11:15", "1130" to "11:30", "1200" to "12:00")
    val SIDS = SLOTS.map { it.first }
}
