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

    // Bed positions transcribed from the hand-drawn sketch of the actual rooms - the sketch is the
    // source of truth for which wall each bed sits against and whether it runs across the room or
    // along it. Coordinates are the room INTERIOR (0-100 wide, 0-112 tall); the renderer insets
    // this inside the walls so door boxes have room to sit outside the wall like in the sketch.
    // Rules the sketch enforces: beds are always against a wall, beds parallel to each other are
    // the same size, and nothing sits in front of the door.
    private fun bed(x: Float, y: Float, w: Float, h: Float, vararg slots: String) =
        Bed(x, y, w, h, slots.toList(), row = false)

    val PLAN: List<Room> = listOf(
        // two bunks facing each other on opposite walls, door on the bottom wall between them
        Room("r1", "Room 1", Door(DoorWall.BOTTOM, 38f), listOf(
            bed(2f, 8f, 28f, 60f, "p2", "p1"),      // Lehr / Shlomo Altein
            bed(70f, 8f, 28f, 60f, "p3", "p4"))),   // Piekarski / Pevzner
        // narrow bunk on the left wall, wide bunk lying across the right of the room
        Room("r2", "Room 2", Door(DoorWall.BOTTOM, 85f), listOf(
            bed(2f, 8f, 26f, 60f, "p8", "p5"),      // Levin / Holtzberg
            bed(38f, 12f, 60f, 48f, "p7", "p6"))),  // Raices / Stolik
        // wide single across the top, single down the left wall, bunk on the right wall
        Room("r3", "Room 3", Door(DoorWall.BOTTOM, 70f), listOf(
            bed(2f, 6f, 58f, 26f, "p10"),           // Hirsch
            bed(2f, 38f, 26f, 50f, "p9"),           // Heidingsfeld
            bed(70f, 12f, 28f, 60f, "p11", "p12"))),// Wolfe / Tzfasman
        // wide single across the top left, single top right, bunk below it on the right wall
        Room("r4", "Room 4", Door(DoorWall.BOTTOM, 24f), listOf(
            bed(2f, 6f, 56f, 26f, "p15"),           // Groner
            bed(68f, 6f, 30f, 28f, "p14"),          // Goldstein
            bed(68f, 38f, 30f, 56f, "p13", "p16"))),// Dovid Altein / Palace
        // two bunks on opposite walls, door low on the left wall below them
        Room("r5", "Room 5", Door(DoorWall.LEFT, 82f), listOf(
            bed(2f, 6f, 28f, 60f, "p20", "p18"),    // Schwei / Browd
            bed(70f, 6f, 28f, 60f, "p17", "p19"))), // Backman / Flint
        // door is on the top wall, so both bunks sit low, away from it
        Room("r6", "Room 6", Door(DoorWall.TOP, 72f), listOf(
            bed(2f, 26f, 28f, 60f, "p22", "p21"),   // Brenenson / Belinitzki
            bed(70f, 26f, 28f, 60f, "p23", "p24"))),// Gourarie / November
        // two wide beds stacked on the right, door bottom-left on the opposite side
        Room("r7", "Room 7", Door(DoorWall.BOTTOM, 14f), listOf(
            bed(36f, 8f, 62f, 38f, "p26", "p27"),   // Lipkind / Fridman
            bed(36f, 56f, 62f, 26f, "p25"))),       // Liberow
        // wide bunk across the top, single down the right wall
        Room("r8", "Room 8", Door(DoorWall.BOTTOM, 16f), listOf(
            bed(2f, 8f, 62f, 38f, "p29", "p30"),    // Fehler / Ceitlin
            bed(70f, 24f, 28f, 52f, "p28")))        // Levitansky
    )

    val roomOf: Map<String, Room> = buildMap {
        PLAN.forEach { room -> room.beds.forEach { bed -> bed.slots.forEach { pid -> put(pid, room) } } }
    }

    val SLOTS = listOf("1115" to "11:15", "1130" to "11:30", "1200" to "12:00")
    val SIDS = SLOTS.map { it.first }
}
