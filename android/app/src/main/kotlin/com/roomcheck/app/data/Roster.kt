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

    private const val BW = 36f
    private const val BH = 46f
    private fun bedV(x: Float, y: Float, slots: List<String>) = Bed(x, y, BW, BH, slots, row = false)
    private fun bedH(x: Float, y: Float, slots: List<String>) = Bed(x, y, BH, BW, slots, row = true)

    val PLAN: List<Room> = listOf(
        Room("r1", "Room 1", Door(DoorWall.BOTTOM, 38f), listOf(
            bedV(9f, 26f, listOf("p2", "p1")), bedV(55f, 26f, listOf("p3", "p4")))),
        Room("r2", "Room 2", Door(DoorWall.BOTTOM, 85f), listOf(
            bedV(9f, 26f, listOf("p8", "p5")), bedV(55f, 26f, listOf("p7", "p6")))),
        Room("r3", "Room 3", Door(DoorWall.BOTTOM, 70f), listOf(
            bedH(7f, 7f, listOf("p10")), bedV(7f, 49f, listOf("p9")), bedV(57f, 7f, listOf("p11", "p12")))),
        Room("r4", "Room 4", Door(DoorWall.BOTTOM, 24f), listOf(
            bedV(7f, 8f, listOf("p15")), bedH(47f, 8f, listOf("p14")), bedH(47f, 52f, listOf("p13", "p16")))),
        Room("r5", "Room 5", Door(DoorWall.LEFT, 82f), listOf(
            bedV(9f, 18f, listOf("p20", "p18")), bedV(55f, 18f, listOf("p17", "p19")))),
        Room("r6", "Room 6", Door(DoorWall.TOP, 72f), listOf(
            bedV(9f, 32f, listOf("p22", "p21")), bedV(55f, 32f, listOf("p23", "p24")))),
        Room("r7", "Room 7", Door(DoorWall.BOTTOM, 14f), listOf(
            bedH(27f, 10f, listOf("p26", "p27")), bedH(27f, 54f, listOf("p25")))),
        Room("r8", "Room 8", Door(DoorWall.BOTTOM, 16f), listOf(
            bedH(7f, 10f, listOf("p29", "p30")), bedV(57f, 10f, listOf("p28"))))
    )

    val roomOf: Map<String, Room> = buildMap {
        PLAN.forEach { room -> room.beds.forEach { bed -> bed.slots.forEach { pid -> put(pid, room) } } }
    }

    val SLOTS = listOf("1115" to "11:15", "1130" to "11:30", "1200" to "12:00")
    val SIDS = SLOTS.map { it.first }
}
