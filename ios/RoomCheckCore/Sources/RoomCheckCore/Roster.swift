import Foundation

/// Hebrew is held as its own first and last, not split off one string: "יהודה לייב גור ארי'" has a
/// two-word surname, so any rule that cuts at the last space gets that one wrong. Anyone left blank
/// falls back to English, which is what makes the Hebrew switch safe on a half-filled roster.
public struct Person: Equatable {
    public let id: String
    public let first: String
    public let last: String
    public let hebFirst: String
    public let hebLast: String
    public init(_ id: String, _ first: String, _ last: String, _ hebFirst: String = "", _ hebLast: String = "") {
        self.id = id; self.first = first; self.last = last; self.hebFirst = hebFirst; self.hebLast = hebLast
    }
}

public struct Bed: Equatable {
    public let x: Double, y: Double, w: Double, h: Double
    public let slots: [String]
    public let row: Bool
    public init(x: Double, y: Double, w: Double, h: Double, slots: [String], row: Bool) {
        self.x = x; self.y = y; self.w = w; self.h = h; self.slots = slots; self.row = row
    }
}

public enum DoorWall { case top, bottom, left, right }
public struct Door: Equatable {
    public let wall: DoorWall
    public let pos: Double
    public init(_ wall: DoorWall, _ pos: Double) { self.wall = wall; self.pos = pos }
    public static func == (a: Door, b: Door) -> Bool { a.wall == b.wall && a.pos == b.pos }
}
extension DoorWall: Equatable {}

/// [w] and [h] are the room's own size in bed units. Rooms are not all the same size - one shared
/// size left half of some rooms as dead empty floor, and gave a wide room nowhere to put two beds
/// end to end along the same wall.
public struct Room: Equatable {
    public let id: String
    public let label: String
    public let hebLabel: String
    public let w: Double, h: Double
    public let door: Door
    public let beds: [Bed]
    public init(_ id: String, _ label: String, _ hebLabel: String, _ w: Double, _ h: Double, _ door: Door, _ beds: [Bed]) {
        self.id = id; self.label = label; self.hebLabel = hebLabel; self.w = w; self.h = h; self.door = door; self.beds = beds
    }
}

public enum Roster {
    public static let PEOPLE: [Person] = [
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
    ]
    public static let byId: [String: Person] = Dictionary(uniqueKeysWithValues: PEOPLE.map { ($0.id, $0) })

    // Every bed is the same twin bed - one footprint, rotated to run along whichever wall it's
    // against. A bunk is the same footprint as a single: two mattresses stack vertically, they
    // don't take more floor. So there is exactly one bed size in the whole app, no exceptions.
    private static let BED_SHORT = 32.0   // across the mattress
    private static let BED_LONG = 60.0    // head to foot

    /// Bed standing against the left or right wall - runs up and down the room.
    private static func upright(_ x: Double, _ y: Double, _ slots: String...) -> Bed {
        Bed(x: x, y: y, w: BED_SHORT, h: BED_LONG, slots: slots, row: false)
    }
    /// Bed against the top or bottom wall - runs across the room. Same bed, turned 90 degrees.
    private static func across(_ x: Double, _ y: Double, _ slots: String...) -> Bed {
        Bed(x: x, y: y, w: BED_LONG, h: BED_SHORT, slots: slots, row: true)
    }

    // Positions follow the hand-drawn sketch: which wall each bed is against, and which beds are
    // paired. Coordinates are the room INTERIOR (0-100 across, 0-h deep); the renderer insets this
    // inside the walls so the door box can sit outside them like the sketch draws it.
    public static let PLAN: [Room] = [
        // bunks on opposite walls, door on the bottom wall between them
        Room("r1", "Room 1", "חדר א׳", 100, 78, Door(.bottom, 38), [
            upright(2, 2, "p2", "p1"),
            upright(66, 2, "p3", "p4")
        ]),
        // bunk on the left wall, the other across the top right
        Room("r2", "Room 2", "חדר ב׳", 100, 78, Door(.bottom, 85), [
            upright(2, 2, "p8", "p5"),
            across(38, 2, "p7", "p6")
        ]),
        // Hirsch across the top wall with his foot against the right wall; the other two run down
        // the side walls level with each other, leaving the middle as the walkway in.
        Room("r3", "Room 3", "חדר ג׳", 100, 118, Door(.bottom, 70), [
            across(38, 2, "p10"),
            upright(2, 42, "p9"),
            upright(66, 42, "p11", "p12")
        ]),
        // A wider room: the two singles lie end to end along the top wall, right next to each
        // other, and the bunk stands against the right wall below them. Door stays bottom-left.
        Room("r4", "Room 4", "חדר ד׳", 126, 104, Door(.bottom, 26), [
            across(2, 2, "p15"),
            across(64, 2, "p14"),
            upright(92, 42, "p13", "p16")
        ]),
        // bunks on opposite walls, door low on the left wall below them
        Room("r5", "Room 5", "חדר ה׳", 100, 92, Door(.left, 76), [
            upright(2, 2, "p20", "p18"),
            upright(66, 2, "p17", "p19")
        ]),
        // The real door is on the far wall, but every plan is drawn from the doorway looking in,
        // so this room is turned a half-turn to match: door at the bottom, and the beds swapped
        // left for right with it.
        Room("r6", "Room 6", "חדר ו׳", 100, 78, Door(.bottom, 28), [
            upright(2, 2, "p23", "p24"),
            upright(66, 2, "p22", "p21")
        ]),
        // two beds across the right, door bottom-left on the opposite side
        Room("r7", "Room 7", "חדר ז׳", 100, 88, Door(.bottom, 14), [
            across(38, 2, "p26", "p27"),
            across(38, 54, "p25")
        ]),
        // bunk across the top, single down the right wall
        Room("r8", "Room 8", "חדר ח׳", 100, 96, Door(.bottom, 16), [
            across(2, 2, "p29", "p30"),
            upright(66, 32, "p28")
        ])
    ]

    public static let roomOf: [String: Room] = {
        var m: [String: Room] = [:]
        for room in PLAN { for bed in room.beds { for pid in bed.slots { m[pid] = room } } }
        return m
    }()

    public static let SIDS = Slots.DEFAULT.map(\.id)
}
