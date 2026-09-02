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

    // Real twin-bed proportions (~39"x75", roughly 1:1.9) rather than the old squarish boxes.
    // A bunk (2 slots) keeps a single bed's footprint conceptually but is drawn a bit taller so
    // each occupant's name+buttons gets its own full-width row, stacked top/bottom - never split
    // side-by-side, which is what silently squeezed names to nothing before. Every bed sits flush
    // against a wall, with two-bed rooms using opposite walls rather than floating mid-room.
    private const val BUNK_W = 28f
    private const val BUNK_H = 64f
    private const val SINGLE_W = 26f
    private const val SINGLE_H = 52f
    private fun bunkLeft(slots: List<String>) = Bed(5f, 6f, BUNK_W, BUNK_H, slots, row = false)
    private fun bunkRight(slots: List<String>) = Bed(67f, 6f, BUNK_W, BUNK_H, slots, row = false)
    private fun singleRightVertical(slots: List<String>) = Bed(67f, 6f, SINGLE_W, SINGLE_H, slots, row = false)
    private fun singleBottomHorizontal(slots: List<String>) = Bed(5f, 74f, SINGLE_H, SINGLE_W, slots, row = true)

    val PLAN: List<Room> = listOf(
        Room("r1", "Room 1", Door(DoorWall.BOTTOM, 38f), listOf(
            bunkLeft(listOf("p2", "p1")), bunkRight(listOf("p3", "p4")))),
        Room("r2", "Room 2", Door(DoorWall.BOTTOM, 85f), listOf(
            bunkLeft(listOf("p8", "p5")), bunkRight(listOf("p7", "p6")))),
        Room("r3", "Room 3", Door(DoorWall.BOTTOM, 70f), listOf(
            bunkLeft(listOf("p11", "p12")), singleRightVertical(listOf("p9")), singleBottomHorizontal(listOf("p10")))),
        Room("r4", "Room 4", Door(DoorWall.BOTTOM, 24f), listOf(
            bunkLeft(listOf("p13", "p16")), singleRightVertical(listOf("p15")), singleBottomHorizontal(listOf("p14")))),
        Room("r5", "Room 5", Door(DoorWall.LEFT, 82f), listOf(
            bunkLeft(listOf("p20", "p18")), bunkRight(listOf("p17", "p19")))),
        Room("r6", "Room 6", Door(DoorWall.TOP, 72f), listOf(
            bunkLeft(listOf("p22", "p21")), bunkRight(listOf("p23", "p24")))),
        Room("r7", "Room 7", Door(DoorWall.BOTTOM, 14f), listOf(
            bunkLeft(listOf("p26", "p27")), singleRightVertical(listOf("p25")))),
        Room("r8", "Room 8", Door(DoorWall.BOTTOM, 16f), listOf(
            bunkLeft(listOf("p29", "p30")), singleRightVertical(listOf("p28"))))
    )

    val roomOf: Map<String, Room> = buildMap {
        PLAN.forEach { room -> room.beds.forEach { bed -> bed.slots.forEach { pid -> put(pid, room) } } }
    }

    val SLOTS = listOf("1115" to "11:15", "1130" to "11:30", "1200" to "12:00")
    val SIDS = SLOTS.map { it.first }
}
