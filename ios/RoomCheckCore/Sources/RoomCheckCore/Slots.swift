import Foundation

/// One time a night gets walked. [id] is HHMM as the clock reads it, and is what marks are filed under.
public struct Slot: Equatable {
    public let id: String
    public let label: String
    public init(id: String, label: String) { self.id = id; self.label = label }
}

/// The times a night can be marked at.
///
/// Three of them stand every night but Thursday's, which has two of its own (see `THURSDAY`). A
/// night that names its own `rounds` gets THOSE INSTEAD - a night with one check at 12:30 has
/// exactly one column, not four.
///
/// Ids are the time as a clock face reads it, not a 24-hour one: "1115" is quarter past eleven at
/// night and "1200" is midnight. That is how the three original ids were written and every night
/// ever marked is filed under them, so the scheme stays. It also means they cannot be sorted as
/// numbers - 12 comes after 11 but before 1, because 1 o'clock is the far side of midnight - and a
/// 1:30 round sorted naively would land at the head of the sheet.
public enum Slots {
    public static let DEFAULT = [
        Slot(id: "1115", label: "11:15"),
        Slot(id: "1130", label: "11:30"),
        Slot(id: "1200", label: "12:00")
    ]

    /// Thursday night - leil shishi - is walked twice, and later: 12:45 and 1:00. That holds from
    /// `THURSDAY_FROM`, the first Thursday after it was decided; the Thursdays before it were walked
    /// other ways, and are filed under whatever they were marked in.
    public static let THURSDAY = [
        Slot(id: "1245", label: "12:45"),
        Slot(id: "0100", label: "1:00")
    ]
    private static let THURSDAY_FROM = "2026-10-29"

    /// The rounds a night has when it names none of its own: Thursday's two, or the standing three.
    /// A night not filed under a date - `dateKey` nil - is the standing three.
    public static func defaultFor(_ dateKey: String?) -> [Slot] {
        guard let key = dateKey, key >= THURSDAY_FROM else { return DEFAULT }
        let parts = key.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return DEFAULT }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        guard let day = cal.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2])) else {
            return DEFAULT
        }
        return cal.component(.weekday, from: day) == 5 ? THURSDAY : DEFAULT   // Sunday is 1
    }

    private static func hourOf(_ id: String) -> Int { Int(id.prefix(2)) ?? 0 }
    private static func minuteOf(_ id: String) -> Int { Int(id.dropFirst(2)) ?? 0 }

    /// Hours laid end to end across one night: 6pm...11pm, midnight, then 1am...5am.
    private static func nightHour(_ h: Int) -> Int {
        if h == 12 { return 12 }
        if h < 6 { return h + 12 }
        return h
    }

    /// Minutes into the night, so the list is in the order the rounds are actually walked.
    public static func order(_ id: String) -> Int { nightHour(hourOf(id)) * 60 + minuteOf(id) }

    /// "0130" -> "1:30".
    public static func labelFor(_ id: String) -> String {
        "\(hourOf(id)):" + String(format: "%02d", minuteOf(id))
    }

    public static func isValidId(_ id: String) -> Bool {
        guard id.count == 4, id.allSatisfy(\.isNumber) else { return false }
        let h = hourOf(id), m = minuteOf(id)
        return (1...12).contains(h) && (0...59).contains(m)
    }

    /// The id for a time on the wall clock right now, in the same shape as the stored ones.
    public static func idForClock(hour24: Int, minute: Int) -> String {
        let h = hour24 % 12 == 0 ? 12 : hour24 % 12
        return String(format: "%02d%02d", h, minute)
    }

    /// Every round the night filed under `dateKey` can be marked at: its usual rounds (see
    /// `defaultFor`), or - a night that declares its own - those INSTEAD of them. Any round the night already has marks in counts too, even if
    /// nobody declared it, so an older night whose rounds were recorded some other way still opens
    /// with the columns it was marked in rather than losing them.
    public static func all(_ n: Night, dateKey: String? = nil) -> [Slot] {
        let declared = n.rounds.filter(isValidId)
        let base = declared.isEmpty ? defaultFor(dateKey).map(\.id) : Array(declared)
        let marked = n.marks.filter { !$0.value.isEmpty }.keys.filter(isValidId)
        var seen = Set<String>()
        var ids: [String] = []
        for id in (base + Array(marked)) where !seen.contains(id) { seen.insert(id); ids.append(id) }
        return ids.sorted { order($0) < order($1) }.map { Slot(id: $0, label: labelFor($0)) }
    }

    /// The rounds that go on this night's picture. All of them unless this night narrowed it, and
    /// if the narrowing names nothing that still exists, all of them again, so removing a round
    /// cannot leave a blank sheet.
    public static func forSheet(_ n: Night, dateKey: String? = nil) -> [Slot] {
        let every = all(n, dateKey: dateKey)
        if n.sheetSlots.isEmpty { return every }
        let kept = every.filter { n.sheetSlots.contains($0.id) }
        return kept.isEmpty ? every : kept
    }
}
