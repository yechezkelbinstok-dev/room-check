package com.roomcheck.app.data

/**
 * Hebrew is held as its own first and last, not split off one string: "יהודה לייב גור ארי'" has a
 * two-word surname, so any rule that cuts at the last space gets that one wrong. Anyone left blank
 * falls back to English, which is what makes the Hebrew switch safe on a half-filled roster.
 */
data class Person(
    val id: String,
    val first: String,
    val last: String,
    val hebFirst: String = "",
    val hebLast: String = ""
)

data class Bed(val x: Float, val y: Float, val w: Float, val h: Float, val slots: List<String>, val row: Boolean)

enum class DoorWall { TOP, BOTTOM, LEFT, RIGHT }
data class Door(val wall: DoorWall, val pos: Float)

/**
 * [w] and [h] are the room's own size in bed units. Rooms are not all the same size - one shared
 * size left half of some rooms as dead empty floor, and gave a wide room nowhere to put two beds
 * end to end along the same wall.
 */
data class Room(val id: String, val label: String, val hebLabel: String, val w: Float, val h: Float, val door: Door, val beds: List<Bed>)

object Roster {
    val PEOPLE: List<Person> = listOf(
        Person("p1", "Shlomo", "Altein", "שלמה", "אלטיין"),
        Person("p2", "Yosef", "Lehr", "יוסף", "לעהר"),
        Person("p3", "Menachem Mendel", "Piekarski", "מנחם מענדל", "פיקארסקי"),
        Person("p4", "Dovber", "Pevzner", "דובער", "פעוזנער"),
        Person("p5", "Sholom", "Holtzberg", "שלום", "הולצברג"),
        Person("p6", "Menachem Mendel", "Stolik", "מנחם מענדל", "סטאליק"),
        Person("p7", "Avrohom", "Raices", "אברהם", "רייצעס"),
        Person("p8", "Menachem Mendel", "Levin", "מנחם מענדל", "לוין"),
        Person("p9", "Avrohom", "Heidingsfeld", "אברהם", "היידינגספעלד"),
        Person("p10", "Menachem Mendel", "Hirsch", "מנחם מענדל", "הירש"),
        Person("p11", "Asher", "Wolfe", "אשר", "וואלף"),
        Person("p12", "Yaakov", "Tzfasman", "יעקב", "צפתמן"),
        Person("p13", "Dovid", "Altein", "דוד", "אלטיין"),
        Person("p14", "Menachem Mendel", "Goldstein", "מנחם מענדל", "גאלדשטיין"),
        Person("p15", "Nochum", "Groner", "נחום", "גראנער"),
        Person("p16", "Menachem Mendel", "Palace", "מנחם מענדל", "פאלאס"),
        Person("p17", "Moshe Eliezer", "Backman", "משה אליעזר", "באקמאן"),
        Person("p18", "Shmuel", "Browd", "שמואל", "בראוד"),
        Person("p19", "Menachem Mendel HaLevi", "Flint", "מנחם מענדל הלוי", "פלינט"),
        Person("p20", "Shlomo", "Schwei", "שלמה", "שוויי"),
        Person("p21", "Yaakov", "Belinitzki", "יעקב", "בליניצקי"),
        Person("p22", "Chaim Yehoshua", "Brenenson", "חיים יהושע", "ברענענסאן"),
        Person("p23", "Yehuda Leib", "Gourarie", "יהודה לייב", "גור ארי'"),
        Person("p24", "Leib Meir", "November", "לייב מאיר", "נאוועמבער"),
        Person("p25", "Sadya", "Liberow", "סעדי'", "ליבעראוו"),
        Person("p26", "Yitzchok Sholom", "Lipkind", "יצחק שלום", "ליפקינד"),
        Person("p27", "Dovid", "Fridman", "דוד", "פרידמאן"),
        Person("p28", "Tzvi Dov HaLevi", "Levitansky", "צבי דוב הלוי", "לויטנסקי"),
        Person("p29", "Yehuda", "Fehler", "יהודה", "פעלער"),
        Person("p30", "Meir Shlomo", "Ceitlin", "מאיר שלמה", "ציטלין")
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
    // paired. Coordinates are the room INTERIOR (0-100 across, 0-h deep); the renderer insets this
    // inside the walls so the door box can sit outside them like the sketch draws it.
    //
    // Every bed sits 2 units off its wall - touching it, not floating - and each room is only as
    // deep as its own beds need plus roughly a 16-unit walkway in from the door. That walkway is
    // all the empty floor there should ever be; a room does not get depth it has no use for.
    val PLAN: List<Room> = listOf(
        // bunks on opposite walls, door on the bottom wall between them
        Room("r1", "Room 1", "חדר א׳", 100f, 78f, Door(DoorWall.BOTTOM, 38f), listOf(
            upright(2f, 2f, "p2", "p1"),        // Lehr / Shlomo Altein
            upright(66f, 2f, "p3", "p4"))),     // Piekarski / Pevzner
        // bunk on the left wall, the other across the top right
        Room("r2", "Room 2", "חדר ב׳", 100f, 78f, Door(DoorWall.BOTTOM, 85f), listOf(
            upright(2f, 2f, "p8", "p5"),        // Levin / Holtzberg
            across(38f, 2f, "p7", "p6"))),      // Raices / Stolik
        // single across the top, single down the left wall, bunk on the right wall
        Room("r3", "Room 3", "חדר ג׳", 100f, 106f, Door(DoorWall.BOTTOM, 70f), listOf(
            across(2f, 2f, "p10"),              // Hirsch
            upright(2f, 42f, "p9"),             // Heidingsfeld
            upright(66f, 28f, "p11", "p12"))),  // Wolfe / Tzfasman
        // A wider room: the two singles lie end to end along the top wall, right next to each
        // other, and the bunk stands against the right wall below them. Door stays bottom-left.
        Room("r4", "Room 4", "חדר ד׳", 126f, 104f, Door(DoorWall.BOTTOM, 26f), listOf(
            across(2f, 2f, "p15"),              // Groner - single
            across(64f, 2f, "p14"),             // Goldstein - single, alongside it
            upright(92f, 42f, "p13", "p16"))),  // Dovid Altein / Palace - bunk on the right wall
        // bunks on opposite walls, door low on the left wall below them
        Room("r5", "Room 5", "חדר ה׳", 100f, 92f, Door(DoorWall.LEFT, 76f), listOf(
            upright(2f, 2f, "p20", "p18"),      // Schwei / Browd
            upright(66f, 2f, "p17", "p19"))),   // Backman / Flint
        // The real door is on the far wall, but every plan is drawn from the doorway looking in,
        // so this room is turned a half-turn to match: door at the bottom, and the beds swapped
        // left for right with it. That is a rotation, not a mirror - stand in the doorway and
        // Gourarie's bunk really is the one on your left.
        Room("r6", "Room 6", "חדר ו׳", 100f, 78f, Door(DoorWall.BOTTOM, 28f), listOf(
            upright(2f, 2f, "p23", "p24"),      // Gourarie / November
            upright(66f, 2f, "p22", "p21"))),   // Brenenson / Belinitzki
        // two beds across the right, door bottom-left on the opposite side
        Room("r7", "Room 7", "חדר ז׳", 100f, 88f, Door(DoorWall.BOTTOM, 14f), listOf(
            across(38f, 2f, "p26", "p27"),      // Lipkind / Fridman
            across(38f, 54f, "p25"))),          // Liberow
        // bunk across the top, single down the right wall
        Room("r8", "Room 8", "חדר ח׳", 100f, 96f, Door(DoorWall.BOTTOM, 16f), listOf(
            across(2f, 2f, "p29", "p30"),       // Fehler / Ceitlin
            upright(66f, 32f, "p28")))          // Levitansky
    )

    val roomOf: Map<String, Room> = buildMap {
        PLAN.forEach { room -> room.beds.forEach { bed -> bed.slots.forEach { pid -> put(pid, room) } } }
    }

    val SLOTS = listOf("1115" to "11:15", "1130" to "11:30", "1200" to "12:00")
    val SIDS = SLOTS.map { it.first }
}
